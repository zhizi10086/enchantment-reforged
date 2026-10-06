package com.enchantmentreforged.combat;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.registry.ModStatusEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 伤害相关的共用计算。
 *
 * <p>目标公式（满蓄力）：{@code (基础伤害 + 锋利加成) × 力量倍率 × 暴击倍率}，
 * 其中力量倍率 = {@code 1 + 每级倍率 × 等级}，等级 = amplifier + 1。
 */
public final class CombatFormulas {
	private CombatFormulas() {
	}

	/** 力量倍率；没有自定义力量时返回 1.0 */
	public static float strengthMultiplier(LivingEntity entity) {
		if (!EnchantmentReforgedConfig.get().enableStrengthRework) {
			// 总开关关闭：力量重做整体失效（此时原版力量来源也不会被重定向，走回原版加算）
			return 1.0F;
		}
		MobEffectInstance effect = entity.getEffect(ModStatusEffects.STRENGTH);
		if (effect == null) {
			return 1.0F;
		}
		return 1.0F + EnchantmentReforgedConfig.get().strengthMultiplierPerLevel * (effect.getAmplifier() + 1);
	}

	/**
	 * 复刻原版跳劈暴击判定。
	 *
	 * <p>"目标是生物"这一条由调用点保证：我们只注入 attack() 中位于
	 * {@code target instanceof LivingEntity} 分支内的那次附魔加成计算。
	 *
	 * @param attackCharge 原版 getAttackStrengthScale(0.5F) 的返回值（计时器重置前）
	 */
	public static boolean isCriticalHit(Player player, float attackCharge) {
		return attackCharge > 0.9F
				&& player.fallDistance > 0.0F
				&& !player.onGround()
				&& !player.onClimbable()
				&& !player.isInWater()
				&& !player.hasEffect(MobEffects.BLINDNESS)
				&& !player.isPassenger()
				&& !player.isSprinting();
	}

	/** 暴击倍率：原版固定 1.5 */
	public static float critFactor(Player player, float attackCharge) {
		if (!EnchantmentReforgedConfig.get().enableCustomSharpness) {
			// 总开关关闭：恢复原版暴击顺序（暴击只放大武器基础伤害）
			return 1.0F;
		}
		return isCriticalHit(player, attackCharge) ? 1.5F : 1.0F;
	}

	/**
	 * 把即将被添加的原版力量实例改写成自定义力量实例。
	 *
	 * <p>药水、信标、谜之炖菜、/effect 命令最终都会走
	 * {@code LivingEntity.addEffect(...)} 或 {@code setStatusEffect(...)}，
	 * 所以在这里改写即可覆盖所有原版来源，且不需要动原版核心逻辑。
	 */
	@Nullable
	public static MobEffectInstance redirectVanillaStrength(@Nullable MobEffectInstance instance) {
		if (!EnchantmentReforgedConfig.get().enableStrengthRework) {
			// 总开关关闭：原版力量照原样添加，不重定向
			return instance;
		}
		if (instance == null || instance.getEffect() != MobEffects.DAMAGE_BOOST) {
			return instance;
		}
		if (ModStatusEffects.STRENGTH == null) {
			return instance;
		}
		// 保留时长 / 等级 / 环境效果 / 粒子 / 图标等全部标志
		return new MobEffectInstance(
				ModStatusEffects.STRENGTH,
				instance.getDuration(),
				instance.getAmplifier(),
				instance.isAmbient(),
				instance.isVisible(),
				instance.showIcon()
		);
	}

	/** 把存档里已经存在的原版力量替换成自定义力量（实体加载时调用） */
	public static void replaceVanillaStrength(LivingEntity entity) {
		if (!EnchantmentReforgedConfig.get().enableStrengthRework) {
			return;
		}
		if (ModStatusEffects.STRENGTH == null) {
			return;
		}
		MobEffectInstance legacy = entity.getEffect(MobEffects.DAMAGE_BOOST);
		if (legacy == null) {
			return;
		}
		// 先移除原版效果（同时摘掉它的属性修饰符），再加自定义效果
		entity.removeEffect(MobEffects.DAMAGE_BOOST);
		entity.addEffect(new MobEffectInstance(
				ModStatusEffects.STRENGTH,
				legacy.getDuration(),
				legacy.getAmplifier(),
				legacy.isAmbient(),
				legacy.isVisible(),
				legacy.showIcon()
		));
	}
}
