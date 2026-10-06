package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 坚甲：限制单次耐久损耗。
 *
 * <p>所有"扣耐久"最终都会走 {@code ItemStack.hurt(int, LivingEntity, Consumer)}，
 * 在这个入口把损耗量按该件装备的坚甲等级截断即可（没附魔时原样放行）。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyVariable(
			method = "hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
			at = @At("HEAD"),
			argsOnly = true
	)
	private int enchantmentReforged$sturdyCap(int amount) {
		int cap = EnchantmentEffects.sturdyCap((ItemStack) (Object) this);
		return cap == Integer.MAX_VALUE ? amount : Math.min(amount, cap);
	}
}
