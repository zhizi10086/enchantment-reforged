package com.enchantmentreforged.particle;

import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.network.ParticlePreferences;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 近战命中的自定义粒子（与箭矢粒子共用同一套 0~12 档位表 + 三档强度）。
 *
 * <p>两条**互相独立**的通道，对应原版两种互斥的命中表现：
 * <ul>
 *     <li>{@code melee_particle}：普通与暴击命中。0 = 原版默认、1 = 不生成并屏蔽暴击星/附魔火花/受伤云、
 *         2~12 = 在命中处生成所选粒子并屏蔽上述原版粒子；</li>
 *     <li>{@code melee_sweep_particle}：横扫命中。0 = 原版默认（保留横扫弧线）、1 = 不生成并屏蔽弧线、
 *         2~12 = 在身前挥舞弧线上生成所选粒子并屏蔽原版弧线。</li>
 * </ul>
 *
 * <p>强度档位（{@code melee_particle_effect}：简化/标准/大量）决定单帧数量、扩散、初速与"余晖"tick 数；
 * 喷发期间放进的短时队列在服务端 tick 里推进，队列上限 {@link #MAX_BURSTS} 条。
 *
 * <p>档位与强度都取**攻击者**的设置；攻击者未上报（刚进服、握手未完成）时一律不介入、保持原版，
 * 不再用观者自己的设置代偿——即"只有玩家的、且档位已知的攻击才有自定义粒子"。
 * 可见范围：32 格内的观者始终能看到，观者开启"远距离显示"时按自己的半径扩展（上限 512）。
 */
public final class MeleeParticles {
	/** 没有开启远距离时的可见半径（格），与原版暴击星/横扫一致 */
	private static final double NEARBY_RADIUS = 32.0D;
	/** 命中点相对目标中心、沿"攻击者 → 目标"反向的偏移（格） */
	private static final double BACK_OFFSET = 0.3D;
	/** 横扫弧线的采样距离（格），最多取前 sweepPoints 个 */
	private static final double[] SWEEP_DISTANCES = {0.6D, 1.0D, 1.4D, 0.8D, 1.2D};
	/** 待喷发队列上限（防极端刷屏） */
	private static final int MAX_BURSTS = 64;

	/** 粒子通道：普通/暴击命中、横扫命中、出其不意的额外粒子 */
	private enum Channel {
		HIT,
		SWEEP,
		SURPRISE
	}

	/** 一条待喷发：世界、攻击者 UUID、基准点集合、通道、已经发了几个 tick */
	private record Burst(ServerWorld world, UUID attackerId, Vec3d[] points, Channel channel, int age) {
	}

	private static final List<Burst> BURSTS = new ArrayList<>();

	private MeleeParticles() {
	}

	/** 攻击者的"普通/暴击"档位 ≥1（非"原版默认"）时，屏蔽原版暴击星 / 附魔火花 / 受伤云 */
	public static boolean suppressesCritParticles(LivingEntity attacker) {
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUuid());
		return preference != null && preference.meleeStyle() >= 1;
	}

	/** 攻击者的"横扫"档位 ≥1（非"原版默认"）时，屏蔽原版横扫弧线 */
	public static boolean suppressesSweepParticles(LivingEntity attacker) {
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUuid());
		return preference != null && preference.sweepStyle() >= 1;
	}

	/**
	 * 玩家近战命中成功后调用（普通与暴击命中；只在服务端生效）。
	 *
	 * <p>只有<b>已登记档位的玩家</b>才会生成粒子：生物近战进不来，
	 * 刚进服还没上报档位的玩家也一律按原版不介入。
	 *
	 * @param attacker 攻击者
	 * @param target   被命中的实体（主命中的那个目标）
	 */
	public static void spawnOnHit(LivingEntity attacker, Entity target) {
		if (target == null || attacker == target || !isServerPlayerAttacker(attacker)) {
			return;
		}
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUuid());
		if (preference == null || preference.meleeStyle() == 0) {
			// 档位未知（未上报）或明确选了"原版默认"：不生成任何粒子
			return;
		}
		Vec3d point = hitPosition(attacker, target);
		queueBurst((ServerWorld) attacker.getWorld(), attacker.getUuid(), new Vec3d[]{point}, Channel.HIT);
	}

	/** 横扫命中时调用（服务端）：在攻击者身前的挥舞弧线上生成粒子 */
	public static void spawnOnSweep(LivingEntity attacker) {
		if (!isServerPlayerAttacker(attacker)) {
			return;
		}
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUuid());
		if (preference == null || preference.sweepStyle() == 0) {
			return;
		}
		queueBurst((ServerWorld) attacker.getWorld(), attacker.getUuid(), sweepPoints(attacker), Channel.SWEEP);
	}

	/**
	 * 出其不意触发时的额外粒子：类型独立（{@code surprise_particle}），强度沿用 {@code melee_particle_effect}。
	 *
	 * <p>在目标位置爆发；档位未知或 0/1 档（攻击者明确选择"不生成"）时什么都不发。
	 */
	public static void spawnSurprise(LivingEntity attacker, Entity target) {
		if (target == null || !isServerPlayerAttacker(attacker)) {
			return;
		}
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUuid());
		if (preference == null || preference.surpriseStyle() <= 1) {
			return;
		}
		queueBurst((ServerWorld) attacker.getWorld(), attacker.getUuid(),
				new Vec3d[]{hitPosition(attacker, target)}, Channel.SURPRISE);
	}

	/** 服务端每 tick 推进待喷发队列（在 END_SERVER_TICK 里调用一次） */
	public static void tickBursts() {
		if (BURSTS.isEmpty()) {
			return;
		}
		for (int i = BURSTS.size() - 1; i >= 0; i--) {
			Burst burst = BURSTS.get(i);
			if (burst.world().getServer() == null) {
				BURSTS.remove(i);
				continue;
			}
			emit(burst);
			if (burst.age() >= MeleeParticleLevels.MAX_TRAIL_TICKS) {
				BURSTS.remove(i);
			} else {
				BURSTS.set(i, new Burst(burst.world(), burst.attackerId(), burst.points(), burst.channel(),
						burst.age() + 1));
			}
		}
	}

	/** 服务器停止时清空 */
	public static void clearBursts() {
		BURSTS.clear();
	}

	/** 只有"服务端上的玩家"才会生成近战粒子 */
	private static boolean isServerPlayerAttacker(LivingEntity attacker) {
		return attacker instanceof PlayerEntity && attacker.getWorld() instanceof ServerWorld;
	}

	private static void queueBurst(ServerWorld world, UUID attackerId, Vec3d[] points, Channel channel) {
		if (BURSTS.size() >= MAX_BURSTS) {
			// 队列满了：只发单帧，不再排队余晖（保证不无限堆积）
			emit(new Burst(world, attackerId, points, channel, 0));
			return;
		}
		BURSTS.add(new Burst(world, attackerId, points, channel, 0));
	}

	/** 发一个 tick 的粒子（按档位与年龄决定数量） */
	private static void emit(Burst burst) {
		ServerWorld world = burst.world();
		MinecraftServer server = world.getServer();
		if (server == null) {
			return;
		}
		ParticlePreferences.Preference attackerPreference = ParticlePreferences.preferenceOf(burst.attackerId());
		if (attackerPreference == null) {
			// 攻击者档位未知（未上报）：不介入，保持原版；不用观者的档位代偿
			return;
		}
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			if (viewer.getWorld() != world) {
				continue;
			}
			ParticlePreferences.Preference viewerPreference = ParticlePreferences.preferenceOf(viewer.getUuid());
			boolean sweep = burst.channel() == Channel.SWEEP;
			int style = switch (burst.channel()) {
				case HIT -> attackerPreference.meleeStyle();
				case SWEEP -> attackerPreference.sweepStyle();
				case SURPRISE -> attackerPreference.surpriseStyle();
			};
			int levelIndex = attackerPreference.meleeEffect();
			ParticleEffect particle = ArrowParticleStyle.byIndex(style).particle();
			if (particle == null) {
				continue;
			}
			MeleeParticleLevels level = MeleeParticleLevels.byIndex(levelIndex);
			int count = level.countForTick(sweep, burst.age());
			if (count <= 0) {
				continue;
			}
			double spread = sweep ? level.sweepSpread() : level.hitSpread();
			double speed = sweep ? level.sweepSpeed() : level.hitSpeed();
			Vec3d[] points = burst.points();
			// 单帧：count 是"每个采样点"的数量；余晖：count 是"这一 tick 的总数"，按采样点平分
			int pointsUsed = sweep ? Math.min(level.sweepPoints(), points.length) : 1;
			int perPoint = burst.age() <= 0 ? count : Math.max(1, count / pointsUsed);
			for (int i = 0; i < pointsUsed; i++) {
				Vec3d point = points[i];
				if (!withinRadius(viewer, viewerPreference, point)) {
					continue;
				}
				world.spawnParticles(viewer, particle, true, point.x, point.y, point.z,
						perPoint, spread, spread, spread, speed);
			}
		}
	}

	private static boolean withinRadius(ServerPlayerEntity viewer, ParticlePreferences.Preference preference,
			Vec3d position) {
		double radius = NEARBY_RADIUS;
		if (preference != null && preference.longDistance()) {
			radius = Math.max(radius, preference.distance());
		}
		return viewer.squaredDistanceTo(position) <= radius * radius;
	}

	/** 命中点：目标身体中部，再沿"攻击者 → 目标"方向退回一点点，粒子就贴在命中那侧 */
	private static Vec3d hitPosition(LivingEntity attacker, Entity target) {
		Vec3d center = new Vec3d(target.getX(), target.getBodyY(0.5D), target.getZ());
		Vec3d direction = center.subtract(attacker.getPos());
		if (direction.lengthSquared() < 1.0E-6D) {
			return center;
		}
		return center.subtract(direction.normalize().multiply(BACK_OFFSET));
	}

	/** 横扫弧线采样点：沿视线方向在不同距离各取一个点（最多用前 sweepPoints 个） */
	private static Vec3d[] sweepPoints(LivingEntity attacker) {
		float yaw = attacker.getYaw() * ((float) Math.PI / 180.0F);
		double forwardX = -MathHelper.sin(yaw);
		double forwardZ = MathHelper.cos(yaw);
		double y = attacker.getBodyY(0.5D);
		Vec3d[] points = new Vec3d[SWEEP_DISTANCES.length];
		for (int i = 0; i < SWEEP_DISTANCES.length; i++) {
			double distance = SWEEP_DISTANCES[i];
			points[i] = new Vec3d(attacker.getX() + forwardX * distance, y, attacker.getZ() + forwardZ * distance);
		}
		return points;
	}
}
