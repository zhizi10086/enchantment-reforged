package com.enchantmentreforged.mixin;

import com.enchantmentreforged.compat.SummyReliquaryCompat;
import com.enchantmentreforged.particle.MeleeParticles;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 与 Summy Reliquary 的「遁入暗影」两段斩击对齐到 ER 的近战管线（可选兼容）。
 *
 * <p>没用 SR 当依赖：目标用字符串指定，没装 SR 时由 {@code SummyReliquaryMixinPlugin} 整份跳过。
 *
 * <p>注意两个被注入的方法都是 SR 自己的 <b>私有静态方法</b>，而且这两个方法名在开发/生产环境
 * <b>都一样</b>，所以 handler 必须是 {@code static}、并且这里保持默认的 {@code remap = true}——
 * 这样 {@code @At} 里那个原版 {@code LivingEntity#hurt} 才能在 dev（官方名）与生产（SRG 名）
 * 都被正确解析；SR 自己的成员名不在映射表里，保持原名即可。
 */
@Mixin(targets = "com.summy.reliquary.effect.ShadowDash")
public abstract class SummyReliquaryShadowDashMixin {
	/** 基础斩击：乘 ER 的伤害乘区，并吃全部命中后效果（斩杀 + 出其不意） */
	@WrapOperation(
			method = "strike",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			),
			require = 0
	)
	private static boolean enchantmentReforged$shadowDashStrike(LivingEntity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		Player player = source.getDirectEntity() instanceof Player attacker ? attacker : null;
		// 先登记"紧随其后那条原版横扫弧线"的归属：SR 是先 hurt、再发弧线
		MeleeParticles.armExternalSlashArc(player);
		if (player == null) {
			return original.call(target, source, amount);
		}
		float adjusted = amount * SummyReliquaryCompat.meleeMultiplier(player);
		boolean hit = original.call(target, source, adjusted);
		SummyReliquaryCompat.settleHit("strike", player, player.getMainHandItem(), target, source,
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
					target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			),
			require = 0
	)
	private static boolean enchantmentReforged$shadowDashHeavySlash(LivingEntity target, DamageSource source,
			float amount, Operation<Boolean> original) {
		Player player = source.getDirectEntity() instanceof Player attacker ? attacker : null;
		MeleeParticles.armExternalSlashArc(player);
		if (player == null) {
			return original.call(target, source, amount);
		}
		boolean hit = original.call(target, source, amount);
		SummyReliquaryCompat.settleHit("heavySlash", player, player.getMainHandItem(), target, source,
				amount, amount, hit, true, false, true);
		return hit;
	}

	/**
	 * SR 每结算一次斩击都会在目标身上放一条原版横扫弧线（{@code SWEEP_ATTACK}）。
	 *
	 * <p>它不是玩家左键的横扫，SR 自己发的，所以原版档位管不到它 —— 这里按我们的近战 / 横扫档位接管：
	 * 档位 0（原版默认）原样放行；任一档位 ≥1 时不再发原版弧线，改在该位置按档位生成我们的粒子。
	 */
	@WrapOperation(
			method = "strike",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"
			),
			require = 0
	)
	private static int enchantmentReforged$takeOverStrikeArc(ServerLevel world, ParticleOptions particle,
			double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed,
			Operation<Integer> original) {
		if (MeleeParticles.consumeExternalSlashArc(particle, new Vec3(x, y, z))) {
			return 0;
		}
		return original.call(world, particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
	}

	/** 强力斩击同样会逐目标放一条原版横扫弧线，处置同上 */
	@WrapOperation(
			method = "heavySlash",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"
			),
			require = 0
	)
	private static int enchantmentReforged$takeOverHeavyArc(ServerLevel world, ParticleOptions particle,
			double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed,
			Operation<Integer> original) {
		if (MeleeParticles.consumeExternalSlashArc(particle, new Vec3(x, y, z))) {
			return 0;
		}
		return original.call(world, particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
	}
}
