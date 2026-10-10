package com.enchantmentreforged.particle;

import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.network.ParticlePreferences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

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
	private record Burst(ServerLevel world, UUID attackerId, Vec3[] points, Channel channel, int age) {
	}

	private static final List<Burst> BURSTS = new ArrayList<>();

	/**
	 * 外部模组（Summy Reliquary 的斩击）那条原版弧线的归属：命中时登记、发弧线时消费。
	 *
	 * <p>实体伤害结算都在服务端线程上串行执行，所以普通静态字段即可。
	 */
	private static LivingEntity externalArcOwner;
	private static boolean externalArcTakeOver;

	private MeleeParticles() {
	}

	/** 攻击者的"普通/暴击"档位 ≥1（非"原版默认"）时，屏蔽原版暴击星 / 附魔火花 / 受伤云 */
	public static boolean suppressesCritParticles(LivingEntity attacker) {
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUUID());
		return preference != null && preference.meleeStyle() >= 1;
	}

	/** 攻击者的"横扫"档位 ≥1（非"原版默认"）时，屏蔽原版横扫弧线 */
	public static boolean suppressesSweepParticles(LivingEntity attacker) {
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUUID());
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
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUUID());
		if (preference == null || preference.meleeStyle() == 0) {
			// 档位未知（未上报）或明确选了"原版默认"：不生成任何粒子
			return;
		}
		Vec3 point = hitPosition(attacker, target);
		queueBurst((ServerLevel) attacker.level(), attacker.getUUID(), new Vec3[]{point}, Channel.HIT);
	}

	/** 横扫命中时调用（服务端）：在攻击者身前的挥舞弧线上生成粒子 */
	public static void spawnOnSweep(LivingEntity attacker) {
		if (!isServerPlayerAttacker(attacker)) {
			return;
		}
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUUID());
		if (preference == null || preference.sweepStyle() == 0) {
			return;
		}
		queueBurst((ServerLevel) attacker.level(), attacker.getUUID(), sweepPoints(attacker), Channel.SWEEP);
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
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUUID());
		if (preference == null || preference.surpriseStyle() <= 1) {
			return;
		}
		queueBurst((ServerLevel) attacker.level(), attacker.getUUID(),
				new Vec3[]{hitPosition(attacker, target)}, Channel.SURPRISE);
	}

	// ==================== 外部弧线接管（Summy Reliquary 的斩击） ====================

	/**
	 * 兼容层用：外部模组（SR 的 {@code ShadowDash#strike} / {@code #heavySlash}）跟着自己的命中
	 * 发送的那条原版横扫弧线（{@code SWEEP_ATTACK}），这一击要不要由我们接管。
	 *
	 * <p>口径与近战粒子一致：主命中档或横扫档**任一 ≥1**（玩家明确要求"不要原版粒子"）就接管；
	 * 两档都是 0（原版默认）时保持原版行为。
	 */
	public static boolean takesOverExternalSlashArc(LivingEntity attacker) {
		if (attacker == null) {
			return false;
		}
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUUID());
		return preference != null && (preference.meleeStyle() >= 1 || preference.sweepStyle() >= 1);
	}

	/**
	 * 兼容层用：SR 的命中包装里登记"紧随其后的那条外部弧线归谁"。
	 *
	 * <p>SR 的两个斩击方法都是"先 hurt、再发弧线"，所以命中时登记、发弧线时消费，一次一清。
	 */
	public static void armExternalSlashArc(LivingEntity attacker) {
		externalArcOwner = attacker;
		externalArcTakeOver = takesOverExternalSlashArc(attacker);
	}

	/**
	 * 兼容层用：外部弧线真的到来时调用。
	 *
	 * @return true = 已由我们接管（调用方不要再发原版弧线）；false = 放行原版
	 */
	public static boolean consumeExternalSlashArc(ParticleOptions particle, Vec3 point) {
		LivingEntity owner = externalArcOwner;
		boolean takeOver = externalArcTakeOver;
		// 用掉即清空：只影响"紧随命中之后的那一条弧线"
		externalArcOwner = null;
		externalArcTakeOver = false;
		if (!takeOver || owner == null || particle != ParticleTypes.SWEEP_ATTACK || point == null) {
			return false;
		}
		spawnExternalSlashParticles(owner, point);
		return true;
	}

	/**
	 * 兼容层用：接管外部弧线后，在弧线原本的位置按我们的档位喷发粒子。
	 *
	 * <p>横扫档 ≥2 优先（那本来就是一条弧线），否则退回主命中档 ≥2；都不到 2 就只屏蔽、不生成。
	 */
	public static void spawnExternalSlashParticles(LivingEntity attacker, Vec3 point) {
		if (point == null || !isServerPlayerAttacker(attacker)) {
			return;
		}
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(attacker.getUUID());
		if (preference == null) {
			return;
		}
		Channel channel;
		if (preference.sweepStyle() >= 2) {
			channel = Channel.SWEEP;
		} else if (preference.meleeStyle() >= 2) {
			channel = Channel.HIT;
		} else {
			return;
		}
		queueBurst((ServerLevel) attacker.level(), attacker.getUUID(), new Vec3[]{point}, channel);
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
		return attacker instanceof Player && attacker.level() instanceof ServerLevel;
	}

	private static void queueBurst(ServerLevel world, UUID attackerId, Vec3[] points, Channel channel) {
		if (BURSTS.size() >= MAX_BURSTS) {
			// 队列满了：只发单帧，不再排队余晖（保证不无限堆积）
			emit(new Burst(world, attackerId, points, channel, 0));
			return;
		}
		BURSTS.add(new Burst(world, attackerId, points, channel, 0));
	}

	/** 发一个 tick 的粒子（按档位与年龄决定数量） */
	private static void emit(Burst burst) {
		ServerLevel world = burst.world();
		MinecraftServer server = world.getServer();
		if (server == null) {
			return;
		}
		ParticlePreferences.Preference attackerPreference = ParticlePreferences.preferenceOf(burst.attackerId());
		if (attackerPreference == null) {
			// 攻击者档位未知（未上报）：不介入，保持原版；不用观者的档位代偿
			return;
		}
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (viewer.level() != world) {
				continue;
			}
			ParticlePreferences.Preference viewerPreference = ParticlePreferences.preferenceOf(viewer.getUUID());
			boolean sweep = burst.channel() == Channel.SWEEP;
			int style = switch (burst.channel()) {
				case HIT -> attackerPreference.meleeStyle();
				case SWEEP -> attackerPreference.sweepStyle();
				case SURPRISE -> attackerPreference.surpriseStyle();
			};
			int levelIndex = attackerPreference.meleeEffect();
			ParticleOptions particle = ArrowParticleStyle.byIndex(style).particle();
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
			Vec3[] points = burst.points();
			// 单帧：count 是"每个采样点"的数量；余晖：count 是"这一 tick 的总数"，按采样点平分
			int pointsUsed = sweep ? Math.min(level.sweepPoints(), points.length) : 1;
			int perPoint = burst.age() <= 0 ? count : Math.max(1, count / pointsUsed);
			for (int i = 0; i < pointsUsed; i++) {
				Vec3 point = points[i];
				if (!withinRadius(viewer, viewerPreference, point)) {
					continue;
				}
				world.sendParticles(viewer, particle, true, point.x, point.y, point.z,
						perPoint, spread, spread, spread, speed);
			}
		}
	}

	private static boolean withinRadius(ServerPlayer viewer, ParticlePreferences.Preference preference,
			Vec3 position) {
		double radius = NEARBY_RADIUS;
		if (preference != null && preference.longDistance()) {
			radius = Math.max(radius, preference.distance());
		}
		return viewer.distanceToSqr(position) <= radius * radius;
	}

	/** 命中点：目标身体中部，再沿"攻击者 → 目标"方向退回一点点，粒子就贴在命中那侧 */
	private static Vec3 hitPosition(LivingEntity attacker, Entity target) {
		Vec3 center = new Vec3(target.getX(), (target.getY() + target.getBbHeight() * 0.5D), target.getZ());
		Vec3 direction = center.subtract(attacker.position());
		if (direction.lengthSqr() < 1.0E-6D) {
			return center;
		}
		return center.subtract(direction.normalize().scale(BACK_OFFSET));
	}

	/** 横扫弧线采样点：沿视线方向在不同距离各取一个点（最多用前 sweepPoints 个） */
	private static Vec3[] sweepPoints(LivingEntity attacker) {
		float yaw = attacker.getYRot() * ((float) Math.PI / 180.0F);
		double forwardX = -Mth.sin(yaw);
		double forwardZ = Mth.cos(yaw);
		double y = (attacker.getY() + attacker.getBbHeight() * 0.5D);
		Vec3[] points = new Vec3[SWEEP_DISTANCES.length];
		for (int i = 0; i < SWEEP_DISTANCES.length; i++) {
			double distance = SWEEP_DISTANCES[i];
			points[i] = new Vec3(attacker.getX() + forwardX * distance, y, attacker.getZ() + forwardZ * distance);
		}
		return points;
	}
}
