package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 忠诚改版。
 *
 * <p>原版投掷会把三叉戟从物品栏里拿走，等它飞回来再塞回去。改版后：
 * <ul>
 *     <li>三叉戟本体始终留在物品栏（投掷只扣耐久）；</li>
 *     <li>真正飞出去的是"假三叉戟"，它命中照常结算伤害，飞回来时只消失、不复制；</li>
 *     <li>假三叉戟没回来之前不能再投：用原版物品冷却让快捷栏显示灰色冷却圈，
 *         既看得见、又由服务端权威地拒绝右键，冷却结束即恢复。</li>
 * </ul>
 * 只包装两处原版调用与一个方法头，没有改写 {@code onStoppedUsing} 的逻辑。
 */
@Mixin(TridentItem.class)
public abstract class TridentItemMixin {
	/** 本次释放是否保留本体（HEAD 记录、TAIL 清掉，仅在同一次投掷内使用） */
	@Unique
	private boolean enchantmentReforged$keepTrident;

	/** 本次释放是否真的投出了假三叉戟（只有它才该挂投掷冷却） */
	@Unique
	private boolean enchantmentReforged$phantomThrown;

	@Inject(method = "onStoppedUsing", at = @At("HEAD"))
	private void enchantmentReforged$recordLoyaltyRework(ItemStack stack, World world, LivingEntity user,
			int remainingUseTicks, CallbackInfo ci) {
		// 创造模式原版就不消耗物品，保持原样
		this.enchantmentReforged$keepTrident = user instanceof PlayerEntity player
				&& !player.getAbilities().creativeMode
				&& EnchantmentEffects.isLoyaltyReworkActive(stack);
	}

	@Inject(method = "onStoppedUsing", at = @At("TAIL"))
	private void enchantmentReforged$clearLoyaltyRework(ItemStack stack, World world, LivingEntity user,
			int remainingUseTicks, CallbackInfo ci) {
		// 只有真的投出去了才挂冷却：蓄力不足等提前 return 的情况不会误挂
		if (this.enchantmentReforged$phantomThrown && user instanceof PlayerEntity player) {
			EnchantmentEffects.setLoyaltyThrowCooldown(player);
		}
		this.enchantmentReforged$keepTrident = false;
		this.enchantmentReforged$phantomThrown = false;
	}

	/** 忠诚：不把本体从物品栏拿走 */
	@WrapOperation(
			method = "onStoppedUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/player/PlayerInventory;removeOne(Lnet/minecraft/item/ItemStack;)V"
			)
	)
	private void enchantmentReforged$keepLoyalTrident(PlayerInventory inventory, ItemStack stack,
			Operation<Void> original) {
		if (this.enchantmentReforged$keepTrident) {
			return;
		}
		original.call(inventory, stack);
	}

	/** 投出去的那把打上"假三叉戟"标记 */
	@WrapOperation(
			method = "onStoppedUsing",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
			)
	)
	private boolean enchantmentReforged$markPhantomTrident(World world, Entity entity, Operation<Boolean> original) {
		if (this.enchantmentReforged$keepTrident && entity instanceof TridentEntity trident) {
			EnchantmentEffects.markPhantomTrident(trident);
			this.enchantmentReforged$phantomThrown = true;
		}
		return original.call(world, entity);
	}

	/** 假三叉戟没回来之前不能再投 */
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$blockWhilePhantomFlying(World world, PlayerEntity user, Hand hand,
			CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.getAbilities().creativeMode || !EnchantmentEffects.isLoyaltyReworkActive(stack)) {
			return;
		}
		if (EnchantmentEffects.hasFlyingLoyalTrident(user)) {
			cir.setReturnValue(TypedActionResult.fail(stack));
		}
	}
}
