package com.enchantmentreforged.enchantment;

import com.enchantmentreforged.compat.SpearCompat;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.registry.ModEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 通用自定义附魔。
 *
 * <p>等级上限、稀有度、可附魔物品、是否宝藏、是否诅咒都在构造时给定；
 * 具体效果由 mixin / 事件读取附魔等级后实现，这个类只承载注册数据与代价曲线
 * （代价曲线沿用 {@link Enchantment} 的默认实现，因此能正常出现在附魔台）。
 */
public class SimpleEnchantment extends Enchantment {
	private final int maxLevel;
	private final boolean treasure;
	private final boolean cursed;

	public SimpleEnchantment(Rarity rarity, EnchantmentCategory target, int maxLevel,
			boolean treasure, boolean cursed, EquipmentSlot... slots) {
		super(rarity, target, slots);
		this.maxLevel = maxLevel;
		this.treasure = treasure;
		this.cursed = cursed;
	}

	@Override
	public int getMaxLevel() {
		return this.maxLevel;
	}

	@Override
	public boolean isTreasureOnly() {
		return this.treasure;
	}

	@Override
	public boolean isCurse() {
		return this.cursed;
	}

	/**
	 * 武器兼容：魔剑、嗜血、迅捷打击、出其不意允许附在三叉戟与矛模组的矛上；
	 * 疾矢、幻影箭、无尽箭袋允许附在弩上（受"弩兼容弓附魔"开关控制）。
	 *
	 * <p>锋利Plus 与斩首保持原版限制（不能附三叉戟）。铁砧、命令与创造模式物品
	 * 都会走这个判定。
	 */
	@Override
	public boolean canEnchant(ItemStack stack) {
		if (this.isTridentCompatible() && (stack.is(Items.TRIDENT) || SpearCompat.isSpear(stack))) {
			return true;
		}
		if (this.isCrossbowCompatible() && stack.is(Items.CROSSBOW)
				&& EnchantmentReforgedConfig.get().enableCrossbowCompat) {
			return true;
		}
		return super.canEnchant(stack);
	}

	/** 兼容清单只写在这里，调整时改这一处即可 */
	private boolean isTridentCompatible() {
		return this == ModEnchantments.SPELLBLADE
				|| this == ModEnchantments.LIFESTEAL
				|| this == ModEnchantments.QUICK_STRIKE
				|| this == ModEnchantments.SURPRISE
				|| this == ModEnchantments.EXECUTION;
	}

	/** 允许附到弩上的本模组弓类附魔 */
	private boolean isCrossbowCompatible() {
		return this == ModEnchantments.ARROW_VELOCITY
				|| this == ModEnchantments.PHANTOM_ARROW
				|| this == ModEnchantments.ENDLESS_QUIVER;
	}
}
