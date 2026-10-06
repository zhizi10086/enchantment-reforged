package com.enchantmentreforged.mixin;

import com.enchantmentreforged.client.ArrowParticleRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 屏蔽光灵箭自带的闪粒（客户端段）。
 *
 * <p>光灵箭飞行时每 tick 会在 {@code tick} 里生成 1 颗粒子，同样无法被替换，
 * 因此当该箭的有效档位 ≥1（非"原版默认"）时直接不生成。
 */
@Environment(EnvType.CLIENT)
@Mixin(SpectralArrowEntity.class)
public abstract class SpectralArrowEntityParticleClientMixin {
	@WrapOperation(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
			)
	)
	private void enchantmentReforged$hideSpectralArrowParticles(World world, ParticleEffect particle,
			double x, double y, double z, double velocityX, double velocityY, double velocityZ,
			Operation<Void> original) {
		if (ArrowParticleRenderer.suppressesVanillaParticles((PersistentProjectileEntity) (Object) this)) {
			return;
		}
		original.call(world, particle, x, y, z, velocityX, velocityY, velocityZ);
	}
}
