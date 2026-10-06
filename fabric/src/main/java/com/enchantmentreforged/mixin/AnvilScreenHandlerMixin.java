package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.ExperienceMath;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 铁砧：消耗经验点数而不是等级，并移除"过于昂贵"。
 *
 * <p>做法是让 {@code cost} 字段承载"经验点数"：{@code updateResult} 结束后把原版的等级成本
 * 换算成从 0 级攒到该等级所需的总点数（每次 updateResult 都会重算，不存在重复换算），
 * 判定与扣费随之改成按点数处理；两处 40 级截断（"过于昂贵"）被改为不生效。
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
	/** 原版把成本放在同步数据槽里（Yarn 名 levelCost），这里直接改写它的值 */
	@Shadow
	@Final
	private Property levelCost;

	/** 成本换算：等级 → 从 0 级到该等级所需经验点数 */
	@Inject(method = "updateResult", at = @At("TAIL"))
	private void enchantmentReforged$convertCostToPoints(CallbackInfo ci) {
		if (!EnchantmentReforgedConfig.get().enableAnvilXpCost) {
			return;
		}
		int current = this.levelCost.get();
		if (current <= 0) {
			return;
		}
		// 成本封顶：反复使用同一件物品时原版成本会按 ×2+1 增长，这里把"等级"夹到上限，
		// 保证成本最多停在 anvil_max_cost_level 对应的点数（默认 30 级 = 1395 点），
		// 玩家只要有这些点数就一定取得出来，不会出现"越用越贵到用不了"。
		int level = Math.min(current, EnchantmentReforgedConfig.get().anvilMaxCostLevel);
		int points = ExperienceMath.totalPointsForLevel(level);
		if (points != current) {
			this.levelCost.set(points);
		}
	}

	/** 取出判定：经验点数足够即可（原版判的是等级） */
	@Inject(method = "canTakeOutput(Lnet/minecraft/entity/player/PlayerEntity;Z)Z", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$mayPickupWithPoints(PlayerEntity player, boolean allowSecondSlot,
			CallbackInfoReturnable<Boolean> cir) {
		if (!EnchantmentReforgedConfig.get().enableAnvilXpCost) {
			return;
		}
		int cost = ((AnvilScreenHandler) (Object) this).getLevelCost();
		// 可支付点数取"等级+进度折算"与累计计数中的较大者：
		// totalExperience 只是累计获得量，/xp … levels 这类命令只改等级不会同步它，
		// 用它判定会出现"1000 级却付不起 55 点"。扣费走 addExperience，本就会逐级往下扣，
		// 所以按等级折算才是玩家真正付得起的上限。
		int spendable = Math.max(player.totalExperience,
				ExperienceMath.spendablePoints(player.experienceLevel, player.experienceProgress));
		cir.setReturnValue((player.getAbilities().creativeMode || spendable >= cost) && cost > 0);
	}

	/** 扣费：把"扣等级"换成"扣经验点数"（此时 cost 已经是点数，原版传进来的是负值） */
	@Redirect(
			method = "onTakeOutput(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addExperienceLevels(I)V")
	)
	private void enchantmentReforged$payWithPoints(PlayerEntity player, int levels) {
		if (EnchantmentReforgedConfig.get().enableAnvilXpCost) {
			player.addExperience(levels);
		} else {
			player.addExperienceLevels(levels);
		}
	}

	/**
	 * 移除"过于昂贵"。
	 *
	 * <p>updateResult 里 40 出现三处：第 1 处是"堆叠修复成本固定 40"（保留），
	 * 第 2、3 处是 40 级截断（把成本压到 39 / 清空结果），这里让后两处永不触发。
	 */
	@ModifyConstant(method = "updateResult", constant = @Constant(intValue = 40, ordinal = 1))
	private int enchantmentReforged$keepCostAbove40(int original) {
		return EnchantmentReforgedConfig.get().enableAnvilXpCost ? Integer.MAX_VALUE : original;
	}

	@ModifyConstant(method = "updateResult", constant = @Constant(intValue = 40, ordinal = 2))
	private int enchantmentReforged$keepExpensiveResult(int original) {
		return EnchantmentReforgedConfig.get().enableAnvilXpCost ? Integer.MAX_VALUE : original;
	}
}
