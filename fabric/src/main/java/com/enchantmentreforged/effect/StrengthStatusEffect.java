package com.enchantmentreforged.effect;

import com.enchantmentreforged.registry.ModStatusEffects;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * 自定义力量效果本体。
 *
 * <p>原版 {@link StatusEffect} 的构造器是 protected，所以必须由子类公开出来。
 * 这里刻意不重写任何方法：不加属性修饰符、不做周期效果——
 * 伤害倍率完全在命中时由 {@code CombatFormulas} 计算。
 */
public class StrengthStatusEffect extends StatusEffect {
	public StrengthStatusEffect() {
		super(StatusEffectCategory.BENEFICIAL, ModStatusEffects.STRENGTH_COLOR);
	}
}
