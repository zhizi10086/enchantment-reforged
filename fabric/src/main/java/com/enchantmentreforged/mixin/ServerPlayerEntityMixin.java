package com.enchantmentreforged.mixin;

import com.enchantmentreforged.particle.MeleeParticles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 近战粒子：屏蔽原版"暴击星 / 附魔命中火花"的**服务端广播**。
 *
 * <p>1.20.1 的原版近战命中粒子有两条来源：
 * <ol>
 *     <li>本机预测：{@code ClientPlayerEntity.addCritParticles/addEnchantedHitParticles}
 *         在本地挂粒子 emitter（只影响攻击者自己的画面，由 {@code ClientPlayerEntityMixin} 处理）；</li>
 *     <li><b>服务端广播</b>：{@link ServerPlayerEntity} 覆盖了同样两个方法，会向附近玩家发送
 *         {@code EntityAnimationS2CPacket(target, 4/5)}，各客户端在自己的 {@code ClientPlayNetworkHandler}
 *         里把这些动画转成 CRIT / ENCHANTED_HIT 的粒子 emitter —— 这条对所有人可见，必须一起屏蔽。</li>
 * </ol>
 *
 * <p>动画 4/5 只驱动粒子 emitter，不影响暴击音效、伤害与挥砍动画，所以直接取消是安全的。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
	@Inject(method = "addCritParticles", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$hideCritAnimation(Entity target, CallbackInfo ci) {
		if (MeleeParticles.suppressesCritParticles((PlayerEntity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "addEnchantedHitParticles", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$hideEnchantedHitAnimation(Entity target, CallbackInfo ci) {
		if (MeleeParticles.suppressesCritParticles((PlayerEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
