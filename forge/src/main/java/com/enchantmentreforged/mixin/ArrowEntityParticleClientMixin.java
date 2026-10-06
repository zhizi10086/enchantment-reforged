package com.enchantmentreforged.mixin;

import com.enchantmentreforged.client.ArrowParticleRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 屏蔽药水箭自带的颜色漩涡粒子（客户端段）。
 *
 * <p>药水箭飞行时会在 {@code makeParticle} 里每 tick 生成 2 颗颜色漩涡、
 * 插在方块里每 5 tick 生成 1 颗；插地 30 秒后在 {@code handleStatus} 里还有一次 20 颗的消散爆发。
 * 这些粒子无法被替换成我们配置的粒子，所以当该箭的有效档位 ≥1（非"原版默认"）时直接不生成。
 */
@Mixin(Arrow.class)
public abstract class ArrowEntityParticleClientMixin {
	@WrapOperation(
			method = "makeParticle",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
			)
	)
	private void enchantmentReforged$hideTippedArrowParticles(Level world, ParticleOptions particle,
			double x, double y, double z, double velocityX, double velocityY, double velocityZ,
			Operation<Void> original) {
		if (ArrowParticleRenderer.suppressesVanillaParticles((AbstractArrow) (Object) this)) {
			return;
		}
		original.call(world, particle, x, y, z, velocityX, velocityY, velocityZ);
	}

	// 官方映射里 Yarn 的 handleStatus = handleEntityEvent
	@WrapOperation(
			method = "handleEntityEvent",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"
			)
	)
	private void enchantmentReforged$hideTippedArrowBurst(Level world, ParticleOptions particle,
			double x, double y, double z, double velocityX, double velocityY, double velocityZ,
			Operation<Void> original) {
		if (ArrowParticleRenderer.suppressesVanillaParticles((AbstractArrow) (Object) this)) {
			return;
		}
		original.call(world, particle, x, y, z, velocityX, velocityY, velocityZ);
	}
}
