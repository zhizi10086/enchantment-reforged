package com.enchantmentreforged.compat;

import com.enchantmentreforged.EnchantmentReforged;
import com.enchantmentreforged.combat.CombatFormulas;
import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.enchantment.SimpleEnchantment;
import com.enchantmentreforged.network.ParticlePreferences;
import com.enchantmentreforged.particle.MeleeParticles;
import com.enchantmentreforged.registry.ModEnchantments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.Locale;

/**
 * 与 Summy Reliquary（mod id: {@code summy_reliquary}）的近战兼容层。
 *
 * <p>SR 有两处"自造"的近战伤害，都自己调 {@code target.hurt(playerAttack)}，因此完全绕过
 * ER 挂在 {@code Player#attack} 上的近战管线：
 * <ul>
 *     <li>暗仪刺刀「遁入暗影」的基础斩击 / 强力斩击（{@code ShadowDash#strike} / {@code #heavySlash}）；</li>
 *     <li>投掷长矛命中（{@code ThrownSpear#onHitEntity}）。</li>
 * </ul>
 * 这里把它们的伤害乘区与命中后效果补齐，口径与左键近战一致（强力斩击按 SR 原设计不吃乘区）。
 *
 * <p>本类刻意不引用 SR 的任何类：是否安装靠类文件/模组列表判断，注入点由独立的可选 Mixin 提供。
 */
public final class SummyReliquaryCompat {
	private static final String MOD_ID = "summy_reliquary";
	/** 用来探测 SR 是否存在的类文件路径（只查资源，不加载类） */
	private static final String PROBE_CLASS = "com/summy/reliquary/effect/ShadowDash.class";

	private static Boolean active;
	/** 首次真正走到兼容路径时打一条日志（只打一次），用来在实机确认注入确实命中了 */
	private static boolean loggedFirstHit;

	private SummyReliquaryCompat() {
	}

	/** 兼容是否生效（没装 SR 时恒为 false，整条链路等于不存在） */
	public static boolean isActive() {
		if (active == null) {
			boolean classVisible = false;
			try {
				// 只查类文件，绝不能 Class.forName —— 那会在 Mixin 准备阶段就把 SR 的类加载进来
				classVisible = SummyReliquaryCompat.class.getClassLoader().getResource(PROBE_CLASS) != null;
			} catch (Throwable ignored) {
				// 退回到下面的模组列表判断
			}
			ModList modList = ModList.get();
			active = classVisible || (modList != null && modList.isLoaded(MOD_ID));
		}
		return active;
	}

	/** 近战伤害乘区：力量 × 死神祝福（造成方）× 复仇，与 {@code PlayerEntityMixin} 里的算法一致 */
	public static float meleeMultiplier(Player attacker) {
		return CombatFormulas.strengthMultiplier(attacker)
				* EnchantmentEffects.deathsBlessingOutgoing(attacker)
				* EnchantmentEffects.revengeMultiplier(attacker);
	}

