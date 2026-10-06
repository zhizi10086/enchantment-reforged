package com.enchantmentreforged.mixin;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.InfinityEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 允许"无限"与"经验修补"共存（开关：enable_infinity_mending）。
 *
 * <p>原版是 {@code InfinityEnchantment#canAccept} 单方面拒绝 MendingEnchantment，
 * 这里直接放行；反方向（this=经验修补、other=无限）由 EnchantmentMixin 处理。
 */
@Mixin(InfinityEnchantment.class)
public abstract class InfinityEnchantmentMixin {
	@Inject(method = "canAccept(Lnet/minecraft/enchantment/Enchantment;)Z", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$allowMending(Enchantment other, CallbackInfoReturnable<Boolean> cir) {
		if (EnchantmentReforgedConfig.get().enableInfinityMending && other == Enchantments.MENDING) {
			cir.setReturnValue(true);
		}
	}
}
