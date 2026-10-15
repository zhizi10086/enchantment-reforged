package com.enchantmentreforged.combat;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.registry.ModEnchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 电刀（1.3.8）：近战主命中之后的链式弹射。
 *
 * <p>规则（已定稿）：
 * <ul>
 *     <li>段数 = 附魔等级（1~4），最多命中 1 + 段数 个目标；</li>
 *     <li>基数 = 主命中的<b>攻击侧金额</b>（原版伤害 × 力量 × 复仇 ×（1 + 藏剑加成）），
 *     逐段再乘 {@code electric_blade_damage_ratio}（默认 0.8）；不含护甲减免与目标的受伤倍率；</li>
 *     <li>每段先清目标无敌帧，再用原版 {@code playerAttack} 打出（归属与死亡信息正确）；</li>
 *     <li>每段各结算一次魔剑与嗜血，但<b>不</b>触发斩杀、不出其不意、也不会再触发一次电刀；
 *     弹射目标是链上没打过的活体，所以同一目标不会被算两次；</li>
 *     <li>段与段之间延迟 {@code electric_blade_delay_ticks}（默认 2 tick = 0.1 秒），
 *     由服务端每 tick 推进（{@code END_SERVER_TICK}）；攻击者离线或目标找不到就结束整条链；</li>
 *     <li>表现：从上一段目标到本段目标沿线生成电火花（{@code ELECTRIC_SPARK}）+ 命中点小爆发。</li>
 * </ul>
 */
public final class ElectricArc {
	/** 单条链的最大段数（防御性上限：配置写成极端值时也不会无限排） */
	private static final int MAX_SEGMENTS = 32;
	/** 连线粒子的采样步数 */
	private static final int LINK_STEPS = 12;

	/** 待推进的弹射段 */
	private static final List<Pending> PENDING = new ArrayList<>();

	private ElectricArc() {
	}

	/** 一条等待推进的弹射：到 {@code readyTick} 时结算一段，然后按延迟排下一段 */
	private static final class Pending {
		private final ServerWorld world;
		private final UUID attackerId;
		private final ItemStack weapon;
		private final Set<Integer> hitIds;
		private final float amount;
		/** 本段的"起点"（搜索圆心与连线起点）：每段结算后更新为上一段目标的命中点 */
		private Vec3d from;
		/** 剩余段数：每段结算后递减，减到 0 就结束整条链 */
		private int segmentsLeft;
		private long readyTick;

		private Pending(ServerWorld world, UUID attackerId, ItemStack weapon, Vec3d from, Set<Integer> hitIds,
				int segmentsLeft, float amount, long readyTick) {
			this.world = world;
			this.attackerId = attackerId;
			this.weapon = weapon;
			this.from = from;
			this.hitIds = hitIds;
			this.segmentsLeft = segmentsLeft;
			this.amount = amount;
			this.readyTick = readyTick;
		}
	}

