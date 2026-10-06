package com.enchantmentreforged.config;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

/**
 * 箭矢粒子档位表：配置项 {@code arrow_particle} 的 0~12 档。
 *
 * <p>0 表示完全不介入（保留原版暴击星），1 表示屏蔽原版暴击星且不生成任何轨迹粒子，
 * 2~12 使用对应的原版粒子作为飞行轨迹。所需粒子全部是原版粒子，客户端本地生成。
 */
public enum ArrowParticleStyle {
	/** 0 原版默认 */
	VANILLA(0, null),
	/** 1 无粒子 */
	NONE(1, null),
	/** 2 火焰 */
	FLAME(2, ParticleTypes.FLAME),
	/** 3 灵魂火 */
	SOUL_FIRE_FLAME(3, ParticleTypes.SOUL_FIRE_FLAME),
	/** 4 末地烛 */
	END_ROD(4, ParticleTypes.END_ROD),
	/** 5 电火花 */
	ELECTRIC_SPARK(5, ParticleTypes.ELECTRIC_SPARK),
	/** 6 附魔符文 */
	ENCHANT(6, ParticleTypes.ENCHANT),
	/** 7 龙息 */
	DRAGON_BREATH(7, ParticleTypes.DRAGON_BREATH),
	/** 8 图腾 */
	TOTEM_OF_UNDYING(8, ParticleTypes.TOTEM_OF_UNDYING),
	/** 9 灵魂 */
	SOUL(9, ParticleTypes.SOUL),
	/** 10 小火焰 */
	SMALL_FLAME(10, ParticleTypes.SMALL_FLAME),
	/** 11 附魔命中 */
	ENCHANTED_HIT(11, ParticleTypes.ENCHANTED_HIT),
	/** 12 烟花 */
	FIREWORK(12, ParticleTypes.FIREWORK);

	/** 档位数量（0~12 共 13 档） */
	public static final int COUNT = 13;
	/** 缓存枚举数组：byIndex 在"每支箭每 tick"的热路径上，避免每次都克隆 values() */
	private static final ArrowParticleStyle[] VALUES = values();

	private final int index;
	private final ParticleEffect particle;

	ArrowParticleStyle(int index, ParticleEffect particle) {
		this.index = index;
		this.particle = particle;
	}

	public int index() {
		return index;
	}

	/** 该档位对应的原版粒子；0（原版）与 1（无粒子）为 null */
	public ParticleEffect particle() {
		return particle;
	}

	/**
	 * 是否屏蔽该箭自带的原版粒子：暴击星、药水箭颜色漩涡、光灵箭闪粒、插地消散爆发
	 * （1~12 都屏蔽；只影响粒子，不影响暴击伤害与药水效果）。
	 */
	public boolean suppressesVanillaParticles() {
		return index >= 1;
	}

	/** "这支箭没带射手档位"的标记值（生物 / 发射器 / 指令生成 / 射手档位尚未上报）：一律按原版不介入 */
	public static final int UNSET = -1;

	/** 取档位名对应的语言键（0~12） */
	public static String nameKey(int index) {
		return "text.enchantment_reforged.particle." + index;
	}

	/** 按编号取档位；越界或未设置时回退到"原版默认" */
	public static ArrowParticleStyle byIndex(int index) {
		return index >= 0 && index < VALUES.length ? VALUES[index] : VANILLA;
	}
}
