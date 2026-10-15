package com.summy.enchantment.mixin;

import com.summy.enchantment.combat.EnchantmentEffects;
import com.summy.enchantment.combat.ArrowTraits;
import com.summy.enchantment.config.SummyEnchantmentConfig;
import com.summy.enchantment.network.ConfigSync;
import com.summy.enchantment.registry.ModEnchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让原版弓的附魔在弩上生效（开关：enable_crossbow_compat）。
 *
 * <ul>
 *     <li>力量/火矢/冲击：在箭生成之后按原版弓的数值写进箭的属性；</li>
 *     <li>无限：装填时仅普通箭免消耗（与原版弓一致），无弹药也能射出普通箭；</li>
 *     <li>疾矢：弩射出的箭速度按等级放大（多重射击每支都吃）；</li>
 *     <li>幻影箭：每支箭独立判定，额外箭属性复制自原箭；</li>
 *     <li>多重射击：散射角可配置，并可让三支箭对同一目标都生效。</li>
 * </ul>
 * "可以附到弩上"由 {@code EnchantmentMixin} 的 isAcceptableItem 处理。
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
	@Unique
	private static final ThreadLocal<ItemStack> summyEnchantment$currentCrossbow = new ThreadLocal<>();

	/** 箭生成后追加弓类附魔的效果 */
	@Inject(method = "getArrow", at = @At("RETURN"))
	private static void summyEnchantment$applyBowEnchantments(Level world, LivingEntity shooter,
			ItemStack crossbow, ItemStack ammo, CallbackInfoReturnable<AbstractArrow> cir) {
		AbstractArrow arrow = cir.getReturnValue();
		if (arrow != null) {
			EnchantmentEffects.applyCrossbowEnchantments(crossbow, arrow);
			summyEnchantment$applyArrowTraits(crossbow, arrow, shooter);
		}
	}

	/**
	 * 1.3.7 的箭矢附魔（弩侧）：轻盈（等级登记 + 同步）与死亡之眼（登记参与统计）。
	 *
	 * <p>破空之愿只作用于弓，弩侧不处理；多重射击的三支箭各自经过 {@code getArrow}，因此三支都登记。
	 */
	@Unique
	private static void summyEnchantment$applyArrowTraits(ItemStack crossbow, AbstractArrow arrow, LivingEntity shooter) {
		SummyEnchantmentConfig config = SummyEnchantmentConfig.get();
		if (config.enableLightness && ModEnchantments.LIGHTNESS != null) {
			int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.LIGHTNESS, crossbow);
			if (level > 0) {
				ArrowTraits.setServerLightness(arrow, level);
				ConfigSync.sendArrowLightness(arrow, level);
			}
		}
		if (config.enableDeathEye && ModEnchantments.DEATH_EYE != null && shooter != null
				&& EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.DEATH_EYE, crossbow) > 0) {
			ArrowTraits.markDeathEye(arrow, shooter);
		}
	}

	@Inject(method = "tryLoadProjectiles", at = @At("HEAD"))
	private static void summyEnchantment$captureCrossbow(LivingEntity shooter, ItemStack crossbow,
			CallbackInfoReturnable<Boolean> cir) {
		summyEnchantment$currentCrossbow.set(crossbow);
	}

	@Inject(method = "tryLoadProjectiles", at = @At("RETURN"))
	private static void summyEnchantment$clearCrossbow(LivingEntity shooter, ItemStack crossbow,
			CallbackInfoReturnable<Boolean> cir) {
		summyEnchantment$currentCrossbow.remove();
	}

	/**
	 * 免消耗弹药：无限（仅普通箭，与原版弓一致）与无尽箭袋（任何箭矢）。
	 *
	 * <p>原版消耗发生在 {@code loadProjectile} 里的 {@code projectile.split(1)}，
	 * 这里改成"返回一份副本"，等价于不消耗，且能拿到弹药类型做判断。
	 */
	@WrapOperation(
			method = "loadProjectile",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;split(I)Lnet/minecraft/world/item/ItemStack;"
			)
	)
	private static ItemStack summyEnchantment$spareAmmo(ItemStack projectile, int amount,
			Operation<ItemStack> original, @Local(argsOnly = true, index = 1) ItemStack crossbow) {
		if (crossbow != null && EnchantmentEffects.sparesAmmo(crossbow, projectile)) {
			// 不消耗：给"被装填的那支"一份副本
			return projectile.copy();
		}
		return original.call(projectile, amount);
	}

	/** 无弹药兜底：无限/无尽箭袋在弩上也能无箭射出普通箭（与弓一致） */
	@ModifyVariable(method = "tryLoadProjectiles", at = @At("STORE"), ordinal = 0)
	private static boolean summyEnchantment$emptyAmmoFallback(boolean creative, @Local(argsOnly = true) ItemStack crossbow) {
		return creative || EnchantmentEffects.allowsEmptyAmmoCrossbow(crossbow);
	}

	/** 多重射击散射角：改成配置值（默认 10 = 原版），左右对称 */
	@ModifyConstant(method = "performShooting", constant = @Constant(floatValue = -10.0F))
	private static float summyEnchantment$spreadLeft(float original) {
		return -SummyEnchantmentConfig.get().multishotSpreadDegrees;
	}

	@ModifyConstant(method = "performShooting", constant = @Constant(floatValue = 10.0F))
	private static float summyEnchantment$spreadRight(float original) {
		return SummyEnchantmentConfig.get().multishotSpreadDegrees;
	}

	/** 疾矢：弩射出的箭速度 ×(1 + 每级加成 × 等级)，多重射击每支都吃 */
	@ModifyArg(
			method = "shootProjectile",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/Projectile;shoot(DDDFF)V"
			),
			index = 3
	)
	private static float summyEnchantment$crossbowArrowSpeed(float speed,
			@Local(argsOnly = true, index = 3) ItemStack crossbow) {
		return speed * EnchantmentEffects.arrowSpeedMultiplier(crossbow);
	}

	/** 幻影箭（弩）：每支箭独立判定；带多重射击时按开关给每支箭打"保证生效"标记 */
	@WrapOperation(
			method = "shootProjectile",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
			)
	)
	private static boolean summyEnchantment$spawnPhantomArrow(Level world, Entity entity,
			Operation<Boolean> original, @Local(argsOnly = true, index = 3) ItemStack crossbow) {
		boolean spawned = original.call(world, entity);
		if (!spawned || !(entity instanceof AbstractArrow arrow)) {
			return spawned;
		}
		EnchantmentEffects.markMultishotArrow(crossbow, arrow);
		if (EnchantmentEffects.rollPhantomArrow(crossbow)) {
			EnchantmentEffects.markExtraHitArrow(arrow);
			EnchantmentEffects.spawnExtraArrow(arrow);
		}
		return spawned;
	}
}
