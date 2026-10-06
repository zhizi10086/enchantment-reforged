package com.enchantmentreforged.combat;

/**
 * 原版经验公式：把"等级"换算成"从 0 级攒到该等级所需的经验点数"。
 *
 * <p>原版每级所需经验分段：0~15 级每级 {@code 2L+7}、16~30 级 {@code 5L-38}、
 * 31 级以上 {@code 9L-158}，这里逐级累加。
 */
public final class ExperienceMath {
	private ExperienceMath() {
	}

	/** 从 0 级升到 {@code level} 级所需的总经验点数 */
	public static int totalPointsForLevel(int level) {
		int total = 0;
		for (int i = 0; i < level; i++) {
			total += pointsForNextLevel(i);
		}
		return total;
	}

	/** 从 {@code level} 级升到下一级所需的经验点数 */
	public static int pointsForNextLevel(int level) {
		if (level >= 31) {
			return 9 * level - 158;
		}
		if (level >= 16) {
			return 5 * level - 38;
		}
		return 2 * level + 7;
	}

	/** 反过来：这些经验点数相当于多少级（用于铁砧同时显示等级与点数） */
	public static int levelForPoints(int points) {
		int level = 0;
		int total = 0;
		while (level < 1000) {
			int next = pointsForNextLevel(level);
			if (total + next > points) {
				break;
			}
			total += next;
			level++;
		}
		return level;
	}

	/**
	 * 玩家当前"可支付"的经验点数：等级换算的总点数 + 当前等级内的进度点数。
	 *
	 * <p>扣费走的是 {@code addExperience(-cost)}，它会先把当前进度扣光、再逐级往下扣，
	 * 所以真正能付的上限就是"等级 + 进度"折算出来的点数。
	 *
	 * <p>不能直接用 {@code player.totalExperience}：它只是"累计获得"的计数，像
	 * {@code /xp … levels} 这类命令（内部调用 {@code setExperienceLevel}）只改等级、
	 * 不会同步它，于是"1000 级却付不起 55 点"。
	 */
	public static int spendablePoints(int level, float progress) {
		// 等级高到离谱时逐级累加会溢出 int，这里夹一个安全上限：
		// 任何合理成本（上限等级 100 = 5855 点）都远小于它，语义不受影响。
		int safeLevel = Math.min(Math.max(0, level), 10000);
		int total = totalPointsForLevel(safeLevel);
		float clamped = Math.max(0.0F, Math.min(1.0F, progress));
		return total + Math.round(clamped * pointsForNextLevel(safeLevel));
	}
}
