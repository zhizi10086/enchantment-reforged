package com.enchantmentreforged.mixin;

import com.enchantmentreforged.client.ArrowParticleRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 屏蔽光灵箭自带的闪粒（客户端段）。
 *
 * <p>光灵箭飞行时每 tick 会在 {@code tick} 里生成 1 颗粒子，同样无法被替换，
 * 因此当该箭的有效档位 ≥1（非"原版默认"）时直接不生成。
 */
@Mixin(SpectralArrow.class)
public abstract class SpectralArrowEntityParticleClientMixin {
	@WrapOperation(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
			)
	)
	private void enchantmentReforged$hideSpectralArrowParticles(Level world, ParticleOptions particle,
			double x, double y, double z, double velocityX, double velocityY, double velocityZ,
			Operation<Void> original) {
		if (ArrowParticleRenderer.suppressesVanillaParticles((AbstractArrow) (Object) this)) {
			return;
		}
		original.call(world, particle, x, y, z, velocityX, velocityY, velocityZ);
	}
}
