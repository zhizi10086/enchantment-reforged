package com.enchantmentreforged.mixin;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 开放原版保护 5 级。
 *
 * <p>只改 minecraft:protection 一个实例的上限，火焰/摔落/爆炸/弹射物保护仍为 4 级。
 * 1.20.1 的附魔还不是数据驱动注册表，数据包无法改上限，因此用最小改动提升原版字段；
 * 代价公式、战利品、铁砧、命令等原版行为全部照旧。
 */
@Mixin(ProtectionEnchantment.class)
public abstract class ProtectionEnchantmentMixin {
	@Inject(method = "getMaxLevel()I", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$raiseProtectionMaxLevel(CallbackInfoReturnable<Integer> cir) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (config.enableProtectionLevel5 && ((Object) this) == Enchantments.ALL_DAMAGE_PROTECTION) {
			cir.setReturnValue(config.protectionMaxLevel);
		}
	}
}
