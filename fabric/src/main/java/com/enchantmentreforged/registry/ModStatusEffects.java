package com.enchantmentreforged.registry;

import com.enchantmentreforged.EnchantmentReforged;
import com.enchantmentreforged.effect.SoulGraceStatusEffect;
import com.enchantmentreforged.effect.StrengthStatusEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * 自定义状态效果注册。
 *
 * <p>自定义力量的伤害是"命中时乘算"，所以这里刻意不挂任何属性修饰符——
 * 原版力量靠 ATTACK_DAMAGE 属性每级加 3，如果这里再挂一次就会双重计算。
 */
public final class ModStatusEffects {
	/** 与原版力量同色（0x932423），视觉上无缝替换 */
	public static final int STRENGTH_COLOR = 0x932423;

	public static StatusEffect STRENGTH;
	/** 灵魂加护的冷却（纯可视化，权威计时在服务端） */
	public static StatusEffect SOUL_GRACE;

	private ModStatusEffects() {
	}

	public static void register() {
		STRENGTH = Registry.register(
				Registries.STATUS_EFFECT,
				EnchantmentReforged.id("strength"),
				new StrengthStatusEffect()
		);

		SOUL_GRACE = Registry.register(
				Registries.STATUS_EFFECT,
				EnchantmentReforged.id("soul_grace"),
				new SoulGraceStatusEffect()
		);
	}
}
