package com.enchantmentreforged.mixin;

import com.enchantmentreforged.client.ArrowParticleRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 箭矢粒子的客户端表现（只在客户端加载）。
 *
 * <ul>
 *     <li>档位 ≥1 时屏蔽原版暴击星粒子（只影响粒子，暴击伤害照常）；</li>
 *     <li>档位 ≥2 时每 tick 生成所选的原版粒子作为飞行轨迹。</li>
 * </ul>
 * 全部是客户端本地生成，不产生任何网络开销。
 */
@Environment(EnvType.CLIENT)
@Mixin(PersistentProjectileEntity.class)
public abstract class ArrowParticleClientMixin {
	@WrapOperation(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/projectile/PersistentProjectileEntity;isCritical()Z"
			)
	)
	private boolean enchantmentReforged$hideCritParticles(PersistentProjectileEntity arrow, Operation<Boolean> original) {
		if (!original.call(arrow)) {
			return false;
		}
		return !ArrowParticleRenderer.suppressesVanillaParticles(arrow);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void enchantmentReforged$spawnParticleTrail(CallbackInfo ci) {
		ArrowParticleRenderer.spawnTrail((PersistentProjectileEntity) (Object) this);
	}
}
