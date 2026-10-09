package com.enchantmentreforged.compat;

import com.enchantmentreforged.EnchantmentReforged;
import com.enchantmentreforged.combat.CombatFormulas;
import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.particle.MeleeParticles;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * 与 Summy Reliquary（mod id: {@code summy_reliquary}）的近战兼容层。
 *
 * <p>SR 有两处"自造"的近战伤害，都自己调 {@code target.damage(playerAttack)}，因此完全绕过
 * ER 挂在 {@code PlayerEntity#attack} 上的近战管线：
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
			active = classVisible || FabricLoader.getInstance().isModLoaded(MOD_ID);
		}
		return active;
	}

	/** 近战伤害乘区：力量 × 死神祝福（造成方）× 复仇，与 {@code PlayerEntityMixin} 里的算法一致 */
	public static float meleeMultiplier(PlayerEntity attacker) {
		return CombatFormulas.strengthMultiplier(attacker)
				* EnchantmentEffects.deathsBlessingOutgoing(attacker)
				* EnchantmentEffects.revengeMultiplier(attacker);
	}

	/**
	 * 命中后结算 ER 的近战效果。
	 *
	 * <p>魔剑 / 嗜血 / 命中粒子恒定生效；{@code allowExecute} 控制斩杀、
	 * {@code allowSurprise} 控制出其不意（命中后再补一次伤害并再结算一次魔剑 / 嗜血，同时播对应粒子）。
	 * 强力斩击只吃斩杀、不掷出其不意，所以两个开关是独立的。
	 *
	 * @param dealt         本次最终伤害（已含乘区），魔剑/嗜血以它为基准
	 * @param allowExecute  是否允许斩杀判定
	 * @param allowSurprise 是否允许出其不意的二次结算
	 */
	public static void onMeleeHit(LivingEntity attacker, ItemStack weapon, Entity target, DamageSource source,
			float dealt, boolean allowExecute, boolean allowSurprise) {
		if (!isActive() || !(attacker instanceof PlayerEntity)) {
			return;
		}
		if (!loggedFirstHit) {
			loggedFirstHit = true;
			// require = 0 时"注入没命中"是静默的，这条日志是唯一的实机判据
			EnchantmentReforged.LOGGER.info("[ER] Summy Reliquary 近战兼容已生效：本次命中按近战管线结算");
		}
		EnchantmentEffects.applySpellblade(attacker, weapon, target, dealt);
		EnchantmentEffects.applyLifesteal(attacker, weapon, dealt);
		MeleeParticles.spawnOnHit(attacker, target);

		if (allowExecute) {
			EnchantmentEffects.tryExecute(attacker, target, weapon);
		}
		if (allowSurprise && EnchantmentEffects.rollSurprise(attacker, weapon)) {
			// 与 PlayerEntityMixin 的出其不意分支保持一致：先清无敌帧，再用同一个伤害源与金额补一次
			MeleeParticles.spawnSurprise(attacker, target);
			if (target instanceof LivingEntity livingTarget) {
				livingTarget.timeUntilRegen = 0;
			}
			if (target.damage(source, dealt)) {
				EnchantmentEffects.applySpellblade(attacker, weapon, target, dealt);
				EnchantmentEffects.applyLifesteal(attacker, weapon, dealt);
			}
		}
	}
}
