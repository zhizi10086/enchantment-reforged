package com.enchantmentreforged.registry;

import com.enchantmentreforged.EnchantmentReforged;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

/**
 * 本模组的创造模式物品页：只放自家的附魔书（每个附魔一本满级书）。
 */
public final class ModItemGroups {
	public static final ItemGroup ENCHANTED_BOOKS = FabricItemGroup.builder()
			.icon(() -> bookFor(ModEnchantments.SHARPNESS_PLUS))
			.displayName(Text.translatable("itemGroup.enchantment_reforged.enchanted_books"))
			.entries((context, entries) -> {
				for (Enchantment enchantment : all()) {
					if (enchantment != null) {
						entries.add(bookFor(enchantment));
					}
				}
			})
			.build();

	private ModItemGroups() {
	}

	public static void register() {
		Registry.register(Registries.ITEM_GROUP, EnchantmentReforged.id("enchanted_books"), ENCHANTED_BOOKS);
	}

	/**
	 * 本模组全部附魔（含锋利Plus），按功能分组排序：
	 * 核心 → 近战 → 远程与箭矢 → 采集与辅助 → 生存与防护。
	 */
	public static List<Enchantment> all() {
		return Arrays.asList(
				ModEnchantments.SHARPNESS_PLUS,
				// 近战
				ModEnchantments.SPELLBLADE,
				ModEnchantments.BEHEADING,
				ModEnchantments.EXECUTION,
				ModEnchantments.LIFESTEAL,
				ModEnchantments.QUICK_STRIKE,
				ModEnchantments.SURPRISE,
				// 远程与箭矢
				ModEnchantments.ARROW_VELOCITY,
				ModEnchantments.QUICK_DRAW,
				ModEnchantments.INSTINCTIVE_RELEASE,
				ModEnchantments.PHANTOM_ARROW,
				ModEnchantments.ENDLESS_QUIVER,
				// 生存与治疗
				ModEnchantments.HEALTH_BOOST,
				ModEnchantments.LIFE_SHIELD,
				ModEnchantments.LIFE_MENDING,
				ModEnchantments.DEAD_MANS_HEART,
				ModEnchantments.SOUL_GRACE,
				ModEnchantments.CALAMITY,
				// 战斗状态
				ModEnchantments.DEATHS_BLESSING,
				ModEnchantments.REVENGE,
				// 防御与耐久
				ModEnchantments.STURDY,
				// 食物与移动
				ModEnchantments.QUICK_EAT,
				ModEnchantments.BIG_STOMACH,
				ModEnchantments.NIMBLE_STEPS,
				// 采集与经验
				ModEnchantments.AUTO_SMELT,
				ModEnchantments.SCHOLAR
		);
	}

	/** 生成一本该附魔的满级附魔书 */
	public static ItemStack bookFor(Enchantment enchantment) {
		ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
		if (enchantment != null) {
			EnchantedBookItem.addEnchantment(stack,
					new EnchantmentLevelEntry(enchantment, enchantment.getMaxLevel()));
		}
		return stack;
	}
}