	/**
	 * 近战命中后的统一结算：SR 的两处斩击 / 投掷命中与左键近战共用同一套口径，并打一行诊断日志。
	 *
	 * <p>顺序与原来的实现逐行一致：魔剑 → 嗜血 → 主命中粒子 → 斩杀（仅主命中）→ 出其不意。
	 * 本轮收紧两处口径：
	 * <ul>
	 *     <li>这一击已经把目标打死（或目标本来就已死）时，不再补魔剑 / 斩杀 / 出其不意，也不再发它们的粒子；
	 *     嗜血照常按本次伤害回血（击杀也要回血）；</li>
	 *     <li>出其不意的粒子只在<b>二次伤害确实落地</b>之后才播，避免"只有粒子、没有伤害"的假象。</li>
	 * </ul>
	 *
	 * @param entry         注入点标签（诊断日志用）：strike / heavySlash / thrownSpear / melee
	 * @param rawAmount     未乘乘区的原始金额
	 * @param amount        最终金额（已乘力量 / 死神祝福·造成方 / 复仇）
	 * @param landed        这一击是否真的落地（原版 hurt 的返回值）
	 * @param allowExecute  是否允许斩杀判定
	 * @param allowSurprise 是否允许出其不意（强力斩击按设计传 false）
	 * @param mainHit       是否主命中（横扫的附加目标传 false：不掷斩杀、不出主命中粒子）
	 */
	public static void settleHit(String entry, Player attacker, ItemStack weapon, Entity target,
			DamageSource source, float rawAmount, float amount, boolean landed, boolean allowExecute,
			boolean allowSurprise, boolean mainHit) {
		if (attacker == null || target == null) {
			return;
		}
		if (!loggedFirstHit) {
			loggedFirstHit = true;
			// require = 0 时"注入没命中"是静默的，这条日志是唯一的实机判据
			EnchantmentReforged.LOGGER.info("[ER] Summy Reliquary 近战兼容已生效：本次命中按近战管线结算");
		}
		boolean targetGone = target instanceof LivingEntity living && (living.isDeadOrDying()
				|| living.getHealth() <= 0.0F);

		float magic = 0.0F;
		float healed = 0.0F;
		boolean executed = false;
		EnchantmentEffects.SurpriseRoll surprise = null;
		boolean surpriseLanded = false;

		if (landed) {
			if (!targetGone) {
				magic = EnchantmentEffects.applySpellbladeReport(attacker, weapon, target, amount);
			}
			// 嗜血与目标是否还活着无关：击杀也要按本次伤害回血
			healed = EnchantmentEffects.applyLifestealReport(attacker, weapon, amount);
			if (mainHit) {
				MeleeParticles.spawnOnHit(attacker, target);
			}
			if (!targetGone && mainHit && allowExecute) {
				executed = EnchantmentEffects.tryExecuteReport(attacker, target, weapon);
			}
			if (!targetGone && allowSurprise) {
				surprise = EnchantmentEffects.rollSurpriseRoll(attacker, weapon);
				if (surprise.hit()) {
					// 与近战管线保持一致：先清无敌帧，再用同一个伤害源与金额补一次
					if (target instanceof LivingEntity livingTarget) {
						livingTarget.invulnerableTime = 0;
					}
					if (target.hurt(source, amount)) {
						surpriseLanded = true;
						MeleeParticles.spawnSurprise(attacker, target);
						EnchantmentEffects.applySpellbladeReport(attacker, weapon, target, amount);
						EnchantmentEffects.applyLifestealReport(attacker, weapon, amount);
					}
				}
			}
		}

		logSettlement(entry, attacker, weapon, target, rawAmount, amount, landed, magic, healed, executed,
				allowExecute, allowSurprise, targetGone, surprise, surpriseLanded);
	}

	// ==================== 诊断日志（debug_sr_compat） ====================

	/** 每 tick 的输出上限：一次斩击打一堆目标时不至于刷屏 */
	private static final int DEBUG_LINES_PER_TICK = 8;
	private static long debugTick = Long.MIN_VALUE;
	private static int debugLinesThisTick;
	private static String debugLastLine;

	/**
	 * `[ER-SR]` 逐次结算日志：把这一击的原始金额、乘区三分量、魔剑 / 嗜血 / 斩杀 / 出其不意的实际结果、
	 * 目标血量与粒子档位写进一行，用来在实机上一眼判断"某个附魔到底有没有生效"。
	 *
	 * <p>只在 {@code debug_sr_compat} 打开、且本环境真的装了 SR 时输出；每 tick 最多 8 行、同内容去重。
	 */
	private static void logSettlement(String entry, Player attacker, ItemStack weapon, Entity target,
			float rawAmount, float amount, boolean landed, float magic, float healed, boolean executed,
			boolean allowExecute, boolean allowSurprise, boolean targetGone,
			EnchantmentEffects.SurpriseRoll surprise, boolean surpriseLanded) {
		if (!EnchantmentReforgedConfig.get().debugSrCompat || !isActive()) {
			return;
		}
		long tick = attacker.level().getGameTime();
		if (tick != debugTick) {
			debugTick = tick;
			debugLinesThisTick = 0;
			debugLastLine = null;
		}
		if (debugLinesThisTick >= DEBUG_LINES_PER_TICK) {
			return;
		}
		String line = buildSettlementLine(entry, attacker, weapon, target, rawAmount, amount, landed, magic, healed,
				executed, allowExecute, allowSurprise, targetGone, surprise, surpriseLanded);
		if (line.equals(debugLastLine)) {
			return;
		}
		debugLastLine = line;
		debugLinesThisTick++;
		EnchantmentReforged.LOGGER.info(line);
	}

