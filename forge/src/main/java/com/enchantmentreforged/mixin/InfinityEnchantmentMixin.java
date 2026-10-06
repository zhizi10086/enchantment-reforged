package com.enchantmentreforged.mixin;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ArrowInfiniteEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 允许"无限"与"经验修补"共存（开关：enable_infinity_mending）。
 *
 * <p>原版是 {@code ArrowInfiniteEnchantment#canAccept} 单方面拒绝 MendingEnchantment，
 * 这里直接放行；反方向（this=经验修补、other=无限）由 EnchantmentMixin 处理。
 */
@Mixin(ArrowInfiniteEnchantment.class)
public abstract class InfinityEnchantmentMixin {
	// 官方映射里 Yarn 的 isCompatibleWith 在附魔子类上是 checkCompatibility
	@Inject(method = "checkCompatibility(Lnet/minecraft/world/item/enchantment/Enchantment;)Z", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$allowMending(Enchantment other, CallbackInfoReturnable<Boolean> cir) {
		if (EnchantmentReforgedConfig.get().enableInfinityMending && other == Enchantments.MENDING) {
			cir.setReturnValue(true);
		}
	}
}
