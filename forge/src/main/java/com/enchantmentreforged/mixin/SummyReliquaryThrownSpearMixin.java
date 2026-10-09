package com.enchantmentreforged.mixin;

import com.enchantmentreforged.compat.SummyReliquaryCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 与 Summy Reliquary 的投掷长矛命中对齐到 ER 的近战管线（可选兼容）。
 *
 * <p>{@code ThrownSpear#onHitEntity} 是原版 {@code AbstractArrow#onHitEntity} 的覆写，
 * 在生产 jar 里被重命名为 {@code m_5790_}，因此这里写两条方法选择器、各 {@code require = 0}：
 * dev 环境命中 {@code onHitEntity}，生产环境命中 {@code m_5790_}。
 * 两条都保持默认 {@code remap = true}，让 {@code @At} 里的原版 {@code LivingEntity#hurt}
 * 分别在 dev / 生产被解析成对应名字（写死 {@code remap = false} 反而只会在 dev 生效）。
 *
 * <p>SR 投掷后本体不消耗（只扣耐久），所以命中时主手仍是那把长矛，复仇的武器判定照常通过。
 */
@Mixin(targets = "com.summy.reliquary.entity.ThrownSpear")
public abstract class SummyReliquaryThrownSpearMixin {
	/** dev 环境的方法名 */
	@WrapOperation(
			method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			),
			require = 0
	)
	private boolean enchantmentReforged$thrownSpearDev(LivingEntity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		return enchantmentReforged$applyThrownSpear(target, source, amount, original);
	}

	/** 生产环境的方法名（SRG） */
	@WrapOperation(
			method = "m_5790_(Lnet/minecraft/world/phys/EntityHitResult;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			),
			require = 0
	)
	private boolean enchantmentReforged$thrownSpearProd(LivingEntity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		return enchantmentReforged$applyThrownSpear(target, source, amount, original);
	}

	/** 投掷命中：乘 ER 的伤害乘区，并吃全部命中后效果（与左键近战同档） */
	@Unique
	private static boolean enchantmentReforged$applyThrownSpear(LivingEntity target, DamageSource source,
			float amount, Operation<Boolean> original) {
		// SR 对非玩家也有一个 thrown(...) 兜底伤害源，那种情况原样放行
		if (!(source.getDirectEntity() instanceof Player player)) {
			return original.call(target, source, amount);
		}
		float adjusted = amount * SummyReliquaryCompat.meleeMultiplier(player);
		boolean hit = original.call(target, source, adjusted);
		if (hit) {
			SummyReliquaryCompat.onMeleeHit(player, player.getMainHandItem(), target, source,
					Math.max(adjusted, 0.0F), true, true);
		}
		return hit;
	}
}
