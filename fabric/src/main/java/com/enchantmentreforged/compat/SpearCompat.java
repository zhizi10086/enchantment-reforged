package com.enchantmentreforged.compat;

import com.enchantmentreforged.combat.CombatFormulas;
import com.enchantmentreforged.combat.EnchantmentEffects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 与矛模组 Backported Spears（mod id: spears）的兼容层。
 *
 * <p>这里刻意**不引用对方的任何类**：物品判定靠命名空间/类名前缀，攻击逻辑靠独立的
 * 可选 Mixin（{@code SpearItemCompatMixin}）。因此没装矛模组时本类恒为"未生效"，
 * 对原版行为零影响。
 *
 * <p>矛模组 1.4.7 的 {@code pierce} 在玩家分支里把基础伤害算了两遍
 * （{@code 最终值 = 参数·(1+cd) + cd·附魔}），所以这里按它的公式**反解**出一个参数，
 * 让我们要的伤害正好等于它最终的输出。
 */
public final class SpearCompat {
	private static final String MOD_ID = "spears";
	private static final String PACKAGE_PREFIX = "com.notunanancyowen.spears.";
	/** 已核对过公式、需要做伤害修正的版本；其它版本一律原样放行 */
	private static final String SUPPORTED_VERSION = "1.4.7";

	private static Boolean active;

	private SpearCompat() {
	}

	/** 是否是矛模组的矛（未安装矛模组时恒为 false） */
	public static boolean isSpear(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (stack.getItem().getClass().getName().startsWith(PACKAGE_PREFIX)) {
			return true;
		}
		Identifier id = Registries.ITEM.getId(stack.getItem());
		return id != null && MOD_ID.equals(id.getNamespace());
	}

	/** 兼容是否生效（矛模组存在且版本为已核对的 {@value #SUPPORTED_VERSION}） */
	public static boolean isActive() {
		if (active == null) {
			active = FabricLoader.getInstance().getModContainer(MOD_ID)
					.map(container -> SUPPORTED_VERSION.equals(container.getMetadata().getVersion().getFriendlyString()))
					.orElse(false);
		}
		return active;
	}

	/**
	 * 修正矛模组传给 {@code pierce} 的伤害参数。
	 *
	 * @param attacker       使用矛的生物
	 * @param spear          矛本体（附魔等级取自它）
	 * @param target         本次目标
	 * @param originalDamage 矛模组原本要传入的参数
	 * @param leftClick      true = 左键刺击（stab），false = 右键充能刺击
	 * @return 反解后的参数，使矛模组算出的最终伤害等于我们期望的值
	 */
	public static float adjustPierceDamage(LivingEntity attacker, ItemStack spear, @Nullable Entity target,
			float originalDamage, boolean leftClick) {
		// 只有玩家分支存在"基础伤害算两遍"的问题，其它生物原样放行
		if (!(attacker instanceof PlayerEntity player) || !isActive() || originalDamage <= 0.0F) {
			return originalDamage;
		}

		EntityGroup group = target instanceof LivingEntity living ? living.getGroup() : EntityGroup.DEFAULT;
		float enchant = EnchantmentHelper.getAttackDamage(spear, group);

		// 复刻矛模组自己的口径：充能中按满蓄力算，否则取攻击冷却进度
		boolean usingItem = attacker.isUsingItem();
		float cooldown = usingItem ? 1.0F : MathHelper.clamp(player.getAttackCooldownProgress(0.5F), 0.0F, 1.0F);
		// 它内部对"参数"的系数：未使用物品时还会乘 (0.2 + 0.8·cd²)，再加上 cd 那一份
		float factor = (usingItem ? 1.0F : 0.2F + 0.8F * cooldown * cooldown) + cooldown;

		// 期望的"不再翻倍"的伤害：左键 = 基础 + 附魔×蓄力；右键 = 原参数（已含基础+附魔+充能奖励）
		float wanted = leftClick ? originalDamage + cooldown * enchant : originalDamage;
		// 我们的倍率：死神祝福两条路径都吃，力量Plus 仅左键
		float multiplier = EnchantmentEffects.deathsBlessingOutgoing(attacker);
		if (leftClick) {
			multiplier *= CombatFormulas.strengthMultiplier(attacker);
		}
		wanted *= multiplier;

		// 反解：它最终算出的值 = 参数·factor + cd·附魔，令其等于 wanted
		return Math.max(0.0F, (wanted - cooldown * enchant) / Math.max(factor, 1.0E-4F));
	}

	/** 命中后按本次实际伤害结算魔剑与嗜血（与近战共用同一套逻辑） */
	public static void applyHitEnchantments(LivingEntity attacker, ItemStack spear, Entity target, float dealtDamage) {
		if (!(attacker instanceof PlayerEntity) || !isActive()) {
			return;
		}
		EnchantmentEffects.applySpellblade(attacker, spear, target, dealtDamage);
		EnchantmentEffects.applyLifesteal(attacker, spear, dealtDamage);
	}
}
