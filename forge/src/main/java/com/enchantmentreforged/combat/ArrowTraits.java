package com.enchantmentreforged.combat;

import com.enchantmentreforged.EnchantmentReforged;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 箭矢上的两类"特性"状态（1.3.7）：
 *
 * <ul>
 *     <li><b>轻盈</b>：每支箭的等级。服务端在箭生成时写表并通过 S2C 包同步给客户端
 *     （重力在 {@code AbstractArrow#tick} 里两端都要算，不能只改服务端）；</li>
 *     <li><b>死亡之眼</b>：这支箭的归属者（射手）与"是否命中过生物 / 是否已结算过落空"标记，
 *     以及每位射手的层数与连续落空计数。</li>
 * </ul>
 *
 * <p>都按实体网络 id 索引（箭是短命实体，服务端 id 单调分配不重用），实体移除或停服时清理。
 */
public final class ArrowTraits {
	/** 服务端：实体 id → 轻盈等级 */
	private static final Map<Integer, Integer> SERVER_LIGHTNESS = new ConcurrentHashMap<>();
	/** 客户端：实体 id → 轻盈等级（由 S2C 包填充） */
	private static final Map<Integer, Integer> CLIENT_LIGHTNESS = new ConcurrentHashMap<>();
	/** 服务端：实体 id → 射手的 UUID（死亡之眼） */
	private static final Map<Integer, UUID> DEATH_EYE_OWNER = new ConcurrentHashMap<>();
	/** 服务端：本箭是否命中过"会受伤的生物" */
	private static final Set<Integer> DEATH_EYE_HIT = ConcurrentHashMap.newKeySet();
	/** 服务端：本箭的落空是否已经结算过（防重复） */
	private static final Set<Integer> DEATH_EYE_SETTLED = ConcurrentHashMap.newKeySet();
	/** 服务端：射手 UUID → [层数, 连续落空次数] */
	private static final Map<UUID, int[]> DEATH_EYE_STATE = new ConcurrentHashMap<>();
	/** 层数被清空时的节流：UUID → 上次打印的 tick */
	private static final Map<UUID, Long> CLEAR_LOG_STAMP = new ConcurrentHashMap<>();
	/** 清空日志的节流间隔（tick） */
	private static final long CLEAR_LOG_INTERVAL_TICKS = 100L;

	private ArrowTraits() {
	}

	// ==================== 轻盈 ====================

	/** 服务端：登记某支箭的轻盈等级（等级 ≤ 0 表示不介入） */
	public static void setServerLightness(AbstractArrow arrow, int level) {
		if (arrow == null) {
			return;
		}
		if (level <= 0) {
			SERVER_LIGHTNESS.remove(arrow.getId());
		} else {
			SERVER_LIGHTNESS.put(arrow.getId(), level);
		}
	}

	/** 服务端：某支箭的轻盈等级（未登记 = 0） */
	public static int serverLightness(AbstractArrow arrow) {
		return arrow == null ? 0 : SERVER_LIGHTNESS.getOrDefault(arrow.getId(), 0);
	}

	/** 客户端：收到同步包后登记等级 */
	public static void setClientLightness(int entityId, int level) {
		if (level <= 0) {
			CLIENT_LIGHTNESS.remove(entityId);
		} else {
			CLIENT_LIGHTNESS.put(entityId, level);
		}
	}

	/**
	 * 这支箭当前的重力倍率（1.0 = 原版）。
	 *
	 * <p>两端各读自己的表；等级未登记（普通箭、生物箭、客户端还没收到包）时返回 1.0，保持原版。
	 * 总减重夹到 0.9 以内，避免配置调大后出现反向重力。
	 */
	public static double gravityFactor(AbstractArrow arrow) {
		if (arrow == null) {
			return 1.0D;
		}
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableLightness) {
			return 1.0D;
		}
		Integer level = arrow.level().isClientSide()
				? CLIENT_LIGHTNESS.get(arrow.getId())
				: SERVER_LIGHTNESS.get(arrow.getId());
		if (level == null || level <= 0) {
			return 1.0D;
		}
		double reduction = Math.min(0.9D, config.lightnessGravityReductionPerLevel * level);
		return 1.0D - reduction;
	}

	/** 服务端：把轻盈等级复制给"额外箭"（多重射击 / 幻影箭） */
	public static void copyLightness(AbstractArrow from, AbstractArrow to) {
		int level = serverLightness(from);
		if (level > 0) {
			setServerLightness(to, level);
		}
	}

	// ==================== 死亡之眼 ====================

	/** 服务端：登记某支箭属于"死亡之眼"（射出时调用；只有装了该附魔的弓弩才登记） */
	public static void markDeathEye(AbstractArrow arrow, LivingEntity shooter) {
		if (arrow == null || shooter == null) {
			return;
		}
		DEATH_EYE_OWNER.put(arrow.getId(), shooter.getUUID());
		DEATH_EYE_HIT.remove(arrow.getId());
		DEATH_EYE_SETTLED.remove(arrow.getId());
	}

	/** 服务端：把"死亡之眼"归属复制给额外箭（状态从"未命中、未结算"重新开始） */
	public static void copyDeathEye(AbstractArrow from, AbstractArrow to) {
		UUID owner = from == null ? null : DEATH_EYE_OWNER.get(from.getId());
		if (owner == null) {
			return;
		}
		DEATH_EYE_OWNER.put(to.getId(), owner);
		DEATH_EYE_HIT.remove(to.getId());
		DEATH_EYE_SETTLED.remove(to.getId());
	}

	/** 服务端：这支箭是否参与死亡之眼统计 */
	public static boolean hasDeathEye(AbstractArrow arrow) {
		return arrow != null && DEATH_EYE_OWNER.containsKey(arrow.getId());
	}

	/** 本次命中的伤害倍率（1 + 当前层数 × 每层加成；未参与或开关关闭时为 1.0） */
	public static float deathEyeMultiplier(AbstractArrow arrow) {
		if (arrow == null) {
			return 1.0F;
		}
		UUID owner = DEATH_EYE_OWNER.get(arrow.getId());
		if (owner == null) {
			return 1.0F;
		}
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableDeathEye) {
			return 1.0F;
		}
		int max = Math.max(0, config.deathEyeMaxStacks);
		int stacks = Math.min(state(owner)[0], max);
		return 1.0F + config.deathEyeDamagePerStack * stacks;
	}

	/** 服务端：命中"会受伤的生物"后调用——打上已命中标记、层数 +1（封顶） */
	public static void recordHit(AbstractArrow arrow) {
		if (arrow == null) {
			return;
		}
		UUID owner = DEATH_EYE_OWNER.get(arrow.getId());
		if (owner == null) {
			return;
		}
		DEATH_EYE_HIT.add(arrow.getId());
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		int max = Math.max(0, config.deathEyeMaxStacks);
		int[] state = state(owner);
		state[0] = Math.min(state[0] + 1, max);
	}

	/** 服务端：这支箭是否命中过生物（命中过就永不结算落空——穿透后落地不再掷概率清空） */
	public static boolean hitLiving(int entityId) {
		return DEATH_EYE_HIT.contains(entityId);
	}

	/** 服务端：标记"落空已结算"，首次返回 true（用于防重复结算） */
	public static boolean markSettled(int entityId) {
		return DEATH_EYE_SETTLED.add(entityId);
	}

	/**
	 * 服务端：整箭落空时结算——**无条件清空层数**（1.3.7 起固定 100%，不再掷概率）。
	 *
	 * <p>只有"这支箭整段飞行没命中过任何生物、且已插地 / 被移除"才会被调用
	 * （穿透命中过生物后落地不结算）；层数原本就是 0 时不打日志。
	 */
	public static void recordMiss(AbstractArrow arrow) {
		if (arrow == null) {
			return;
		}
		UUID owner = DEATH_EYE_OWNER.get(arrow.getId());
		if (owner == null) {
			return;
		}
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableDeathEye) {
			return;
		}
		int[] state = state(owner);
		int before = state[0];
		state[0] = 0;
		if (before > 0) {
			logClear(arrow, owner, before);
		}
	}

	/** 服务端：某位玩家当前的死亡之眼层数 */
	public static int stacks(UUID playerId) {
		return playerId == null ? 0 : state(playerId)[0];
	}

	/** 服务端：清空某位玩家的层数（死亡 / 重生 / 断线） */
	public static void clearPlayer(UUID playerId) {
		if (playerId != null) {
			DEATH_EYE_STATE.remove(playerId);
			CLEAR_LOG_STAMP.remove(playerId);
		}
	}

	/** 服务端停止 / 客户端断开：整表清空 */
	public static void clearAll() {
		SERVER_LIGHTNESS.clear();
		CLIENT_LIGHTNESS.clear();
		DEATH_EYE_OWNER.clear();
		DEATH_EYE_HIT.clear();
		DEATH_EYE_SETTLED.clear();
		DEATH_EYE_STATE.clear();
		CLEAR_LOG_STAMP.clear();
	}

	/** 箭被移除时清掉它的三张按 id 的表 */
	public static void forget(int entityId) {
		SERVER_LIGHTNESS.remove(entityId);
		CLIENT_LIGHTNESS.remove(entityId);
		DEATH_EYE_OWNER.remove(entityId);
		DEATH_EYE_HIT.remove(entityId);
		DEATH_EYE_SETTLED.remove(entityId);
	}

	private static int[] state(UUID owner) {
		// 单元素数组：只存层数（1.3.7 起"落空必清空"，连续落空计数已无判定用途）
		return DEATH_EYE_STATE.computeIfAbsent(owner, key -> new int[1]);
	}

	private static void logClear(AbstractArrow arrow, UUID owner, int before) {
		long tick = arrow.level().getGameTime();
		Long last = CLEAR_LOG_STAMP.get(owner);
		if (last != null && tick - last < CLEAR_LOG_INTERVAL_TICKS) {
			return;
		}
		CLEAR_LOG_STAMP.put(owner, tick);
		String name = owner.toString();
		if (arrow.level().getServer() != null) {
			ServerPlayer player = arrow.level().getServer().getPlayerList().getPlayer(owner);
			if (player != null) {
				name = player.getGameProfile().getName();
			}
		}
		EnchantmentReforged.LOGGER.info("[ER] 死亡之眼（{}）：整箭落空 → 清空（原 {} 层）", name, before);
	}
}
