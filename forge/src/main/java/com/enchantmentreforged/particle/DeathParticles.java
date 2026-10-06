package com.enchantmentreforged.particle;

import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.network.ParticlePreferences;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 玩家死亡时在原地爆发的一团粒子。
 *
 * <p>强度**固定**（48 颗、扩散 0.8、初速 0.35，单波爆发），配置项 {@code death_particle}
 * 只决定粒子种类（0/1 = 不生成）。粒子类型取**死者**的档位；死者未上报（旧客户端/未装模组）
 * 时退回观者自己的档位。可见范围沿用现有规则：32 格内始终可见，观者开启"远距离显示"时
 * 按其半径扩展。只对玩家生效，生物死亡不生成。
 */
public final class DeathParticles {
	private static final int COUNT = 48;
	private static final double SPREAD = 0.8D;
	private static final double SPEED = 0.35D;
	/** 没有开启远距离时的可见半径（格） */
	private static final double NEARBY_RADIUS = 32.0D;

	private DeathParticles() {
	}

	/** 玩家死亡时调用（服务端；生物死亡直接忽略） */
	public static void onPlayerDeath(LivingEntity victim) {
		if (!(victim instanceof Player) || victim.level().isClientSide) {
			return;
		}
		if (!EnchantmentReforgedConfig.get().enableDeathParticle) {
			return;
		}
		if (!(victim.level() instanceof ServerLevel world)) {
			return;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return;
		}
		ParticlePreferences.Preference victimPreference = ParticlePreferences.preferenceOf(victim.getUUID());
		Vec3 position = new Vec3(victim.getX(), (victim.getY() + victim.getBbHeight() * 0.5D), victim.getZ());
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (viewer.level() != world) {
				continue;
			}
			ParticlePreferences.Preference viewerPreference = ParticlePreferences.preferenceOf(viewer.getUUID());
			int style = victimPreference != null
					? victimPreference.deathStyle()
					: (viewerPreference == null ? 0 : viewerPreference.deathStyle());
			ParticleOptions particle = ArrowParticleStyle.byIndex(style).particle();
			if (particle == null) {
				continue;
			}
			double radius = NEARBY_RADIUS;
			if (viewerPreference != null && viewerPreference.longDistance()) {
				radius = Math.max(radius, viewerPreference.distance());
			}
			if (viewer.distanceToSqr(position) > radius * radius) {
				continue;
			}
			world.sendParticles(viewer, particle, true, position.x, position.y, position.z,
					COUNT, SPREAD, SPREAD, SPREAD, SPEED);
		}
	}
}
