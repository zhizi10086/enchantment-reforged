package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.CombatFormulas;
import com.enchantmentreforged.combat.EnchantmentEffects;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 原版力量来源重定向。
 *
 * <p>Fabric API 0.92.7 没有可用的"效果添加"事件（不存在 ServerMobEffectEvents），
 * 但原版所有力量来源最终都汇聚到这两个方法：
 * <ul>
 *     <li>addStatusEffect(StatusEffectInstance, Entity)：药水、信标、谜之炖菜、/effect 命令；</li>
 *     <li>setStatusEffect(StatusEffectInstance, Entity)（原版 forceAddEffect）：强制覆盖式添加。</li>
 * </ul>
 * 因此只改写"即将被放进实体身上的实例"，不替换任何原版逻辑。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** 死者之心的治疗缩放标记：避免"取消后重新回血"再次被缩放 */
	@Unique
	private boolean enchantmentReforged$healRescaled;

	/**
	 * 灵动步伐：按几率完全无效化这次伤害。
	 *
	 * <p>放在 HEAD 且直接取消：虚空、指令等所有来源都能闪避，被闪避时连
	 * 无敌帧、击退与受伤动画都不会产生。
	 */
	@Inject(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$nimbleStepsDodge(DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (EnchantmentEffects.rollNimbleSteps(self)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * 复仇：受伤且伤害真的落地时刷新"3 秒内伤害提高"的窗口。
	 */
	@Inject(method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("RETURN"))
	private void enchantmentReforged$markRevengeWindow(DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			EnchantmentEffects.onDamaged((LivingEntity) (Object) this);
		}
	}

	/**
	 * 速食：缩短食物的使用时长（只动写进 {@code itemUseTimeLeft} 的那个值）。
	 *
	 * <p>调用点里 {@code activeItemStack} 已经赋值，所以这里用"当前正在使用的物品"判断，
	 * 双端执行同一段逻辑，进食节奏一致。
	 */
	@ModifyExpressionValue(
			method = "setCurrentHand(Lnet/minecraft/util/Hand;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getMaxUseTime()I")
	)
	private int enchantmentReforged$quickEatUseTicks(int original) {
		LivingEntity self = (LivingEntity) (Object) this;
		ItemStack active = self.getActiveItem();
		return EnchantmentEffects.quickEatUseTicks(self, active, original);
	}

	@ModifyVariable(
			method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
			at = @At("HEAD"),
			argsOnly = true
	)
	private StatusEffectInstance enchantmentReforged$redirectStrengthOnAdd(StatusEffectInstance instance) {
		return CombatFormulas.redirectVanillaStrength(instance);
	}

	@ModifyVariable(
			method = "setStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)V",
			at = @At("HEAD"),
			argsOnly = true
	)
	private StatusEffectInstance enchantmentReforged$redirectStrengthOnSet(StatusEffectInstance instance) {
		return CombatFormulas.redirectVanillaStrength(instance);
	}

	/** 死神祝福：受到伤害倍率（与其它受伤增减同乘区） */
	@ModifyVariable(
			method = "damage(Lnet/minecraft/entity/damage/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true
	)
	private float enchantmentReforged$deathsBlessingIncoming(float amount) {
		LivingEntity self = (LivingEntity) (Object) this;
		return amount * EnchantmentEffects.deathsBlessingIncoming(self);
	}

	/**
	 * 恶咒：直接在所有"设置生命值"的入口裁剪。
	 *
	 * <p>回血内部就是调 {@code setHealth}，在这里把超过 1 的值改成 1，
	 * 就不会出现"血量先升一帧、下一 tick 再压回"造成的红闪。
	 */
	@ModifyVariable(method = "setHealth(F)V", at = @At("HEAD"), argsOnly = true)
	private float enchantmentReforged$calamityClampHealth(float value) {
		if (value <= 1.0F) {
			return value;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		// 构造期间（age == 0）装备栏还没建立，查附魔会 NPE（例如 Carpet/GCA 生成假玩家时），
		// 这里直接跳过；实体进入世界后由 tick 里的兜底逻辑负责压血。
		if (self.age <= 0) {
			return value;
		}
		try {
			if (EnchantmentEffects.isCalamityActive(self)) {
				return 1.0F;
			}
		} catch (Exception ignored) {
			// 实体状态还不完整时不做任何处理
		}
		return value;
	}

	/**
	 * 生命护盾：回复生命时按"实际回复量"累积护盾。
	 *
	 * <p>在 HEAD 处取回复前的血量，才能算出真正生效的回复量（满血时 heal 不产生护盾）。
	 *
	 * <p>生命修补（诅咒）：这次治疗若被用来修装备耐久，就把整段治疗取消掉
	 * （不回血、也不触发生命护盾等"基于回血"的联动）；没有可修的装备时照常回血。
	 * 满血时"实际回复量"为 0，但生命修补改为按<b>本次治疗量</b>换算耐久（见下），
	 * 于是满血喝治疗药水也能修装备，只是照旧不回血。
	 *
	 * <p>死者之心：先按配置把治疗量削掉一部分（默认 -50%），再走上面的流程——
	 * 用"取消后按缩放值重新回血"的写法，保证后面的每一处计算口径一致。
	 */
	@Inject(method = "heal(F)V", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$onHeal(float amount, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		// 死者之心：缩放治疗量后重新进入本方法（第二次不再缩放，避免递归缩放）
		if (!this.enchantmentReforged$healRescaled) {
			float factor = EnchantmentEffects.deadMansHeartHealFactor(self);
			if (factor < 1.0F) {
				this.enchantmentReforged$healRescaled = true;
				try {
					self.heal(amount * factor);
				} finally {
					this.enchantmentReforged$healRescaled = false;
				}
				ci.cancel();
				return;
			}
		}
		float health = self.getHealth();
		if (health <= 0.0F) {
			return;
		}
		float healed = Math.min(amount, self.getMaxHealth() - health);
		// 生命护盾/恶咒联动用"实际回复量"；生命修补用"本次治疗量"——
		// 满血时实际回复量是 0，此时按原始治疗量换算，满血也能修耐久。
		float mendingFuel = healed > 0.0F ? healed : amount;
		// 生命修补：修到耐久就吞掉这次治疗
		if (mendingFuel > 0.0F && EnchantmentEffects.repairWithLifeMending(self, mendingFuel) > 0) {
			ci.cancel();
			return;
		}
		if (healed <= 0.0F) {
			// 满血且没修到任何耐久：与之前一样，不累积护盾、不触发恶咒压血
			return;
		}
		EnchantmentEffects.onHeal(self, healed);
		// 恶咒：同一个 tick 内就把血量压回 1，避免客户端看到"回血又掉血"的闪烁
		EnchantmentEffects.applyCalamity(self);
	}

	/**
	 * 不死者加护：在护甲与保护附魔减伤之后、伤害吸收之前扣掉固定的点数（手持不死图腾时生效）。
	 *
	 * <p>{@code modifyAppliedDamage} 正好是"护甲/保护都算完"的那一步，返回值会被
	 * 立刻用于扣吸收值与血量，所以这里改返回值就是"最终伤害 -N"。
	 */
	@ModifyReturnValue(method = "modifyAppliedDamage(Lnet/minecraft/entity/damage/DamageSource;F)F", at = @At("RETURN"))
	private float enchantmentReforged$undyingGraceReduction(float original) {
		LivingEntity self = (LivingEntity) (Object) this;
		float reduction = EnchantmentEffects.undyingGraceReduction(self);
		return reduction <= 0.0F ? original : Math.max(0.0F, original - reduction);
	}

	/**
	 * 每 tick 维护装备附魔带来的属性（生命提升、迅捷打击）与恶咒的锁血。
	 *
	 * <p>恶咒刻意放在这里而非伤害流程：回血逻辑（含生命护盾）先正常执行，
	 * 随后血量被压回 1，因此"基于回血"的联动依然成立。
	 */
	@Inject(method = "tick()V", at = @At("TAIL"))
	private void enchantmentReforged$tickEquipmentEffects(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		// 客户端的属性完全由服务端同步，不必再本地算一遍：
		// 这一块是所有可见实体每 tick 都要跑的，放在客户端纯属冗余开销。
		if (self.getWorld().isClient) {
			return;
		}
		EnchantmentEffects.updateEquipmentAttributes(self);
		EnchantmentEffects.applyCalamity(self);
		// 生命护盾：数值存在属性里，所以每 tick 的维护改由这里驱动（内部有快速早退）
		EnchantmentEffects.tickLifeShield(self);
	}
}
