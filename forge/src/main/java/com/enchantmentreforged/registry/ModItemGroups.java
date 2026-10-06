package com.enchantmentreforged.registry;

import com.enchantmentreforged.EnchantmentReforged;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

import java.util.Arrays;
import java.util.List;

/**
 * 本模组的创造模式物品页：只放自家的附魔书（每个附魔一本满级书）。
 */
public final class ModItemGroups {
	/** 在注册事件里回填（模组构造器阶段创造模式物品页注册表已锁定） */
	public static CreativeModeTab ENCHANTED_BOOKS;

	private ModItemGroups() {
	}

	public static void register(IEventBus bus) {
		bus.addListener(ModItemGroups::onRegister);
	}

	private static void onRegister(RegisterEvent event) {
		if (!Registries.CREATIVE_MODE_TAB.equals(event.getRegistryKey())) {
			return;
		}
		event.register(Registries.CREATIVE_MODE_TAB, EnchantmentReforged.id("enchanted_books"),
				() -> ENCHANTED_BOOKS = CreativeModeTab.builder()
						.icon(() -> bookFor(ModEnchantments.SHARPNESS_PLUS))
						.title(Component.translatable("itemGroup.enchantment_reforged.enchanted_books"))
						.displayItems((params, output) -> {
							for (Enchantment enchantment : all()) {
								if (enchantment != null) {
									output.accept(bookFor(enchantment));
								}
							}
						})
						.build());
	}

	/**
	 * 本模组全部附魔（含锋利Plus），按功能分组排序：
	 * 锋利Plus → 近战 → 远程与箭矢 → 生存与治疗 → 战斗状态 → 防御与耐久 → 食物与移动 → 采集与经验。
	 */
	public static List<Enchantment> all() {
		return Arrays.asList(
				ModEnchantments.SHARPNESS_PLUS,
				ModEnchantments.SPELLBLADE,
				ModEnchantments.BEHEADING,
				ModEnchantments.EXECUTION,
				ModEnchantments.LIFESTEAL,
				ModEnchantments.QUICK_STRIKE,
				ModEnchantments.SURPRISE,
				ModEnchantments.ARROW_VELOCITY,
				ModEnchantments.QUICK_DRAW,
				ModEnchantments.INSTINCTIVE_RELEASE,
				ModEnchantments.PHANTOM_ARROW,
				ModEnchantments.ENDLESS_QUIVER,
				ModEnchantments.HEALTH_BOOST,
				ModEnchantments.LIFE_SHIELD,
				ModEnchantments.LIFE_MENDING,
				ModEnchantments.DEAD_MANS_HEART,
				ModEnchantments.SOUL_GRACE,
				ModEnchantments.CALAMITY,
				ModEnchantments.DEATHS_BLESSING,
				ModEnchantments.REVENGE,
				ModEnchantments.STURDY,
				ModEnchantments.QUICK_EAT,
				ModEnchantments.BIG_STOMACH,
				ModEnchantments.NIMBLE_STEPS,
				ModEnchantments.AUTO_SMELT,
				ModEnchantments.SCHOLAR
		);
	}

	/** 生成一本该附魔的满级附魔书 */
	public static ItemStack bookFor(Enchantment enchantment) {
		ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
		if (enchantment != null) {
			EnchantedBookItem.addEnchantment(stack,
					new EnchantmentInstance(enchantment, enchantment.getMaxLevel()));
		}
		return stack;
	}
}
