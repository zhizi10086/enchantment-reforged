package com.enchantmentreforged.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 灵魂加护的冷却效果。
 *
 * <p>只是"冷却还剩多久"的可视化：真正判定免死的是服务端的计时表，
 * 所以喝牛奶或 {@code /effect clear} 把图标清掉也绕不过冷却（图标会在 1 秒内自动回来）。
 */
public class SoulGraceStatusEffect extends MobEffect {
	/** 与生命护盾同色（金），图标也复用生命护盾那张贴图 */
	public static final int COLOR = 0xFFC64B;

	public SoulGraceStatusEffect() {
		super(MobEffectCategory.BENEFICIAL, COLOR);
	}
}
