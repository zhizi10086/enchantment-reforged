package com.enchantmentreforged.mixin;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.combat.ExperienceMath;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 铁砧界面：移除"过于昂贵"提示。
 *
 * <p>成本字段现在承载的是经验点数（通常大于 40），所以客户端这段
 * {@code cost >= 40} 的判定必须一并失效，否则每次都会显示 Too Expensive。
 */
@Environment(EnvType.CLIENT)
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
	@ModifyConstant(method = "drawForeground", constant = @Constant(intValue = 40))
	private int enchantmentReforged$removeTooExpensive(int original) {
		return EnchantmentReforgedConfig.get().enableAnvilXpCost ? Integer.MAX_VALUE : original;
	}

	/** 成本改成"等级 / 点数"共同显示（服务端存的是点数，这里反推等级） */
	@Redirect(
			method = "drawForeground",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/text/Text;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/text/MutableText;"
			)
	)
	private MutableText enchantmentReforged$showLevelAndPoints(String key, Object[] args) {
		if (EnchantmentReforgedConfig.get().enableAnvilXpCost
				&& "container.repair.cost".equals(key)
				&& args.length > 0
				&& args[0] instanceof Integer points) {
			return Text.translatable("text.enchantment_reforged.anvil.cost",
					ExperienceMath.levelForPoints(points), points);
		}
		return Text.translatable(key, args);
	}
}
