package com.enchantmentreforged.mixin;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 近战粒子：屏蔽原版暴击星与"附魔命中火花"（客户端段）。
 *
 * <p>1.20.1 的 {@code PlayerEntity.addCritParticles} / {@code addEnchantedHitParticles} 是空方法，
 * 真正可见的粒子来自 {@link ClientPlayerEntity} 的覆盖实现——它在目标实体上挂一个粒子 emitter
 * （{@code ParticleManager.addEmitter(目标, CRIT / ENCHANTED_HIT)}）。这个 emitter 只影响
 * **本机玩家自己**的攻击，所以这里直接用本地设置判定：普通/暴击档位 ≥1（非"原版默认"）就取消。
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
	@Inject(method = "addCritParticles", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$hideCritEmitter(Entity target, CallbackInfo ci) {
		if (suppresses()) {
			ci.cancel();
		}
	}

	@Inject(method = "addEnchantedHitParticles", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$hideEnchantedHitEmitter(Entity target, CallbackInfo ci) {
		if (suppresses()) {
			ci.cancel();
		}
	}

	/** 本机玩家的普通/暴击档位是否 ≥1（1~12 都屏蔽原版粒子，只留我们配置的） */
	private static boolean suppresses() {
		return EnchantmentReforgedConfig.get().meleeParticle >= 1;
	}
}
