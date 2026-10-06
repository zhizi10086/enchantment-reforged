package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.combat.PhantomTrident;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.EntityHitResult;
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
 * {@code EntityGroup.AQUATIC} 或 {@code DEFAULT}，从而复用原版穿刺的数值与叠加顺序。
 * 三叉戟上只有穿刺会用到生物类型，所以替换不会影响其它附魔。
 *
 * <p>此外这里还承载两件事：投掷命中结算魔剑/嗜血（等级取自三叉戟本体），
 * 以及忠诚改版生成的"假三叉戟"标记（飞回主人时只消失、不复制物品）。
 */
@Mixin(TridentEntity.class)
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
	private ItemStack tridentStack;

	/** 原版记录忠诚等级的同步数据（客户端也能读到，用来判断"假三叉戟还在飞"） */
	@Shadow
	private static TrackedData<Byte> LOYALTY;

	@Override
	public void enchantmentReforged$setPhantom(boolean phantom) {
		this.enchantmentReforged$phantom = phantom;
	}

	@Override
	public boolean enchantmentReforged$isLoyal() {
		return ((TridentEntity) (Object) this).getDataTracker().get(LOYALTY) > 0;
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
		TridentEntity self = (TridentEntity) (Object) this;
		// 只在服务端清除，客户端等服务端的移除包即可
		if (!self.getWorld().isClient
				&& this.enchantmentReforged$flightTicks > EnchantmentEffects.PHANTOM_CLEANUP_TICKS
				&& !self.isRemoved()) {
			self.discard();
		}
	}

	@Inject(method = "onEntityHit", at = @At("HEAD"))
	private void enchantmentReforged$captureTarget(EntityHitResult hitResult, CallbackInfo ci) {
		if (hitResult.getEntity() instanceof LivingEntity living) {
			this.enchantmentReforged$impalingTarget = living;
		}
	}

	@ModifyArg(
			method = "onEntityHit",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/enchantment/EnchantmentHelper;getAttackDamage(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityGroup;)F"
			),
			index = 1
	)
	private EntityGroup enchantmentReforged$impalingGroup(EntityGroup group) {
		return EnchantmentEffects.impalingGroupFor(this.enchantmentReforged$impalingTarget, group);
	}

	/**
	 * 投掷命中的魔剑 / 嗜血。
	 *
	 * <p>近战走 {@code PlayerEntityMixin} 的主手判定，投掷时主手可能是空的，
	 * 所以这里用三叉戟本体上的附魔等级单独结算一次，效果与近战一致。
	 */
	@WrapOperation(
			method = "onEntityHit",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			)
	)
	private boolean enchantmentReforged$applyThrownEnchantments(Entity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		boolean hit = original.call(target, source, amount);
		if (hit) {
			Entity owner = ((TridentEntity) (Object) this).getOwner();
			if (owner instanceof LivingEntity livingOwner) {
				float dealt = Math.max(amount, 0.0F);
				EnchantmentEffects.applySpellblade(livingOwner, this.tridentStack, target, dealt);
				EnchantmentEffects.applyLifesteal(livingOwner, this.tridentStack, dealt);
				// 斩杀：投掷三叉戟同样可以补刀（近战由 PlayerEntityMixin 处理）
				EnchantmentEffects.tryExecute(livingOwner, target, this.tridentStack);
				// 出其不意：投掷三叉戟同样可以让这次攻击再结算一次
				if (EnchantmentEffects.rollSurprise(livingOwner, this.tridentStack)) {
					if (target instanceof LivingEntity livingTarget) {
						livingTarget.timeUntilRegen = 0;
					}
					if (target.damage(source, amount)) {
						EnchantmentEffects.applySpellblade(livingOwner, this.tridentStack, target, dealt);
						EnchantmentEffects.applyLifesteal(livingOwner, this.tridentStack, dealt);
					}
				}
			}
		}
		return hit;
	}

	/** 假三叉戟飞回主人身上：让它消失即可，不能再复制一份（本体已经在物品栏里） */
	@Inject(method = "tryPickup", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$pickupPhantom(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
		if (this.enchantmentReforged$phantom
				&& player == ((TridentEntity) (Object) this).getOwner()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void enchantmentReforged$writePhantom(NbtCompound nbt, CallbackInfo ci) {
		if (this.enchantmentReforged$phantom) {
			nbt.putBoolean("EnchantmentReforgedPhantom", true);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void enchantmentReforged$readPhantom(NbtCompound nbt, CallbackInfo ci) {
		this.enchantmentReforged$phantom = nbt.getBoolean("EnchantmentReforgedPhantom");
		if (this.enchantmentReforged$phantom) {
			// 区块重新加载后同样不能掉落复制品
			((TridentEntity) (Object) this).pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
		}
	}
}
