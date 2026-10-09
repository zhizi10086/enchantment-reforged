package com.enchantmentreforged.mixin;

import com.enchantmentreforged.compat.SummyReliquaryCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 与 Summy Reliquary 的投掷长矛命中对齐到 ER 的近战管线（可选兼容）。
 *
 * <p><b>方法选择器必须写 intermediary 名 {@code method_7454}</b>（配 {@code remap = false}）：
 * SR 只产出 Forge jar，Fabric 侧由 Kilt 把它的 SRG 名重映射成 intermediary，
 * 实测 Kilt 的重映射产物（{@code .kilt/remappedMods/summy_reliquary_*.jar}）里
 * {@code ThrownSpear} 的那个覆写就叫 {@code protected void method_7454(net/minecraft/class_3966)}。
 * 写 dev/官方名 {@code onHitEntity} 是没用的 —— 它不在 Yarn 映射表里，注解处理器只会映射参数类型、
 * 成员名原样保留，于是永远匹配不到。
 *
 * <p><b>{@code @At} 的 owner 必须是 {@code Entity}（{@code class_1297}）、不能写 {@code LivingEntity}</b>：
 * SR 那句是 {@code Entity target = result.getEntity(); target.hurt(...)}，局部变量静态类型就是 {@code Entity}；
 * Kilt 重映射产物里实测为 {@code invokevirtual net/minecraft/class_1297.method_5643(...)}。
 * handler 的第一个参数同理必须是 {@code Entity}。
 *
 * <p>注：Fabric 的 dev 环境（Yarn 名）里装不了 SR（只有 Forge jar），所以本兼容只在 Kilt 实例验证。
 *
 * <p>SR 投掷后本体不消耗（只扣耐久），所以命中时主手仍是那把长矛，复仇的武器判定照常通过。
 */
@Mixin(targets = "com.summy.reliquary.entity.ThrownSpear")
public abstract class SummyReliquaryThrownSpearMixin {
	@WrapOperation(
			method = "method_7454(Lnet/minecraft/class_3966;)V",
			// Kilt 重映射后的 intermediary 名，不是 Yarn 名：不能再让它去查映射表
			remap = false,
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z",
					// 这条是原版成员，仍然要按 Yarn → intermediary 映射（class_1297;method_5643）
					remap = true
			),
			require = 0
	)
	private boolean enchantmentReforged$thrownSpear(Entity target, DamageSource source, float amount,
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
