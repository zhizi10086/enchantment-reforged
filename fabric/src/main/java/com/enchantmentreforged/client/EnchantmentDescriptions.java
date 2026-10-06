package com.enchantmentreforged.client;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.registry.ModEnchantments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 附魔书的说明文本。
 *
 * <p>数值全部取当前配置；多级附魔按"该等级的总效果（每级效果）"显示，
 * 这样不同等级的附魔书描述互不相同，也不会让人误以为"数值就是每级的量"。
 */
@Environment(EnvType.CLIENT)
public final class EnchantmentDescriptions {
	/** 判断"这件是不是正穿在/拿在身上的"时检查的装备槽 */
	private static final EquipmentSlot[] WORN_SLOTS = {
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private EnchantmentDescriptions() {
	}

	public static List<Text> describe(ItemStack stack) {
		// 附魔书：不加 [名称等级] 前缀，但保留"忠诚已修正"那一行
		return collect(stack, false);
	}

	/**
	 * 装备（非附魔书）用的描述：不含"忠诚已修正"那一行。
	 *
	 * <p>那行在装备提示里是常显的，不参与"按住 Shift 展开"，所以这里排除掉避免重复。
	 */
	public static List<Text> describeEquipment(ItemStack stack) {
		// 装备：带 [名称等级] 前缀，忠诚那行单独常显
		return collect(stack, true);
	}

	private static List<Text> collect(ItemStack stack, boolean withPrefix) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		List<Text> lines = new ArrayList<>();

		for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.get(stack).entrySet()) {
			Text line = describeOne(entry.getKey(), Math.max(1, entry.getValue()), stack, config, withPrefix);
			if (line != null) {
				lines.add(line);
			}
		}
		return lines;
	}

	private static Text describeOne(Enchantment enchantment, int level, ItemStack stack,
			EnchantmentReforgedConfig config, boolean withPrefix) {
		if (enchantment == ModEnchantments.SHARPNESS_PLUS) {
			float per = config.sharpnessDamagePerLevel;
			return key(enchantment, level, withPrefix, "sharpness_plus", number(per * level));
		}
		if (enchantment == ModEnchantments.SPELLBLADE) {
			float per = config.spellbladeMagicPercentPerLevel;
			return key(enchantment, level, withPrefix, "spellblade", percent(per * level));
		}
		if (enchantment == ModEnchantments.BEHEADING) {
			float per = config.beheadingHeadChancePerLevel;
			return key(enchantment, level, withPrefix, "beheading", percent(per * level));
		}
		if (enchantment == ModEnchantments.AUTO_SMELT) {
			return key(enchantment, level, withPrefix, "auto_smelt");
		}
		if (enchantment == ModEnchantments.ARROW_VELOCITY) {
			float per = config.arrowVelocityBonusPerLevel;
			return key(enchantment, level, withPrefix, "arrow_velocity", percent(per * level));
		}
		if (enchantment == ModEnchantments.QUICK_DRAW) {
			float per = config.quickDrawSecondsPerLevel;
			return key(enchantment, level, withPrefix, "quick_draw", number(per * level));
		}
		if (enchantment == ModEnchantments.INSTINCTIVE_RELEASE) {
			return key(enchantment, level, withPrefix, "instinctive_release");
		}
		if (enchantment == ModEnchantments.LIFE_SHIELD) {
			float per = config.lifeShieldPercentPerLevel;
			return key(enchantment, level, withPrefix, "life_shield", percent(per * level), percent(per * level));
		}
		if (enchantment == ModEnchantments.HEALTH_BOOST) {
			float per = config.healthBoostPerLevel;
			return key(enchantment, level, withPrefix, "health_boost", number(per * level));
		}
		if (enchantment == ModEnchantments.LIFESTEAL) {
			float per = config.lifestealPercentPerLevel;
			return key(enchantment, level, withPrefix, "lifesteal", percent(per * level));
		}
		if (enchantment == ModEnchantments.SCHOLAR) {
			float per = config.scholarBonusPerLevel;
			return key(enchantment, level, withPrefix, "scholar", percent(per * level));
		}
		if (enchantment == ModEnchantments.CALAMITY) {
			return key(enchantment, level, withPrefix, "calamity");
		}
		if (enchantment == ModEnchantments.DEATHS_BLESSING) {
			PlayerEntity player = MinecraftClient.getInstance().player;
			if (player != null && isWornBy(player, stack)) {
				// 正穿在身上：显示此刻真正生效的数值
				float loss = EnchantmentEffects.healthLossPercent(player);
				return key(enchantment, level, withPrefix, "deaths_blessing.live",
						percent(loss / 100.0F),
						multiplier(EnchantmentEffects.deathsBlessingOutgoingAt(loss)),
						multiplier(EnchantmentEffects.deathsBlessingIncomingAt(loss)));
			}
			// 附魔书等：照旧显示三个系数
			return key(enchantment, level, withPrefix, "deaths_blessing",
					percent(config.deathsBlessingDamageTakenBonus),
					percent(config.deathsBlessingDamageBonusPerPercent),
					percent(config.deathsBlessingResistPerPercent));
		}
		if (enchantment == ModEnchantments.QUICK_STRIKE) {
			float per = config.quickStrikeBonusPerLevel;
			// 迅捷打击是"加法攻速"，所以显示绝对数值而不是百分比
			return key(enchantment, level, withPrefix, "quick_strike", number(per * level));
		}
		if (enchantment == ModEnchantments.LIFE_MENDING) {
			float per = config.lifeMendingDurabilityPerLevel;
			return key(enchantment, level, withPrefix, "life_mending", number(per * level));
		}
		if (enchantment == ModEnchantments.STURDY) {
			if (!withPrefix) {
				// 附魔书没有"最大耐久"，显示等级系数（百分比）而不是具体点数
				float coefficient = Math.max(0.0F, config.sturdyCoefficient - config.sturdyCoefficientStep * (level - 1));
				return key(enchantment, level, withPrefix, "sturdy.ratio", percent(coefficient));
			}
			// 装备：按这件装备的最大耐久算出的具体上限
			int cap = EnchantmentEffects.sturdyCapFor(stack.getMaxDamage(), level);
			return key(enchantment, level, withPrefix, "sturdy", number(cap));
		}
		if (enchantment == ModEnchantments.SURPRISE) {
			float per = config.surpriseChancePerLevel;
			return key(enchantment, level, withPrefix, "surprise", percent(per * level));
		}
		if (enchantment == ModEnchantments.PHANTOM_ARROW) {
			float per = config.phantomArrowChancePerLevel;
			return key(enchantment, level, withPrefix, "phantom_arrow", percent(per * level));
		}
		if (enchantment == ModEnchantments.ENDLESS_QUIVER) {
			return key(enchantment, level, withPrefix, "endless_quiver");
		}
		if (enchantment == ModEnchantments.EXECUTION) {
			return key(enchantment, level, withPrefix, "execution", percent(config.executionThreshold));
		}
		if (enchantment == ModEnchantments.QUICK_EAT) {
			float per = config.quickEatReductionPerLevel;
			return key(enchantment, level, withPrefix, "quick_eat", percent(per * level));
		}
		if (enchantment == ModEnchantments.BIG_STOMACH) {
			float per = config.bigStomachBonusPerLevel;
			return key(enchantment, level, withPrefix, "big_stomach", number(per * level));
		}
		if (enchantment == ModEnchantments.DEAD_MANS_HEART) {
			return key(enchantment, level, withPrefix, "dead_mans_heart",
					percent(config.deadMansHeartHealthBonus),
					percent(config.deadMansHeartHealingPenalty));
		}
		if (enchantment == ModEnchantments.REVENGE) {
			// 与效果共用同一处计算，避免"写出来的"和"算出来的"再次对不上
			return key(enchantment, level, withPrefix, "revenge", percent(EnchantmentEffects.revengeBonus(level)));
		}
		if (enchantment == ModEnchantments.SOUL_GRACE) {
			return key(enchantment, level, withPrefix, "soul_grace", config.soulGraceCooldownSeconds);
		}
		if (enchantment == ModEnchantments.NIMBLE_STEPS) {
			float per = config.nimbleStepsChancePerLevel;
			return key(enchantment, level, withPrefix, "nimble_steps", percent(per * level));
		}
		// 原版忠诚被本模组改成了"本体不离开物品栏"：附魔书上补一行说明（装备上那行单独常显）
		if (!withPrefix && enchantment == Enchantments.LOYALTY && config.enableLoyaltyRework) {
			return plain("loyalty_rework");
		}
		return null;
	}

	/**
	 * [附魔名(+罗马等级)] + 描述文本（不带句号）。
	 *
	 * @param withPrefix 只有装备上的描述才加前缀；附魔书不加
	 */
	private static Text key(Enchantment enchantment, int level, boolean withPrefix, String id, Object... args) {
		MutableText text = Text.empty();
		if (withPrefix) {
			text.append(Text.literal("[")).append(Text.translatable(enchantment.getTranslationKey()));
			// 上限只有 1 级的附魔不标等级
			if (enchantment.getMaxLevel() > 1) {
				text.append(Text.translatable("enchantment.level." + level));
			}
			text.append(Text.literal("]"));
		}
		return text.append(Text.translatable("tooltip.enchantment_reforged.desc." + id, args));
	}

	/** 不带附魔名前缀的说明行（忠诚那行用） */
	private static Text plain(String id, Object... args) {
		return Text.translatable("tooltip.enchantment_reforged.desc." + id, args);
	}

	/** 这件物品是否正被玩家穿在身上/拿在手上（用它来决定显示实时值还是系数文案） */
	private static boolean isWornBy(PlayerEntity player, ItemStack stack) {
		for (EquipmentSlot slot : WORN_SLOTS) {
			ItemStack worn = player.getEquippedStack(slot);
			if (!worn.isEmpty() && ItemStack.areEqual(worn, stack)) {
				return true;
			}
		}
		return false;
	}

	/** 倍率显示：至少保留一位小数（1.0 / 1.12 / 1.185） */
	private static String multiplier(float value) {
		String text = String.format(Locale.ROOT, "%.3f", value);
		while (text.length() > 3 && text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}

	/** 0.08 → "8%"（整数不带小数点） */
	private static String percent(float ratio) {
		double value = ratio * 100.0D;
		if (Math.abs(value - Math.round(value)) < 1.0E-3D) {
			return Math.round(value) + "%";
		}
		return String.format(Locale.ROOT, "%.1f%%", value);
	}

	/** 1.5 → "1.5"（去掉多余的 0） */
	private static String number(float value) {
		String text = String.format(Locale.ROOT, "%.2f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}
}
