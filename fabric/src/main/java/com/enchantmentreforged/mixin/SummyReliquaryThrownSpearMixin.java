package com.enchantmentreforged.mixin;

import com.enchantmentreforged.compat.SummyReliquaryCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 与 Summy Reliquary 的投掷长矛命中对齐到 ER 的近战管线（可选兼容）。
 *
 * <p>Fabric 侧只写 dev 名 {@code onHitEntity}：它经 refmap 映射成 intermediary 后，正好对上
 * Kilt/Connector 转换过的 SR 覆写方法（SR 生产 jar 里的 SRG 名在 Fabric 环境不会出现）。
 *
 * <p>SR 投掷后本体不消耗（只扣耐久），所以命中时主手仍是那把长矛，复仇的武器判定照常通过。
 */
@Mixin(targets = "com.summy.reliquary.entity.ThrownSpear")
public abstract class SummyReliquaryThrownSpearMixin {
	@WrapOperation(
			method = "onHitEntity(Lnet/minecraft/util/hit/EntityHitResult;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/LivingEntity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			),
			require = 0
	)
	private boolean enchantmentReforged$thrownSpear(LivingEntity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		// SR 对非玩家也有一个 thrown(...) 兜底伤害源，那种情况原样放行
		if (!(source.getAttacker() instanceof PlayerEntity player)) {
			return original.call(target, source, amount);
		}
		float adjusted = amount * SummyReliquaryCompat.meleeMultiplier(player);
		boolean hit = original.call(target, source, adjusted);
		if (hit) {
			SummyReliquaryCompat.onMeleeHit(player, player.getMainHandStack(), target, source,
					Math.max(adjusted, 0.0F), true, true);
		}
		return hit;
	}
}
