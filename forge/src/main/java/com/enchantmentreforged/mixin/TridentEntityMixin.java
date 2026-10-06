package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.combat.PhantomTrident;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 投掷三叉戟的穿刺加成改为基岩版判定。
 *
 * <p>原版这里用 {@code target.getMobType()} 决定穿刺是否生效；我们把它换成
 * {@code MobType.WATER} 或 {@code DEFAULT}，从而复用原版穿刺的数值与叠加顺序。
 * 三叉戟上只有穿刺会用到生物类型，所以替换不会影响其它附魔。
 *
 * <p>此外这里还承载两件事：投掷命中结算魔剑/嗜血（等级取自三叉戟本体），
 * 以及忠诚改版生成的"假三叉戟"标记（飞回主人时只消失、不复制物品）。
 */
@Mixin(ThrownTrident.class)
public abstract class TridentEntityMixin implements PhantomTrident {
	@Unique
	private LivingEntity enchantmentReforged$impalingTarget;

	/** 忠诚改版生成的假三叉戟（本体还在玩家物品栏里） */
	@Unique
	private boolean enchantmentReforged$phantom;

	/** 假三叉戟的飞行计时（自管理，不依赖原版的 age） */
	@Unique
	private int enchantmentReforged$flightTicks;

	/** 三叉戟本体：投掷命中时用它取附魔等级（此时主手未必拿着三叉戟） */
	@Shadow
	private ItemStack tridentItem;

	/** 原版记录忠诚等级的同步数据（客户端也能读到，用来判断"假三叉戟还在飞"） */
	@Shadow
	private static EntityDataAccessor<Byte> ID_LOYALTY;

	@Override
	public void enchantmentReforged$setPhantom(boolean phantom) {
		this.enchantmentReforged$phantom = phantom;
	}

	@Override
	public boolean enchantmentReforged$isLoyal() {
		return ((ThrownTrident) (Object) this).getEntityData().get(ID_LOYALTY) > 0;
	}

	@Override
	public int enchantmentReforged$flightTicks() {
		return this.enchantmentReforged$flightTicks;
	}

	/**
	 * 假三叉戟的计时与兜底清场。
	 *
	 * <p>正常情况它会被主人接住（{@code tryPickup}）或随主人死亡而消失；
	 * 但跨维度、区块长期不加载等情况下可能一直留着。这里给它一个总寿命：
	 * 超过上限就直接清除。清除的只是"假货"，本体始终在玩家处，不构成物品损失。
	 */
	@Inject(method = "tick", at = @At("TAIL"))
	private void enchantmentReforged$tickPhantom(CallbackInfo ci) {
		if (!this.enchantmentReforged$phantom) {
			return;
		}
		++this.enchantmentReforged$flightTicks;
		ThrownTrident self = (ThrownTrident) (Object) this;
		// 只在服务端清除，客户端等服务端的移除包即可
		if (!self.level().isClientSide
				&& this.enchantmentReforged$flightTicks > EnchantmentEffects.PHANTOM_CLEANUP_TICKS
				&& !self.isRemoved()) {
			self.discard();
		}
	}

	// 官方映射里 Yarn 的 onEntityHit = onHitEntity
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void enchantmentReforged$captureTarget(EntityHitResult hitResult, CallbackInfo ci) {
		if (hitResult.getEntity() instanceof LivingEntity living) {
			this.enchantmentReforged$impalingTarget = living;
		}
	}

	@ModifyArg(
			method = "onHitEntity",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageBonus(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/MobType;)F"
			),
			index = 1
	)
	private MobType enchantmentReforged$impalingGroup(MobType group) {
		return EnchantmentEffects.impalingGroupFor(this.enchantmentReforged$impalingTarget, group);
	}

	/**
	 * 投掷命中的魔剑 / 嗜血。
	 *
	 * <p>近战走 {@code PlayerEntityMixin} 的主手判定，投掷时主手可能是空的，
	 * 所以这里用三叉戟本体上的附魔等级单独结算一次，效果与近战一致。
	 */
	@WrapOperation(
			method = "onHitEntity",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			)
	)
	private boolean enchantmentReforged$applyThrownEnchantments(Entity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		boolean hit = original.call(target, source, amount);
		if (hit) {
			Entity owner = ((ThrownTrident) (Object) this).getOwner();
			if (owner instanceof LivingEntity livingOwner) {
				float dealt = Math.max(amount, 0.0F);
				EnchantmentEffects.applySpellblade(livingOwner, this.tridentItem, target, dealt);
				EnchantmentEffects.applyLifesteal(livingOwner, this.tridentItem, dealt);
				// 斩杀：投掷三叉戟同样可以补刀（近战由 PlayerEntityMixin 处理）
				EnchantmentEffects.tryExecute(livingOwner, target, this.tridentItem);
				// 出其不意：投掷三叉戟同样可以让这次攻击再结算一次
				if (EnchantmentEffects.rollSurprise(livingOwner, this.tridentItem)) {
					if (target instanceof LivingEntity livingTarget) {
						livingTarget.invulnerableTime = 0;
					}
					if (target.hurt(source, amount)) {
						EnchantmentEffects.applySpellblade(livingOwner, this.tridentItem, target, dealt);
						EnchantmentEffects.applyLifesteal(livingOwner, this.tridentItem, dealt);
					}
				}
			}
		}
		return hit;
	}

	/** 假三叉戟飞回主人身上：让它消失即可，不能再复制一份（本体已经在物品栏里） */
	@Inject(method = "tryPickup", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$pickupPhantom(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (this.enchantmentReforged$phantom
				&& player == ((ThrownTrident) (Object) this).getOwner()) {
			cir.setReturnValue(true);
		}
	}

	// 官方映射里 Yarn 的 writeCustomDataToNbt / readCustomDataFromNbt
	// = addAdditionalSaveData / readAdditionalSaveData
	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void enchantmentReforged$writePhantom(CompoundTag nbt, CallbackInfo ci) {
		if (this.enchantmentReforged$phantom) {
			nbt.putBoolean("EnchantmentReforgedPhantom", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void enchantmentReforged$readPhantom(CompoundTag nbt, CallbackInfo ci) {
		this.enchantmentReforged$phantom = nbt.getBoolean("EnchantmentReforgedPhantom");
		if (this.enchantmentReforged$phantom) {
			// 区块重新加载后同样不能掉落复制品
			((ThrownTrident) (Object) this).pickup = AbstractArrow.Pickup.DISALLOWED;
		}
	}
}
