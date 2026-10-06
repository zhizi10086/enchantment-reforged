package com.enchantmentreforged.registry;

import com.enchantmentreforged.EnchantmentReforged;
import com.enchantmentreforged.enchantment.SharpnessPlusEnchantment;
import com.enchantmentreforged.enchantment.SimpleEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 自定义附魔注册表。
 *
 * <p>全部新增条目，不改动、不覆盖任何原版附魔。
 * 等级上限按需求写死；开关与每级数值走 {@code EnchantmentReforgedConfig}。
 *
 * <p>Forge 版必须走 {@link RegisterEvent}（模组构造器阶段原版注册表已锁定）。
 */
public final class ModEnchantments {
	public static SharpnessPlusEnchantment SHARPNESS_PLUS;

	public static SimpleEnchantment SPELLBLADE;
	public static SimpleEnchantment BEHEADING;
	public static SimpleEnchantment AUTO_SMELT;
	public static SimpleEnchantment ARROW_VELOCITY;
	public static SimpleEnchantment QUICK_DRAW;
	public static SimpleEnchantment INSTINCTIVE_RELEASE;
	public static SimpleEnchantment LIFE_SHIELD;
	public static SimpleEnchantment HEALTH_BOOST;
	public static SimpleEnchantment LIFESTEAL;
	public static SimpleEnchantment SCHOLAR;
	public static SimpleEnchantment CALAMITY;
	public static SimpleEnchantment DEATHS_BLESSING;
	public static SimpleEnchantment QUICK_STRIKE;
	public static SimpleEnchantment LIFE_MENDING;
	public static SimpleEnchantment STURDY;
	public static SimpleEnchantment SURPRISE;
	public static SimpleEnchantment PHANTOM_ARROW;
	public static SimpleEnchantment ENDLESS_QUIVER;
	public static SimpleEnchantment EXECUTION;
	public static SimpleEnchantment QUICK_EAT;
	public static SimpleEnchantment BIG_STOMACH;
	public static SimpleEnchantment DEAD_MANS_HEART;
	public static SimpleEnchantment REVENGE;
	public static SimpleEnchantment SOUL_GRACE;
	public static SimpleEnchantment NIMBLE_STEPS;

	/** 注册事件期间的临时引用，仅用于 register 辅助方法 */
	private static RegisterEvent pendingEvent;

	private ModEnchantments() {
	}

	public static void register(IEventBus bus) {
		bus.addListener(ModEnchantments::onRegister);
	}

	private static void onRegister(RegisterEvent event) {
		if (!ForgeRegistries.Keys.ENCHANTMENTS.equals(event.getRegistryKey())) {
			return;
		}
		pendingEvent = event;
		try {
			registerAll();
		} finally {
			pendingEvent = null;
		}
	}

