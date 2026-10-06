package com.enchantmentreforged.particle;

import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.network.ParticlePreferences;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * 箭矢粒子轨迹：服务端代发部分（"远距离显示"用）。
 *
 * <p>客户端只会同步到 64 格内的箭（实体同步范围），再远的箭客户端根本没有实体，
 * 本地渲染自然就断了。开启远距离显示后改由服务端逐玩家代发粒子，最远到原版强制
 * 发粒子的上限（512 格），这样远处的箭也能看到轨迹。
 */
public final class ArrowParticleTrails {
	/** 每 tick 给每位观者发送的粒子数量（与客户端本地渲染保持一致的观感） */
	private static final int PARTICLES_PER_TICK = 2;
	/** 速度低于这个平方值视为"停下/插在方块上"，不再发粒子 */
	private static final double MOVING_EPSILON = 1.0E-4D;

	private ArrowParticleTrails() {
	}

	/** 只作用于箭（含药水箭与光灵箭），三叉戟等其它投掷物不变 */
	public static boolean isArrow(PersistentProjectileEntity arrow) {
		return arrow.getType() == EntityType.ARROW || arrow.getType() == EntityType.SPECTRAL_ARROW;
	}

	/**
	 * 服务端每 tick 对一支箭调用：给"开启了远距离显示"的观者代发轨迹粒子。
	 *
	 * <p>客户端调用时直接返回（客户端用本地渲染）。粒子类型只认箭上携带的射手档位：
	 * 没有携带（生物 / 发射器 / 指令生成 / 射手档位尚未上报）时整段跳过，不按观者档位代偿；
	 * 档位 0（原版默认）与 1（无粒子）也都不发。
	 */
	public static void tickServerTrail(PersistentProjectileEntity arrow) {
		if (arrow.getWorld().isClient || !isArrow(arrow)) {
			return;
		}
		// 快速路径：没有人开启"远距离显示"时整段跳过（不分配、不遍历玩家）
		if (ParticlePreferences.longDistanceUsers() == 0) {
			return;
		}
		Vec3d velocity = arrow.getVelocity();
		if (velocity.lengthSquared() < MOVING_EPSILON) {
			return;
		}
		if (!(arrow.getWorld() instanceof ServerWorld world)) {
			return;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return;
		}
		int arrowStyle = arrow instanceof ArrowParticleCarrier carrier
				? carrier.enchantmentReforged$getParticleStyle()
				: ArrowParticleStyle.UNSET;
		if (arrowStyle == ArrowParticleStyle.UNSET) {
			// 非玩家射出的箭（或射手档位未知）：本模组不介入，保持原版
			return;
		}
		// 与客户端本地渲染一致：位置沿速度反方向拖一小段
		Vec3d position = arrow.getPos().subtract(velocity.normalize().multiply(0.25D));
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			if (viewer.getWorld() != world) {
				continue;
			}
			ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(viewer.getUuid());
			if (preference == null || !preference.longDistance()) {
				continue;
			}
			int style = arrowStyle;
			ParticleEffect particle = ArrowParticleStyle.byIndex(style).particle();
			if (particle == null) {
				continue;
			}
			double radius = preference.distance();
			if (viewer.squaredDistanceTo(position) > radius * radius) {
				continue;
			}
			// force = true：原版会把半径上限放到 512 格（同时也带上包里的 longDistance 标记）
			world.spawnParticles(viewer, particle, true, position.x, position.y, position.z,
					PARTICLES_PER_TICK, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}
}
