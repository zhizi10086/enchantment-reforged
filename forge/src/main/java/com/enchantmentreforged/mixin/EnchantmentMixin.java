package com.enchantmentreforged.mixin;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.registry.ModEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 附魔层面的两处调整。
 *
 * <p>注入 Enchantment#canAccept（1.20.1 Yarn 中可覆写的互斥判断），而不是公开的
 * canCombine：后者是 final 且内部调用前者，所以注入基类即可覆盖双向判断。
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
	/** 互斥表：锋利Plus ↔ 原版锋利、自动熔炼 ↔ 精准采集（附魔台、铁砧、随机附魔都会走这里） */
	@Inject(method = "isCompatibleWith(Lnet/minecraft/world/item/enchantment/Enchantment;)Z", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$blockVanillaSharpnessPair(Enchantment other, CallbackInfoReturnable<Boolean> cir) {
		Enchantment self = (Enchantment) (Object) this;

		// 锋利Plus 与原版锋利由同一个开关决定：开启锋利Plus 时原版锋利自动失效
		if (EnchantmentReforgedConfig.get().enableCustomSharpness && ModEnchantments.SHARPNESS_PLUS != null) {
			boolean vanillaToCustom = self == Enchantments.SHARPNESS && other == ModEnchantments.SHARPNESS_PLUS;
			boolean customToVanilla = self == ModEnchantments.SHARPNESS_PLUS && other == Enchantments.SHARPNESS;
			if (vanillaToCustom || customToVanilla) {
				cir.setReturnValue(false);
				return;
			}
		}

		// 自动熔炼与精准采集互斥（双向）
		if (ModEnchantments.AUTO_SMELT != null) {
			boolean autoSmeltToSilk = self == ModEnchantments.AUTO_SMELT && other == Enchantments.SILK_TOUCH;
			boolean silkToAutoSmelt = self == Enchantments.SILK_TOUCH && other == ModEnchantments.AUTO_SMELT;
			if (autoSmeltToSilk || silkToAutoSmelt) {
				cir.setReturnValue(false);
			}
		}

		// 无尽箭袋与无限互斥（双向）：无尽箭袋是无限的上位替代
		if (ModEnchantments.ENDLESS_QUIVER != null) {
			boolean endlessToInfinity = self == ModEnchantments.ENDLESS_QUIVER && other == Enchantments.INFINITY_ARROWS;
			boolean infinityToEndless = self == Enchantments.INFINITY_ARROWS && other == ModEnchantments.ENDLESS_QUIVER;
			if (endlessToInfinity || infinityToEndless) {
				cir.setReturnValue(false);
				return;
			}
		}

		// 无限与经验修补共存（反向：this=经验修补、other=无限）
		if (EnchantmentReforgedConfig.get().enableInfinityMending
				&& self == Enchantments.MENDING && other == Enchantments.INFINITY_ARROWS) {
			cir.setReturnValue(true);
		}
	}

	/** 弩兼容：让原版弓的四个附魔（力量/无限/火矢/冲击）可以附到弩上 */
	@Inject(method = "canEnchant(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$allowCrossbow(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableCrossbowCompat || !stack.is(Items.CROSSBOW)) {
			return;
		}
		Enchantment self = (Enchantment) (Object) this;
		if (self == Enchantments.POWER_ARROWS || self == Enchantments.INFINITY_ARROWS
				|| self == Enchantments.FLAMING_ARROWS || self == Enchantments.PUNCH_ARROWS) {
			cir.setReturnValue(true);
		}
	}

	/**
	 * 把原版锋利移出"随机可选"范围。
	 *
	 * <p>附魔台（EnchantmentHelper.getPossibleEntries）与随机战利品/村民附魔书
	 * （EnchantRandomlyFunction）都会过滤这个方法，因此一处生效即可。
	 * 原版锋利仍留在注册表中，命令与数据包显式列表依然可用。
	 */
	@Inject(method = "isDiscoverable()Z", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$hideVanillaSharpness(CallbackInfoReturnable<Boolean> cir) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (config.enableCustomSharpness && ((Object) this) == Enchantments.SHARPNESS) {
			cir.setReturnValue(false);
		}
	}
}
