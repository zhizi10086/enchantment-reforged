package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.registry.ModEnchantments;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 弓的两个附魔：速射（缩短满蓄力时间）与疾矢（提高箭矢初速度）。
 *
 * <p>原版拉弓威力由 static 的 {@code getPullProgress(int)} 计算，方法内硬编码 /20，
 * 拿不到玩家与弓，所以这里缓存"正在松手的那把弓"，把实参按缩短后的时间换算。
 */
@Mixin(BowItem.class)
public abstract class BowItemMixin {
	@Unique
	private static final ThreadLocal<ItemStack> enchantmentReforged$currentBow = new ThreadLocal<>();

	@Inject(method = "onStoppedUsing", at = @At("HEAD"))
	private void enchantmentReforged$captureBow(ItemStack stack, World world, LivingEntity user,
			int remainingUseTicks, CallbackInfo ci) {
		enchantmentReforged$currentBow.set(stack);
	}

	@Inject(method = "onStoppedUsing", at = @At("RETURN"))
	private void enchantmentReforged$clearBow(ItemStack stack, World world, LivingEntity user,
			int remainingUseTicks, CallbackInfo ci) {
		enchantmentReforged$currentBow.remove();
	}

	/** 速射：把"已经过的 tick"放大，等效于满蓄力时间缩短 */
	@ModifyArg(
			method = "onStoppedUsing",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/BowItem;getPullProgress(I)F"),
			index = 0
	)
	private int enchantmentReforged$speedUpDraw(int useTicks) {
		if (!EnchantmentReforgedConfig.get().enableQuickDraw) {
			return useTicks;
		}
		ItemStack bow = enchantmentReforged$currentBow.get();
		if (bow == null) {
			return useTicks;
		}
		int required = EnchantmentEffects.requiredDrawTicks(bow);
		if (required >= 20) {
			return useTicks;
		}
		return Math.round(useTicks * (20.0F / required));
	}

	/** 疾矢：箭矢初速度 × (1 + 每级加成 × 等级) */
	@ModifyArg(
			method = "onStoppedUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/projectile/PersistentProjectileEntity;setVelocity(Lnet/minecraft/entity/Entity;FFFFF)V"
			),
			index = 4
	)
	private float enchantmentReforged$arrowVelocity(float speed) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableArrowVelocity || ModEnchantments.ARROW_VELOCITY == null) {
			return speed;
		}
		ItemStack bow = enchantmentReforged$currentBow.get();
		if (bow == null) {
			return speed;
		}
		int level = EnchantmentHelper.getLevel(ModEnchantments.ARROW_VELOCITY, bow);
		if (level <= 0) {
			return speed;
		}
		return speed * (1.0F + config.arrowVelocityBonusPerLevel * level);
	}

	/**
	 * 无尽箭袋 / 无限：决定这次射击是否免消耗弹药。
	 *
	 * <p>原版这里是 {@code bl2 = 有无限 && 弹药是普通箭}，只有普通箭才免消耗；
	 * 无尽箭袋是它的上位替代——任何弹药都免消耗。
	 */
	@WrapOperation(
			method = "onStoppedUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"
			)
	)
	private boolean enchantmentReforged$spareAmmo(ItemStack ammo, Item item, Operation<Boolean> original) {
		if (original.call(ammo, item)) {
			return true;
		}
		ItemStack bow = enchantmentReforged$currentBow.get();
		return bow != null && EnchantmentEffects.sparesAmmo(bow, ammo);
	}

	/** 无尽箭袋：让原版"无限"的判定也认同它（无弹药兜底与免消耗都走这条） */
	@WrapOperation(
			method = "onStoppedUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/enchantment/EnchantmentHelper;getLevel(Lnet/minecraft/enchantment/Enchantment;Lnet/minecraft/item/ItemStack;)I"
			)
	)
	private int enchantmentReforged$endlessQuiverActsLikeInfinity(Enchantment enchantment, ItemStack stack,
			Operation<Integer> original) {
		int level = original.call(enchantment, stack);
		if (level <= 0 && enchantment == Enchantments.INFINITY && EnchantmentEffects.hasEndlessQuiver(stack)) {
			return 1;
		}
		return level;
	}

	/** 幻影箭：主箭生成后按几率再生成一支同款箭（并给主箭打上"保证生效"标记） */
	@WrapOperation(
			method = "onStoppedUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
			)
	)
	private boolean enchantmentReforged$spawnPhantomArrow(World world, Entity entity, Operation<Boolean> original) {
		boolean spawned = original.call(world, entity);
		if (spawned && entity instanceof PersistentProjectileEntity arrow) {
			ItemStack bow = enchantmentReforged$currentBow.get();
			if (bow != null && EnchantmentEffects.rollPhantomArrow(bow)) {
				EnchantmentEffects.markExtraHitArrow(arrow);
				EnchantmentEffects.spawnExtraArrow(arrow);
			}
		}
		return spawned;
	}

	/** 原版弓的自然散布（第 5 号实参，原版写死 1.0）改成读配置 */
	@ModifyArg(
			method = "onStoppedUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/projectile/PersistentProjectileEntity;setVelocity(Lnet/minecraft/entity/Entity;FFFFF)V"
			),
			index = 5
	)
	private float enchantmentReforged$bowInaccuracy(float divergence) {
		return EnchantmentReforgedConfig.get().bowInaccuracy;
	}
}
