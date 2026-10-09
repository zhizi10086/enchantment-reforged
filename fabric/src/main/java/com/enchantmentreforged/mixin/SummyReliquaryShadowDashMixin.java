package com.enchantmentreforged.mixin;

import com.enchantmentreforged.compat.SummyReliquaryCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 与 Summy Reliquary 的「遁入暗影」两段斩击对齐到 ER 的近战管线（可选兼容）。
 *
 * <p>没用 SR 当依赖：目标用字符串指定，没装 SR 时由 {@code SummyReliquaryMixinPlugin} 整份跳过。
 *
 * <p>注意两个被注入的方法都是 SR 自己的 <b>私有静态方法</b>，而且这两个方法名在开发/生产环境
 * <b>都一样</b>，所以 handler 必须是 {@code static}、并且保持默认的 {@code remap = true}——
 * 这样 {@code @At} 里那个原版 {@code LivingEntity#damage} 才能在 dev（Yarn 名，经 refmap）
 * 与 Kilt/Connector 环境都被正确解析；SR 自己的成员名不在映射表里，保持原名即可。
 */
@Mixin(targets = "com.summy.reliquary.effect.ShadowDash")
public abstract class SummyReliquaryShadowDashMixin {
	/** 基础斩击：乘 ER 的伤害乘区，并吃全部命中后效果（斩杀 + 出其不意） */
	@WrapOperation(
			method = "strike",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/LivingEntity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			),
			require = 0
	)
	private static boolean enchantmentReforged$shadowDashStrike(LivingEntity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		if (!(source.getAttacker() instanceof PlayerEntity player)) {
			return original.call(target, source, amount);
		}
		float adjusted = amount * SummyReliquaryCompat.meleeMultiplier(player);
		boolean hit = original.call(target, source, adjusted);
		if (hit) {
			ItemStack weapon = player.getMainHandStack();
			SummyReliquaryCompat.onMeleeHit(player, weapon, target, source, Math.max(adjusted, 0.0F), true, true);
		}
		return hit;
	}

	/**
	 * 强力斩击：保持 SR「这一击不吃增伤乘区」的原设计，只补命中后效果。
	 *
	 * <p>按本轮定稿：吃斩杀，但<b>不掷出其不意</b>（所以也不会出现二次结算与额外粒子）。
	 */
	@WrapOperation(
			method = "heavySlash",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/LivingEntity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			),
			require = 0
	)
	private static boolean enchantmentReforged$shadowDashHeavySlash(LivingEntity target, DamageSource source,
			float amount, Operation<Boolean> original) {
		if (!(source.getAttacker() instanceof PlayerEntity player)) {
			return original.call(target, source, amount);
		}
		boolean hit = original.call(target, source, amount);
		if (hit) {
			ItemStack weapon = player.getMainHandStack();
			SummyReliquaryCompat.onMeleeHit(player, weapon, target, source, Math.max(amount, 0.0F), true, false);
		}
		return hit;
	}
}
