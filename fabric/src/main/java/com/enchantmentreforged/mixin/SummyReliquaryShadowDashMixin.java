package com.enchantmentreforged.mixin;

import com.enchantmentreforged.compat.SummyReliquaryCompat;
import com.enchantmentreforged.particle.MeleeParticles;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
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
 *
 * <p>除伤害与命中后效果外，这里还接管 SR 每次斩击自己发的那条原版横扫弧线
 * （{@code SWEEP_ATTACK}）：它不经过玩家左键路径，原版档位管不到它，所以按我们的近战 / 横扫档位处理。
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
		PlayerEntity player = source.getAttacker() instanceof PlayerEntity attacker ? attacker : null;
		// 先登记"紧随其后那条原版横扫弧线"的归属：SR 是先 hurt、再发弧线
		MeleeParticles.armExternalSlashArc(player);
		if (player == null) {
			return original.call(target, source, amount);
		}
		float adjusted = amount * SummyReliquaryCompat.meleeMultiplier(player);
		boolean hit = original.call(target, source, adjusted);
		SummyReliquaryCompat.settleHit("strike", player, player.getMainHandStack(), target, source,
				amount, adjusted, hit, true, true, true);
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
		PlayerEntity player = source.getAttacker() instanceof PlayerEntity attacker ? attacker : null;
		MeleeParticles.armExternalSlashArc(player);
		if (player == null) {
			return original.call(target, source, amount);
		}
		boolean hit = original.call(target, source, amount);
		SummyReliquaryCompat.settleHit("heavySlash", player, player.getMainHandStack(), target, source,
				amount, amount, hit, true, false, true);
		return hit;
	}

	/**
	 * SR 每结算一次斩击都会在目标身上放一条原版横扫弧线（{@code SWEEP_ATTACK}）。
	 *
	 * <p>它不是玩家左键的横扫，SR 自己发的，所以原版档位管不到它 —— 这里按我们的近战/横扫档位接管：
	 * 档位 0（原版默认）原样放行；任一档位 ≥1 时不再发原版弧线，改在该位置按档位生成我们的粒子。
	 */
	@WrapOperation(
			method = "strike",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/world/ServerWorld;spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I"
			),
			require = 0
	)
	private static int enchantmentReforged$takeOverStrikeArc(ServerWorld world, ParticleEffect particle,
			double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed,
			Operation<Integer> original) {
		if (MeleeParticles.consumeExternalSlashArc(particle, new Vec3d(x, y, z))) {
			return 0;
		}
		return original.call(world, particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
	}

	/** 强力斩击同样会逐目标放一条原版横扫弧线，处置同上 */
	@WrapOperation(
			method = "heavySlash",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/world/ServerWorld;spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I"
			),
			require = 0
	)
	private static int enchantmentReforged$takeOverHeavyArc(ServerWorld world, ParticleEffect particle,
			double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed,
			Operation<Integer> original) {
		if (MeleeParticles.consumeExternalSlashArc(particle, new Vec3d(x, y, z))) {
			return 0;
		}
		return original.call(world, particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
	}
}
