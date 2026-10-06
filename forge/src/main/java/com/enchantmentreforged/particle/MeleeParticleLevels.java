package com.enchantmentreforged.particle;

/**
 * 近战粒子的效果强度三档：简化 / 标准 / 大量。
 *
 * <p>每档给出"单帧爆发"与"余晖"两组参数：命中（普通/暴击）与横扫各自独立。
 * 数值只影响观感与带宽，不影响伤害。
 */
public enum MeleeParticleLevels {
	/** 简化：单帧一簇，最省带宽 */
	SIMPLE(8, 0.40D, 0.25D, 0, 0, 3, 3, 0.10D, 0.15D, 0, 0),
	/** 标准（默认）：一簇 + 2 tick 余晖 */
	STANDARD(12, 0.45D, 0.30D, 2, 4, 3, 4, 0.10D, 0.18D, 2, 4),
	/** 大量：更密更久 */
	HEAVY(16, 0.50D, 0.35D, 4, 6, 5, 4, 0.12D, 0.20D, 4, 6);

	/** 最大档位编号（用于配置夹取） */
	public static final int MAX = 2;
	/** 余晖最长 tick 数（队列按它决定一条喷发存活多久） */
	public static final int MAX_TRAIL_TICKS = 4;

	private final int hitCount;
	private final double hitSpread;
	private final double hitSpeed;
	private final int hitTrailTicks;
	private final int hitTrailCount;
	private final int sweepPoints;
	private final int sweepCount;
	private final double sweepSpread;
	private final double sweepSpeed;
	private final int sweepTrailTicks;
	private final int sweepTrailCount;

	MeleeParticleLevels(int hitCount, double hitSpread, double hitSpeed, int hitTrailTicks, int hitTrailCount,
			int sweepPoints, int sweepCount, double sweepSpread, double sweepSpeed,
			int sweepTrailTicks, int sweepTrailCount) {
		this.hitCount = hitCount;
		this.hitSpread = hitSpread;
		this.hitSpeed = hitSpeed;
		this.hitTrailTicks = hitTrailTicks;
		this.hitTrailCount = hitTrailCount;
		this.sweepPoints = sweepPoints;
		this.sweepCount = sweepCount;
		this.sweepSpread = sweepSpread;
		this.sweepSpeed = sweepSpeed;
		this.sweepTrailTicks = sweepTrailTicks;
		this.sweepTrailCount = sweepTrailCount;
	}

	/** 命中单帧数量 */
	public int hitCount() {
		return hitCount;
	}

	public double hitSpread() {
		return hitSpread;
	}

	public double hitSpeed() {
		return hitSpeed;
	}

	public int hitTrailCount() {
		return hitTrailCount;
	}

	/** 横扫使用几个采样点 */
	public int sweepPoints() {
		return sweepPoints;
	}

	/** 横扫每个采样点的单帧数量 */
	public int sweepCount() {
		return sweepCount;
	}

	public double sweepSpread() {
		return sweepSpread;
	}

	public double sweepSpeed() {
		return sweepSpeed;
	}

	public int sweepTrailCount() {
		return sweepTrailCount;
	}

	/** 第 age 个喷发 tick（0 = 单帧爆发）时，该档位要发多少颗；0 表示这一 tick 不发 */
	public int countForTick(boolean sweep, int age) {
		if (age <= 0) {
			return sweep ? sweepCount : hitCount;
		}
		int trailTicks = sweep ? sweepTrailTicks : hitTrailTicks;
		return age <= trailTicks ? (sweep ? sweepTrailCount : hitTrailCount) : 0;
	}

	/** 按编号取档位；越界时回退到标准档 */
	public static MeleeParticleLevels byIndex(int index) {
		return switch (index) {
			case 0 -> SIMPLE;
			case 2 -> HEAVY;
			default -> STANDARD;
		};
	}

	/** 配置页显示用的语言键（简化/标准/大量） */
	public static String nameKey(int index) {
		return "text.enchantment_reforged.melee_effect." + Math.max(0, Math.min(MAX, index));
	}
}
