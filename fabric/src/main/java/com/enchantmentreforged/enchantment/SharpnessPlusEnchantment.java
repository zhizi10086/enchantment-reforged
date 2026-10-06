package com.enchantmentreforged.enchantment;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.compat.SpearCompat;
import net.minecraft.enchantment.DamageEnchantment;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * 自定义"锋利"。
 *
 * <p>继承原版 {@link DamageEnchantment} 并使用 ALL 类型，于是：
 * <ul>
 *     <li>自动继承原版锋利的冲突关系（与亡灵杀手、节肢杀手互斥）与可附魔物品、代价曲线；</li>
 *     <li>伤害加成走原版 {@code EnchantmentHelper.getAttackDamage} 通道，不额外注入。</li>
 * </ul>
 */
public class SharpnessPlusEnchantment extends DamageEnchantment {
	public SharpnessPlusEnchantment(Enchantment.Rarity rarity, EquipmentSlot... slots) {
		super(rarity, DamageEnchantment.ALL_INDEX, slots);
	}

	@Override
	public int getMaxLevel() {
		return EnchantmentReforgedConfig.get().sharpnessMaxLevel;
	}

	/**
	 * 矛模组的矛也接受锋利Plus。
	 *
	 * <p>三叉戟保持原版限制（锋利不属于三叉戟的原版可附魔列表），所以这里只额外放行矛。
	 */
	@Override
	public boolean isAcceptableItem(ItemStack stack) {
		if (SpearCompat.isSpear(stack)) {
			return true;
		}
		return super.isAcceptableItem(stack);
	}

	@Override
	public float getAttackDamage(int level, EntityGroup group) {
		if (!EnchantmentReforgedConfig.get().enableCustomSharpness) {
			// 总开关关闭：自定义锋利不再提供伤害加成
			return 0.0F;
		}
		// 每级固定 +1.5，不区分生物类型
		return EnchantmentReforgedConfig.get().sharpnessDamagePerLevel * level;
	}
}
