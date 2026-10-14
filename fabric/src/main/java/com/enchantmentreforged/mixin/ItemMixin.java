package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "使用中每 tick"的钩子（1.3.7）：破空之愿满蓄力时的提示粒子。
 *
 * <p>原版弓（{@code BowItem}）没有覆盖 {@code usageTick}，所以这里注入基类 {@link Item}；
 * 调用方是 {@code LivingEntity#tickItemStackUsage}，客户端与服务端都会跑到——
 * 具体的"是不是弓、有没有破空之愿、蓄力是否到顶"判定与"只在服务端发粒子"
 * 都在 {@link EnchantmentEffects#tickSkyWishFullHint} 里，本方法只做转发。
 */
@Mixin(Item.class)
public abstract class ItemMixin {
	@Inject(method = "usageTick", at = @At("HEAD"))
	private void enchantmentReforged$skyWishFullHint(World world, LivingEntity user, ItemStack stack,
			int remainingUseTicks, CallbackInfo ci) {
		EnchantmentEffects.tickSkyWishFullHint(user, stack, remainingUseTicks);
	}
}
