package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.combat.HungerOwner;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 大胃袋：把饥饿值上限从原版写死的 20 换成"20 + 附魔加成"。
 *
 * <p>只改两处真正的上限判定：
 * <ul>
 *     <li>{@code add(int, float)} 里饿值封顶的那个 20（吃超过上限的部分不再被吞掉）；</li>
 *     <li>{@code isNotFull()} 里判断"还能不能吃"的 20（吃满了就真的不能再吃）。</li>
 * </ul>
 * 自然回血仍然沿用原版的 {@code foodLevel >= 20}，不在此次范围内。
 */
@Mixin(HungerManager.class)
public abstract class HungerManagerMixin implements HungerOwner {
	/** 所属玩家（由 PlayerEntity 构造完成时登记） */
	@Unique
	private PlayerEntity enchantmentReforged$owner;

	@Override
	public void enchantmentReforged$setOwner(PlayerEntity player) {
		this.enchantmentReforged$owner = player;
	}

	/** 饥饿值上限：原版 20 + 大胃袋加成 */
	@Unique
	private int enchantmentReforged$foodLevelCap() {
		return this.enchantmentReforged$owner == null
				? 20
				: EnchantmentEffects.foodLevelCap(this.enchantmentReforged$owner);
	}

	@ModifyConstant(method = "add(IF)V", constant = @Constant(intValue = 20))
	private int enchantmentReforged$extendFoodCap(int original) {
		return enchantmentReforged$foodLevelCap();
	}

	@ModifyConstant(method = "isNotFull()Z", constant = @Constant(intValue = 20))
	private int enchantmentReforged$extendFullThreshold(int original) {
		return enchantmentReforged$foodLevelCap();
	}
}
