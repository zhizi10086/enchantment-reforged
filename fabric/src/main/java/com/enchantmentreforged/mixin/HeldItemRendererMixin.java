package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 速射的客户端表现。
 *
 * <p>第一人称拉弓动画在 {@code HeldItemRenderer} 里同样内联了 {@code /20.0F}，
 * 把这里的 20 换成"缩短后的满蓄力 tick 数"，动画与武器威力就能对上，
 * 不会出现"动画没拉满箭就飞出去"的观感。
 */
@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@ModifyConstant(method = "renderFirstPersonItem", constant = @Constant(floatValue = 20.0F))
	private float enchantmentReforged$quickDrawAnimation(float original) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) {
			return original;
		}
		int required = EnchantmentEffects.requiredDrawTicks(client.player.getActiveItem());
		return Math.max(1.0F, required);
	}
}
