package com.summy.enchantment.mixin;

import com.summy.enchantment.combat.EnchantmentEffects;
import com.summy.enchantment.combat.ArrowTraits;
import com.summy.enchantment.config.SummyEnchantmentConfig;
import com.summy.enchantment.network.ConfigSync;
import com.summy.enchantment.registry.ModEnchantments;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
	private static final ThreadLocal<ItemStack> summyEnchantment$currentBow = new ThreadLocal<>();
	/** 本次松手时的真实蓄力 tick 数（破空之愿用；与"速射"改的换算无关） */
	@Unique
	private static final ThreadLocal<Integer> summyEnchantment$currentDrawTicks = new ThreadLocal<>();

	@Inject(method = "releaseUsing", at = @At("HEAD"))
	private void summyEnchantment$captureBow(ItemStack stack, Level world, LivingEntity user,
			int remainingUseTicks, CallbackInfo ci) {
		summyEnchantment$currentBow.set(stack);
		summyEnchantment$currentDrawTicks.set(Math.max(0, stack.getUseDuration() - remainingUseTicks));
	}

	@Inject(method = "releaseUsing", at = @At("RETURN"))
	private void summyEnchantment$clearBow(ItemStack stack, Level world, LivingEntity user,
			int remainingUseTicks, CallbackInfo ci) {
		summyEnchantment$currentBow.remove();
		summyEnchantment$currentDrawTicks.remove();
	}

	/** 速射：把"已经过的 tick"放大，等效于满蓄力时间缩短 */
	@ModifyArg(
			method = "releaseUsing",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"),
			index = 0
	)
	private int summyEnchantment$speedUpDraw(int useTicks) {
		if (!SummyEnchantmentConfig.get().enableQuickDraw) {
			return useTicks;
		}
		ItemStack bow = summyEnchantment$currentBow.get();
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
			method = "releaseUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;shootFromRotation(Lnet/minecraft/world/entity/Entity;FFFFF)V"
			),
			index = 4
	)
	private float summyEnchantment$arrowVelocity(float speed) {
		SummyEnchantmentConfig config = SummyEnchantmentConfig.get();
		if (!config.enableArrowVelocity || ModEnchantments.ARROW_VELOCITY == null) {
			return speed;
		}
		ItemStack bow = summyEnchantment$currentBow.get();
		if (bow == null) {
			return speed;
		}
		int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ARROW_VELOCITY, bow);
		if (level <= 0) {
			return speed;
		}
		return speed * (1.0F + config.arrowVelocityBonusPerLevel * level);
	}

	/**
	 * 无尽箭袋：决定这次射击是否免消耗弹药。
	 *
	 * <p>Forge 1.20.1 的弓用 {@link ArrowItem#isInfinite(ItemStack, ItemStack, Player)}
	 * 判断免消耗，而不是 {@code ItemStack#is(Items.ARROW)}。原版只认“无限 + 普通箭”，
	 * 这里让本模组的无尽箭袋对所有 {@link ArrowItem} 弹药都返回免消耗。
	 */
	@WrapOperation(
			method = "releaseUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ArrowItem;isInfinite(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Z",
					remap = false
			)
	)
	private boolean summyEnchantment$endlessQuiverSparesAmmo(ArrowItem item, ItemStack ammo, ItemStack bow,
			Player player, Operation<Boolean> original) {
		if (original.call(item, ammo, bow, player)) {
			return true;
		}
		return EnchantmentEffects.sparesAmmo(bow, ammo);
	}

	/** 无尽箭袋：让原版"无限"的判定也认同它（无弹药兜底仍走这条；免消耗由上面的 ArrowItem#isInfinite 包装处理） */
	@WrapOperation(
			method = "releaseUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getItemEnchantmentLevel(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/item/ItemStack;)I"
			)
	)
	private int summyEnchantment$endlessQuiverActsLikeInfinity(Enchantment enchantment, ItemStack stack,
			Operation<Integer> original) {
		int level = original.call(enchantment, stack);
		if (level <= 0 && enchantment == Enchantments.INFINITY_ARROWS && EnchantmentEffects.hasEndlessQuiver(stack)) {
			return 1;
		}
		return level;
	}

	/**
	 * 主箭生成后的追加处理：弩的两个附魔（穿透 / 多重射击，1.3.6 起）与幻影箭。
	 *
	 * <p>挂在原有的实体生成包装点上——此处主箭的附魔（力量/火矢/冲击/暴击/疾矢）都已应用完毕，
	 * 因此额外箭靠 NBT 复制就能与原箭表现一致。
	 */
	@WrapOperation(
			method = "releaseUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
			)
	)
	private boolean summyEnchantment$afterArrowSpawned(Level world, Entity entity, Operation<Boolean> original) {
		boolean spawned = original.call(world, entity);
		if (!spawned || !(entity instanceof AbstractArrow arrow)) {
			return spawned;
		}
		ItemStack bow = summyEnchantment$currentBow.get();
		if (bow == null) {
			return spawned;
		}
		summyEnchantment$applyArrowTraits(bow, arrow);
		summyEnchantment$applyBowCrossbowEnchantments(bow, arrow);
		if (EnchantmentEffects.rollPhantomArrow(bow)) {
			EnchantmentEffects.markExtraHitArrow(arrow);
			EnchantmentEffects.spawnExtraArrow(arrow);
		}
		return spawned;
	}

	/**
	 * 弩的两个附魔在弓上的效果（共用开关 {@code enable_crossbow_compat}，1.3.6 起双向互通）。
	 *
	 * <ul>
	 *     <li><b>穿透</b>：按等级直接写原版的穿透字段（1.20.1 该字段是公开 API）；必须先写再生成额外箭，
	 *     额外箭靠 NBT 复制继承；</li>
	 *     <li><b>多重射击</b>：原版是"主箭 + 左右各一支"，散射角与弩共用 {@code multishot_spread_degrees}；
	 *     额外箭的方向按原版弩的数学重算（见 {@code EnchantmentEffects#spawnMultishotArrow}）。</li>
	 * </ul>
	 *
	 * <p>弹药口径：固定射 3 支、仍只走原版那次消耗（无限/无尽箭袋/创造照旧免耗），额外箭不可拾取。
	 */
	@Unique
	private static void summyEnchantment$applyBowCrossbowEnchantments(ItemStack bow, AbstractArrow arrow) {
		SummyEnchantmentConfig config = SummyEnchantmentConfig.get();
		if (!config.enableCrossbowCompat) {
			return;
		}
		int pierce = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, bow);
		if (pierce > 0) {
			arrow.setPierceLevel((byte) pierce);
		}
		int multishot = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, bow);
		if (multishot <= 0) {
			return;
		}
		// 与弩侧同口径：主箭与额外箭各自判定"保证生效"
		EnchantmentEffects.markMultishotArrow(bow, arrow);
		if (!(arrow.getOwner() instanceof LivingEntity shooter)) {
			return;
		}
		double spread = config.multishotSpreadDegrees;
		float inaccuracy = config.bowInaccuracy;
		EnchantmentEffects.spawnMultishotArrow(arrow, bow, shooter, -spread, inaccuracy);
		EnchantmentEffects.spawnMultishotArrow(arrow, bow, shooter, spread, inaccuracy);
	}

	/**
	 * 1.3.7 的三个箭矢附魔（弓侧）：
	 *
	 * <ul>
	 *     <li><b>轻盈</b>：登记等级并同步给客户端（重力两端都要算）；</li>
	 *     <li><b>破空之愿</b>：按真实蓄力 tick 放大箭的<b>基础伤害</b>（已含原版力量加值）；
	 *     0 tick 起算、每 tick +5%、上限 +200%（40 tick 封顶）；</li>
	 *     <li><b>死亡之眼</b>：把这支箭登记为"参与统计"（层数由命中/落空推进）。</li>
	 * </ul>
	 *
	 * <p>必须放在生成多重射击/幻影箭的额外箭之前——额外箭靠复制会顺带继承这三项。
	 */
	@Unique
	private static void summyEnchantment$applyArrowTraits(ItemStack bow, AbstractArrow arrow) {
		SummyEnchantmentConfig config = SummyEnchantmentConfig.get();
		if (config.enableLightness && ModEnchantments.LIGHTNESS != null) {
			int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.LIGHTNESS, bow);
			if (level > 0) {
				ArrowTraits.setServerLightness(arrow, level);
				ConfigSync.sendArrowLightness(arrow, level);
			}
		}
		if (config.enableSkyWish && ModEnchantments.SKY_WISH != null
				&& EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.SKY_WISH, bow) > 0) {
			Integer drawTicks = summyEnchantment$currentDrawTicks.get();
			int ticks = drawTicks == null ? 0 : drawTicks;
			float bonus = (float) Math.min(config.skyWishBonusPerTick * ticks, config.skyWishMaxBonus);
			if (bonus > 0.0F) {
				arrow.setBaseDamage(arrow.getBaseDamage() * (1.0D + bonus));
			}
		}
		if (config.enableDeathEye && ModEnchantments.DEATH_EYE != null
				&& EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.DEATH_EYE, bow) > 0
				&& arrow.getOwner() instanceof LivingEntity shooter) {
			ArrowTraits.markDeathEye(arrow, shooter);
		}
	}

	/** 原版弓的自然散布（第 5 号实参，原版写死 1.0）改成读配置 */
	@ModifyArg(
			method = "releaseUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;shootFromRotation(Lnet/minecraft/world/entity/Entity;FFFFF)V"
			),
			index = 5
	)
	private float summyEnchantment$bowInaccuracy(float divergence) {
		return SummyEnchantmentConfig.get().bowInaccuracy;
	}
}
