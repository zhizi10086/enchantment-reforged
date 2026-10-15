package com.summy.enchantment.mixin;

import com.summy.enchantment.combat.EnchantmentEffects;
import com.summy.enchantment.combat.HungerOwner;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
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
 *     <li>{@code needsFood()} 里判断"还能不能吃"的 20（吃满了就真的不能再吃）。</li>
 * </ul>
 * 自然回血仍然沿用原版的 {@code foodLevel >= 20}，不在此次范围内。
 */
@Mixin(FoodData.class)
public abstract class HungerManagerMixin implements HungerOwner {
	/** 所属玩家（由 Player 构造完成时登记） */
	@Unique
	private Player summyEnchantment$owner;

	@Override
	public void summyEnchantment$setOwner(Player player) {
		this.summyEnchantment$owner = player;
	}

	/** 饥饿值上限：原版 20 + 大胃袋加成 */
	@Unique
	private int summyEnchantment$foodLevelCap() {
		return this.summyEnchantment$owner == null
				? 20
				: EnchantmentEffects.foodLevelCap(this.summyEnchantment$owner);
	}

	@ModifyConstant(method = "eat(IF)V", constant = @Constant(intValue = 20))
	private int summyEnchantment$extendFoodCap(int original) {
		return summyEnchantment$foodLevelCap();
	}

	@ModifyConstant(method = "needsFood()Z", constant = @Constant(intValue = 20))
	private int summyEnchantment$extendFullThreshold(int original) {
		return summyEnchantment$foodLevelCap();
	}
}