	private static void registerAll() {
		SHARPNESS_PLUS = register("sharpness_plus",
				new SharpnessPlusEnchantment(Enchantment.Rarity.COMMON, EquipmentSlot.MAINHAND));

		// 魔剑：每级 +8% 额外魔法伤害，剑斧
		SPELLBLADE = register("spellblade", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON, 5, false, false, EquipmentSlot.MAINHAND));
		// 斩首：每级 +10% 头颅掉落几率，剑斧
		BEHEADING = register("beheading", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON, 3, false, false, EquipmentSlot.MAINHAND));
		// 自动熔炼：烧炼挖掘掉落，镐类，与精准采集互斥
		AUTO_SMELT = register("auto_smelt", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.DIGGER, 1, false, false, EquipmentSlot.MAINHAND));
		// 疾矢：每级 +10% 箭矢速度
		ARROW_VELOCITY = register("arrow_velocity", new SimpleEnchantment(
				Enchantment.Rarity.COMMON, EnchantmentCategory.BOW, 5, false, false, EquipmentSlot.MAINHAND));
		// 速射：每级 -0.1 秒最大拉弓时间
		QUICK_DRAW = register("quick_draw", new SimpleEnchantment(
				Enchantment.Rarity.UNCOMMON, EnchantmentCategory.BOW, 5, false, false, EquipmentSlot.MAINHAND));
		// 本能释放：拉满自动射出并重新蓄力（宝藏）
		INSTINCTIVE_RELEASE = register("instinctive_release", new SimpleEnchantment(
				Enchantment.Rarity.VERY_RARE, EnchantmentCategory.BOW, 1, true, false, EquipmentSlot.MAINHAND));
		// 生命护盾：回血累积伤害吸收，胸甲
		LIFE_SHIELD = register("life_shield", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR_CHEST, 5, false, false, EquipmentSlot.CHEST));
		// 生命提升：每级 +1 最大生命，4 件盔甲可叠加
		HEALTH_BOOST = register("health_boost", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR, 5, false, false,
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET));
		// 嗜血：每级 +8% 吸血，剑斧
		LIFESTEAL = register("lifesteal", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON, 3, false, false, EquipmentSlot.MAINHAND));
		// 经验学者：每级 +10% 经验球经验，头盔
		SCHOLAR = register("scholar", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR_HEAD, 3, false, false, EquipmentSlot.HEAD));
		// 恶咒：当前生命压到 1，胸甲，诅咒，可进附魔台
		CALAMITY = register("calamity", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR_CHEST, 1, false, true, EquipmentSlot.CHEST));
		// 死神祝福：受伤更高、越残血输出越高、受到的伤害越低，胸甲
		DEATHS_BLESSING = register("deaths_blessing", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR_CHEST, 1, false, false, EquipmentSlot.CHEST));
		// 迅捷打击：每级 +10% 攻击速度，剑斧
		QUICK_STRIKE = register("quick_strike", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON, 5, false, false, EquipmentSlot.MAINHAND));
		// 生命修补：治疗时按治疗量修耐久并阻断这次治疗，任何有耐久的物品，诅咒
		LIFE_MENDING = register("life_mending", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.BREAKABLE, 3, false, true,
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
				EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND));
		// 坚甲：单次耐久损耗上限，四件盔甲
		STURDY = register("sturdy", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR, 3, false, false,
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET));
		// 出其不意：几率让本次攻击再结算一次，剑斧三叉戟，宝藏
		SURPRISE = register("surprise", new SimpleEnchantment(
				Enchantment.Rarity.VERY_RARE, EnchantmentCategory.WEAPON, 3, true, false, EquipmentSlot.MAINHAND));
		// 幻影箭：几率额外射出一支箭，弓弩，宝藏
		PHANTOM_ARROW = register("phantom_arrow", new SimpleEnchantment(
				Enchantment.Rarity.VERY_RARE, EnchantmentCategory.BOW, 3, true, false, EquipmentSlot.MAINHAND));
		// 无尽箭袋：等同无限但任何箭矢都不消耗，弓弩，宝藏，与无限互斥
		ENDLESS_QUIVER = register("endless_quiver", new SimpleEnchantment(
				Enchantment.Rarity.VERY_RARE, EnchantmentCategory.BOW, 1, true, false, EquipmentSlot.MAINHAND));
		// 斩杀：目标生命 ≤15% 时补一击必杀，剑斧三叉戟，宝藏
		EXECUTION = register("execution", new SimpleEnchantment(
				Enchantment.Rarity.VERY_RARE, EnchantmentCategory.WEAPON, 1, true, false, EquipmentSlot.MAINHAND));
		// 速食：每级 -25% 进食时长（最低 8 tick），头盔
		QUICK_EAT = register("quick_eat", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR_HEAD, 3, false, false, EquipmentSlot.HEAD));
		// 大胃袋：每级 +4 饥饿值上限，胸甲
		BIG_STOMACH = register("big_stomach", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR_CHEST, 5, false, false, EquipmentSlot.CHEST));
		// 死者之心：最大生命 ×1.5、受到治疗 -50%，胸甲，诅咒（可进附魔台）
		DEAD_MANS_HEART = register("dead_mans_heart", new SimpleEnchantment(
				Enchantment.Rarity.VERY_RARE, EnchantmentCategory.ARMOR_CHEST, 1, false, true, EquipmentSlot.CHEST));
		// 复仇：受击后 3 秒内剑斧三叉戟近战伤害提高，胸甲
		REVENGE = register("revenge", new SimpleEnchantment(
				Enchantment.Rarity.RARE, EnchantmentCategory.ARMOR_CHEST, 3, false, false, EquipmentSlot.CHEST));
		// 灵魂加护：死亡时原地满血复活（180 秒冷却），胸甲，宝藏
		SOUL_GRACE = register("soul_grace", new SimpleEnchantment(
				Enchantment.Rarity.VERY_RARE, EnchantmentCategory.ARMOR_CHEST, 1, true, false, EquipmentSlot.CHEST));
		// 灵动步伐：每级 +5% 几率完全闪避伤害，靴子，宝藏
		NIMBLE_STEPS = register("nimble_steps", new SimpleEnchantment(
				Enchantment.Rarity.VERY_RARE, EnchantmentCategory.ARMOR_FEET, 3, true, false, EquipmentSlot.FEET));
	}

	private static <T extends Enchantment> T register(String path, T enchantment) {
		pendingEvent.register(ForgeRegistries.Keys.ENCHANTMENTS,
				EnchantmentReforged.id(path), () -> enchantment);
		return enchantment;
	}
}
