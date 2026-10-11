package com.enchantmentreforged.particle;

import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.network.ParticlePreferences;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

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
	/** 额外允许显示箭矢轨迹的软兼容投掷物（按实体注册名比对，不需要编译期依赖对方模组） */
	private static final Set<String> SOFT_ARROW_IDS = Set.of(
			"summy-reliquary-apocrypha:new_testament_arrow");

	private ArrowParticleTrails() {
	}

	/**
	 * 只作用于箭（含药水箭与光灵箭）与下面的软兼容白名单，三叉戟等其它投掷物不变。
	 *
	 * <p>白名单里目前是 Summy Reliquary: Apocrypha 的「落箭」：它同样继承 AbstractArrow、
	 * 由玩家射出时会被 PersistentProjectileEntityMixin 记上射手档位，因此表现与原版箭矢完全一致。
	 * 原版三叉戟（ThrownTrident）虽然也是 AbstractArrow，但**不在**名单里 ——
	 * 保持"非箭类投掷物不介入"的既有口径。
	 */
	public static boolean isArrow(AbstractArrow arrow) {
		EntityType<?> type = arrow.getType();
		if (type == EntityType.ARROW || type == EntityType.SPECTRAL_ARROW) {
			return true;
		}
		ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return key != null && SOFT_ARROW_IDS.contains(key.toString());
	}

	/**
	 * 服务端每 tick 对一支箭调用：给"开启了远距离显示"的观者代发轨迹粒子。
	 *
	 * <p>客户端调用时直接返回（客户端用本地渲染）。粒子类型只认箭上携带的射手档位：
	 * 没有携带（生物 / 发射器 / 指令生成 / 射手档位尚未上报）时整段跳过，不按观者档位代偿；
	 * 档位 0（原版默认）与 1（无粒子）也都不发。
	 */
	public static void tickServerTrail(AbstractArrow arrow) {
		if (arrow.level().isClientSide || !isArrow(arrow)) {
			return;
		}
		// 快速路径：没有人开启"远距离显示"时整段跳过（不分配、不遍历玩家）
		if (ParticlePreferences.longDistanceUsers() == 0) {
			return;
		}
		Vec3 velocity = arrow.getDeltaMovement();
		if (velocity.lengthSqr() < MOVING_EPSILON) {
			return;
		}
		if (!(arrow.level() instanceof ServerLevel world)) {
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
		Vec3 position = arrow.position().subtract(velocity.normalize().scale(0.25D));
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (viewer.level() != world) {
				continue;
			}
			ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(viewer.getUUID());
			if (preference == null || !preference.longDistance()) {
				continue;
			}
			int style = arrowStyle;
			ParticleOptions particle = ArrowParticleStyle.byIndex(style).particle();
			if (particle == null) {
				continue;
			}
			double radius = preference.distance();
			if (viewer.distanceToSqr(position) > radius * radius) {
				continue;
			}
			// force = true：原版会把半径上限放到 512 格（同时也带上包里的 longDistance 标记）
			world.sendParticles(viewer, particle, true, position.x, position.y, position.z,
					PARTICLES_PER_TICK, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}
}
