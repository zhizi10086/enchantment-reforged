package com.enchantmentreforged.combat;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.registry.ModEnchantments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
 *     由服务端每 tick 推进（{@code TickEvent.ServerTickEvent}）；攻击者离线或目标找不到就结束整条链；</li>
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
		private final ServerLevel level;
		private final UUID attackerId;
		private final ItemStack weapon;
		private final Vec3 from;
		private final Set<Integer> hitIds;
		private final int segmentsLeft;
		private final float amount;
		private long readyTick;

		private Pending(ServerLevel level, UUID attackerId, ItemStack weapon, Vec3 from, Set<Integer> hitIds,
				int segmentsLeft, float amount, long readyTick) {
			this.level = level;
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
	 * @param attacker   攻击者（必须是玩家，电刀只对玩家生效）
	 * @param firstHit   本刀打中的目标（第一段的起点）
	 * @param weapon     攻击时主手的剑（决定等级，也用于魔剑/嗜血的等级判定）
	 * @param baseAmount 主命中的攻击侧金额（弹射基数的来源）
	 */
	public static void start(LivingEntity attacker, Entity firstHit, ItemStack weapon, float baseAmount) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableElectricBlade || !(attacker instanceof Player player) || firstHit == null
				|| weapon == null || weapon.isEmpty()) {
			return;
		}
		int level = EnchantmentEffects.levelOf(weapon, ModEnchantments.ELECTRIC_BLADE);
		if (level <= 0 || baseAmount <= 0.0F) {
			return;
		}
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		int segments = Math.min(level, MAX_SEGMENTS);
		Set<Integer> hitIds = new HashSet<>();
		hitIds.add(firstHit.getId());
		long delay = Math.max(0, config.electricBladeDelayTicks);
		PENDING.add(new Pending(serverLevel, player.getUUID(), weapon.copy(), firstHit.position(), hitIds,
				segments, baseAmount, serverLevel.getGameTime() + delay));
	}

	/** 服务端每 tick 推进一次（在 ServerTickEvent 的 END 阶段调用，与近战粒子队列并列） */
	public static void tick() {
		if (PENDING.isEmpty()) {
			return;
		}
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		long now;
		for (int i = PENDING.size() - 1; i >= 0; i--) {
			Pending pending = PENDING.get(i);
			now = pending.level.getGameTime();
			if (now < pending.readyTick) {
				continue;
			}
			PENDING.remove(i);
			if (!advance(pending, config)) {
				continue;
			}
			// 还有下一段：按延迟重新排队
			pending.readyTick = now + Math.max(0, config.electricBladeDelayTicks);
			PENDING.add(pending);
		}
	}

	/** 结算一段弹射；返回 false 表示这条链结束 */
	private static boolean advance(Pending pending, EnchantmentReforgedConfig config) {
		if (pending.level.getServer() == null) {
			return false;
		}
		ServerPlayer attacker = pending.level.getServer().getPlayerList().getPlayer(pending.attackerId);
		if (attacker == null || attacker.isRemoved() || attacker.isDeadOrDying()) {
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
		target.invulnerableTime = 0;
		DamageSource source = pending.level.damageSources().playerAttack(attacker);
		boolean hit = target.hurt(source, amount);
		if (hit) {
			// 弹射段只吃魔剑与嗜血（每个目标各一次；不触发斩杀/出其不意/再次电刀）
			EnchantmentEffects.applySpellbladeReport(attacker, pending.weapon, target, amount);
			EnchantmentEffects.applyLifestealReport(attacker, pending.weapon, amount);
		}
		spawnArcParticles(pending.level, pending.from, target);
		pending.hitIds.add(target.getId());
		return pending.segmentsLeft - 1 > 0;
	}

	/** 以上一段目标为圆心找半径内最近、且链上没打过的活体（排除攻击者本人） */
	private static LivingEntity findNextTarget(Pending pending) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		double radius = Math.max(0.5D, config.electricBladeRadius);
		AABB box = new AABB(pending.from, pending.from).inflate(radius);
		List<LivingEntity> candidates = pending.level.getEntitiesOfClass(LivingEntity.class, box,
				entity -> entity.isAlive() && !pending.hitIds.contains(entity.getId())
						&& !entity.getUUID().equals(pending.attackerId));
		LivingEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (LivingEntity candidate : candidates) {
			double distance = candidate.position().distanceToSqr(pending.from);
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest;
	}

	/** 连线电火花 + 命中点小爆发（服务端广播，附近玩家都看得到） */
	private static void spawnArcParticles(ServerLevel level, Vec3 from, LivingEntity target) {
		Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
		for (int step = 0; step <= LINK_STEPS; step++) {
			double t = (double) step / LINK_STEPS;
			double x = from.x + (to.x - from.x) * t;
			double y = from.y + (to.y - from.y) * t;
			double z = from.z + (to.z - from.z) * t;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 8, 0.25D, 0.25D, 0.25D, 0.05D);
	}

	/** 服务器停止时清空待推进的弹射 */
	public static void clearAll() {
		PENDING.clear();
	}
}