	/** 拼一行结算日志（字段顺序固定，便于对照） */
	private static String buildSettlementLine(String entry, Player attacker, ItemStack weapon, Entity target,
			float rawAmount, float amount, boolean landed, float magic, float healed, boolean executed,
			boolean allowExecute, boolean allowSurprise, boolean targetGone,
			EnchantmentEffects.SurpriseRoll surprise, boolean surpriseLanded) {
		int spellblade = levelOf(weapon, ModEnchantments.SPELLBLADE);
		int execution = levelOf(weapon, ModEnchantments.EXECUTION);
		int surpriseLevel = levelOf(weapon, ModEnchantments.SURPRISE);
		StringBuilder sb = new StringBuilder(240);
		sb.append("[ER-SR] ").append(entry)
				.append(" | ").append(attacker.getGameProfile().getName())
				.append(" 手持 ").append(BuiltInRegistries.ITEM.getKey(weapon.getItem()))
				.append("（魔剑").append(spellblade)
				.append(" / 斩杀").append(execution)
				.append(" / 出其不意").append(surpriseLevel).append("）")
				.append(" | ").append(fmt(rawAmount))
				.append(" × 力量").append(fmt(CombatFormulas.strengthMultiplier(attacker)))
				.append(" × 死神").append(fmt(EnchantmentEffects.deathsBlessingOutgoing(attacker)))
				.append(" × 复仇").append(fmt(EnchantmentEffects.revengeMultiplier(attacker)))
				.append(" = ").append(fmt(amount))
				.append(" | hurt=").append(landed);
		if (target instanceof LivingEntity living) {
			sb.append(" | 目标 ").append(living.getName().getString())
					.append(' ').append(fmt(living.getHealth())).append('/').append(fmt(living.getMaxHealth()));
		}
		if (!landed) {
			sb.append(" | 未落地：不结算任何命中后效果");
		} else {
			sb.append(" | 魔剑+").append(fmt(magic));
			sb.append(" 嗜血+").append(fmt(healed));
			sb.append(" 斩杀").append(executionNote(execution, targetGone, allowExecute, executed));
			sb.append(" | ").append(surpriseNote(surpriseLevel, targetGone, allowSurprise, surprise, surpriseLanded));
		}
		return sb.append(" | 粒子 ").append(particleNote(attacker)).toString();
	}

	/** 斩杀这一项的结论 */
	private static String executionNote(int level, boolean targetGone, boolean allowExecute, boolean executed) {
		if (level <= 0) {
			return "-（武器无附魔）";
		}
		if (!allowExecute) {
			return "-（设计不判）";
		}
		if (targetGone) {
			return "-（目标已死）";
		}
		return executed ? "=已补刀" : "=未达门槛";
	}

	/** 出其不意这一项的结论：几率 / 掷值 / 是否命中 / 二次伤害是否落地 */
	private static String surpriseNote(int level, boolean targetGone, boolean allowSurprise,
			EnchantmentEffects.SurpriseRoll surprise, boolean surpriseLanded) {
		if (!allowSurprise) {
			return "出其不意-（设计不掷）";
		}
		if (level <= 0) {
			return "出其不意-（武器无附魔）";
		}
		if (targetGone) {
			return "出其不意-（目标已死）";
		}
		if (surprise == null) {
			return "出其不意-（未判定）";
		}
		String outcome = surprise.hit()
				? (surpriseLanded ? " → 命中且落地" : " → 命中但二次伤害未落地")
				: " → 未命中";
		return "出其不意 " + String.format(Locale.ROOT, "%.1f", surprise.chance() * 100.0F) + "% 掷"
				+ fmt(surprise.roll()) + outcome;
	}

	/** 粒子档位说明（攻击者自己的上报值） */
	private static String particleNote(Player attacker) {
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUUID());
		if (preference == null) {
			return "档位未上报（按原版）";
		}
		return "主命中" + preference.meleeStyle() + " / 横扫" + preference.sweepStyle()
				+ " / 出其不意" + preference.surpriseStyle() + " / 强度" + preference.meleeEffect();
	}

	private static String fmt(float value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/** 武器上的附魔等级（附魔对象为空时按 0 处理） */
	private static int levelOf(ItemStack stack, SimpleEnchantment enchantment) {
		return enchantment == null ? 0 : EnchantmentEffects.levelOf(stack, enchantment);
	}
}