	/**
	 * 启动一条链（玩家左键近战主命中落地后调用）。
	 *
	 * @param attacker    攻击者（必须是玩家，电刀只对玩家生效）
	 * @param firstHit    本刀打中的目标（第一段的起点）
	 * @param weapon      攻击时主手的剑（决定等级，也用于魔剑/嗜血的等级判定）
	 * @param baseAmount  主命中的攻击侧金额（弹射基数的来源）
	 * @param excludeIds  本次挥砍已经伤害过的实体 id（主命中 + 横扫受害者）——
	 *                    它们不该被本刀的电刀再打一次；可为 null
	 */
	public static void start(LivingEntity attacker, Entity firstHit, ItemStack weapon, float baseAmount,
			Set<Integer> excludeIds) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableElectricBlade || !(attacker instanceof PlayerEntity player) || firstHit == null
				|| weapon == null || weapon.isEmpty()) {
			return;
		}
		int level = EnchantmentEffects.levelOf(weapon, ModEnchantments.ELECTRIC_BLADE);
		if (level <= 0 || baseAmount <= 0.0F) {
			return;
		}
		if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
			return;
		}
		Set<Integer> hitIds = new HashSet<>();
		if (excludeIds != null) {
			hitIds.addAll(excludeIds);
		}
		hitIds.add(firstHit.getId());
		int segments = Math.min(level, MAX_SEGMENTS);
		long delay = Math.max(0, config.electricBladeDelayTicks);
		PENDING.add(new Pending(serverWorld, player.getUuid(), weapon.copy(), firstHit.getPos(), hitIds,
				segments, baseAmount, serverWorld.getTime() + delay));
	}

	/** 服务端每 tick 推进一次（在 END_SERVER_TICK 里调用，与近战粒子队列并列） */
	public static void tick() {
		if (PENDING.isEmpty()) {
			return;
		}
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		long now = 0L;
		for (int i = PENDING.size() - 1; i >= 0; i--) {
			Pending pending = PENDING.get(i);
			now = pending.world.getTime();
			if (now < pending.readyTick) {
				continue;
			}
			PENDING.remove(i);
			if (!advance(pending, config)) {
				continue;
			}
			// 还有下一段：按延迟重新排队
			long delay = Math.max(0, config.electricBladeDelayTicks);
			pending.readyTick = now + delay;
			PENDING.add(pending);
		}
	}

	/** 结算一段弹射；返回 false 表示这条链结束 */
	private static boolean advance(Pending pending, EnchantmentReforgedConfig config) {
		if (pending.world.getServer() == null) {
			return false;
		}
		ServerPlayerEntity attacker = pending.world.getServer().getPlayerManager().getPlayer(pending.attackerId);
		if (attacker == null || attacker.isRemoved() || attacker.isDead()) {
			// 攻击者离线/死亡：整条链结束（否则归属与武器等级都不再可靠）
			return false;
		}
		LivingEntity target = findNextTarget(pending);
		if (target == null) {
			return false;
		}
		// 每段基数：上一段金额 × 倍率（默认 0.8）
		float ratio = Math.max(0.0F, config.electricBladeDamageRatio);
		float amount = pending.amount * ratio;
		if (amount <= 0.0F) {
			return false;
		}
		// 清无敌帧：否则刚被主命中打过的目标会把这一段完全吞掉
		target.timeUntilRegen = 0;
		DamageSource source = pending.world.getDamageSources().playerAttack(attacker);
		boolean hit = target.damage(source, amount);
		if (hit) {
			// 弹射段只吃魔剑与嗜血（每个目标各一次；不触发斩杀/出其不意/再次电刀）
			EnchantmentEffects.applySpellbladeReport(attacker, pending.weapon, target, amount);
			EnchantmentEffects.applyLifestealReport(attacker, pending.weapon, amount);
		}
		// 本段的命中点：既是这次的连线终点，也是下一段的搜索圆心与起点
		Vec3d hitPoint = target.getPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
		spawnArcParticles(pending.world, pending.from, hitPoint);
		pending.from = hitPoint;
		pending.hitIds.add(target.getId());
		// 段数递减：1~4 级严格对应 1~4 段
		pending.segmentsLeft--;
		return pending.segmentsLeft > 0;
	}

	/** 以上一段目标为圆心找半径内最近、且链上没打过的活体（排除攻击者本人） */
	private static LivingEntity findNextTarget(Pending pending) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		double radius = Math.max(0.5D, config.electricBladeRadius);
		Box box = new Box(pending.from, pending.from).expand(radius);
		List<LivingEntity> candidates = pending.world.getEntitiesByClass(LivingEntity.class, box,
				entity -> entity.isAlive() && !pending.hitIds.contains(entity.getId())
						&& !entity.getUuid().equals(pending.attackerId));
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : candidates) {
			double distance = candidate.getPos().squaredDistanceTo(pending.from);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest;
	}

	/** 连线电火花 + 命中点小爆发（服务端广播，附近玩家都看得到） */
	private static void spawnArcParticles(ServerWorld world, Vec3d from, Vec3d to) {
		for (int step = 0; step <= LINK_STEPS; step++) {
			double t = (double) step / LINK_STEPS;
			double x = from.x + (to.x - from.x) * t;
			double y = from.y + (to.y - from.y) * t;
			double z = from.z + (to.z - from.z) * t;
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
		}
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 8, 0.25D, 0.25D, 0.25D, 0.05D);
	}

	/** 服务器停止时清空待推进的弹射 */
	public static void clearAll() {
		PENDING.clear();
	}
}
