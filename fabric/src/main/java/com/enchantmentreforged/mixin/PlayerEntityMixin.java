package com.enchantmentreforged.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.enchantmentreforged.combat.CombatFormulas;
import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.combat.HungerOwner;
import com.mojang.authlib.GameProfile;
import com.enchantmentreforged.particle.MeleeParticles;
import com.enchantmentreforged.registry.ModAttributes;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家近战伤害管线（1.20.1 原版顺序）：
 *
 * <pre>
 * base  = 属性攻击力 x (0.2 + charge^2 * 0.8)
 * bonus = 附魔加成（锋利等）x charge
 * 暴击：base *= 1.5      （原版只放大武器基础伤害，不放大附魔加成）
 * 命中：damage(source, base + bonus)
 * </pre>
 *
 * <p>需求要求 (基础 + 锋利) x 力量 x 暴击，所以这里做两件事：
 * <ol>
 *     <li>把附魔加成按暴击系数放大，等价于"暴击也放大锋利加成"；</li>
 *     <li>在最终 damage 调用处乘上力量倍率。</li>
 * </ol>
 * 全程只包装原版调用，不重写 attack()。
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	/**
	 * attack() 中 getAttackCooldownProgress(0.5F) 的结果。
	 *
	 * <p>原版读到蓄力值后立刻 resetAttackStrengthTicker()，之后再查会得到"刚重置"的值，
	 * 所以必须在调用点截获，暴击判定才能与原版一致。
	 */
	@Unique
	private float enchantmentReforged$attackCharge;

	/** 本次攻击的目标（穿刺的基岩版判定需要它） */
	@Unique
	private Entity enchantmentReforged$attackTarget;

	@Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
	private void enchantmentReforged$captureAttackTarget(Entity target, CallbackInfo ci) {
		this.enchantmentReforged$attackTarget = target;
	}

	/**
	 * 大胃袋：把饥饿值组件的"主人"绑定到玩家身上。
	 *
	 * <p>1.20.1 的 {@code HungerManager} 自己不持有玩家引用，而提升饥饿上限必须知道
	 * 胸甲上的附魔等级，所以在这里（构造完成时）把玩家登记进去。
	 */
	@Inject(
			method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;FLcom/mojang/authlib/GameProfile;)V",
			at = @At("TAIL")
	)
	private void enchantmentReforged$bindHungerOwner(World world, BlockPos pos, float yaw, GameProfile profile,
			CallbackInfo ci) {
		PlayerEntity self = enchantmentReforged$self();
		if (self.getHungerManager() instanceof HungerOwner owner) {
			owner.enchantmentReforged$setOwner(self);
		}
	}

	/**
	 * 近战粒子（普通/暴击通道）：档位 ≥1 时屏蔽原版暴击星。
	 *
	 * <p>原版暴击星是服务端生成的，直接取消这个方法即可对所有观者生效；
	 * 自定义粒子在下面的命中包装点生成（每次命中一颗）。
	 */
	@Inject(method = "addCritParticles", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$hideCritParticles(Entity target, CallbackInfo ci) {
		if (MeleeParticles.suppressesCritParticles(enchantmentReforged$self())) {
			ci.cancel();
		}
	}

	/**
	 * 近战粒子（横扫通道）：档位 ≥1 时用配置的粒子替换原版横扫弧线。
	 *
	 * <p>横扫弧线同样是服务端生成的一条粒子（在攻击者身前），这里取消它并在同一位置
	 * 生成我们选的粒子，做到"位置一致、外观可换"。
	 */
	@Inject(method = "spawnSweepAttackParticles", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$replaceSweepParticles(CallbackInfo ci) {
		PlayerEntity self = enchantmentReforged$self();
		if (MeleeParticles.suppressesSweepParticles(self)) {
			MeleeParticles.spawnOnSweep(self);
			ci.cancel();
		}
	}

	/**
	 * 近战粒子：档位 ≥1 时屏蔽原版"受伤云"。
	 *
	 * <p>原版在"本次造成伤害 &gt; 2"时会服务端生成 {@code DAMAGE_INDICATOR}，这里直接跳过这次调用
	 * （{@code attack} 内只有这一处该形态的调用，已用 javap 核对）。
	 */
	@WrapOperation(
			method = "attack(Lnet/minecraft/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/world/ServerWorld;spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I"
			)
	)
	private int enchantmentReforged$hideDamageIndicator(ServerWorld world, ParticleEffect particle,
			double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed,
			Operation<Integer> original) {
		if (MeleeParticles.suppressesCritParticles(enchantmentReforged$self())) {
			return 0;
		}
		return original.call(world, particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
	}

	@Unique
	private PlayerEntity enchantmentReforged$self() {
		return (PlayerEntity) (Object) this;
	}

	/** 截获蓄力值（原版在调用的下一行就重置了计时器） */
	@WrapOperation(
			method = "attack(Lnet/minecraft/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/player/PlayerEntity;getAttackCooldownProgress(F)F"
			)
	)
	private float enchantmentReforged$captureAttackCharge(PlayerEntity instance, float baseTime, Operation<Float> original) {
		float charge = original.call(instance, baseTime);
		this.enchantmentReforged$attackCharge = charge;
		return charge;
	}

	/**
	 * 让暴击也放大附魔加成。
	 *
	 * <p>只包装 ordinal = 0 的调用点：那是 target instanceof LivingEntity 分支内的附魔加成计算。
	 * 非生物分支的调用点永远不可能暴击（暴击要求目标是生物），无需处理。
	 */
	@WrapOperation(
			method = "attack(Lnet/minecraft/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/enchantment/EnchantmentHelper;getAttackDamage(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityGroup;)F",
					ordinal = 0
			)
	)
	private float enchantmentReforged$scaleEnchantmentBonusByCrit(ItemStack stack, EntityGroup group, Operation<Float> original) {
		float factor = CombatFormulas.critFactor(enchantmentReforged$self(), this.enchantmentReforged$attackCharge);
		// 三叉戟的穿刺按基岩版规则判定（接触水或淋雨），用替换生物类型的方式复用原版加成
		EntityGroup adjusted = group;
		if (stack.isOf(Items.TRIDENT) && this.enchantmentReforged$attackTarget instanceof LivingEntity livingTarget) {
			adjusted = EnchantmentEffects.impalingGroupFor(livingTarget, group);
		}
		return original.call(stack, adjusted) * factor;
	}

	/** 主命中：乘力量倍率 */
	@WrapOperation(
			method = "attack(Lnet/minecraft/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			)
	)
	private boolean enchantmentReforged$applyStrengthToHit(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
		PlayerEntity self = enchantmentReforged$self();
		// 力量倍率 × 死神祝福的"造成伤害"倍率 × 复仇的窗口加成（三者同乘区）
		float finalAmount = amount * CombatFormulas.strengthMultiplier(self)
				* EnchantmentEffects.deathsBlessingOutgoing(self)
				* EnchantmentEffects.revengeMultiplier(self);
		boolean hit = original.call(target, source, finalAmount);
		if (hit) {
			// 魔剑与嗜血都以本次最终伤害为基准
			float dealt = Math.max(finalAmount, 0.0F);
			EnchantmentEffects.applySpellblade(self, target, dealt);
			EnchantmentEffects.applyLifesteal(self, dealt);
			// 近战粒子：只在"主命中"的目标处生成一次（开发环境下横扫会走同一个注入点，这里排除掉）
			if (target == this.enchantmentReforged$attackTarget) {
				MeleeParticles.spawnOnHit(self, target);
				// 斩杀：只有"主命中"才判定补刀（横扫的每个目标不参与）
				EnchantmentEffects.tryExecute(self, target, self.getMainHandStack());
			}
			// 出其不意：按几率让本次攻击再结算一次（会再次触发上面的魔剑/嗜血）
			if (EnchantmentEffects.rollSurprise(self, self.getMainHandStack())) {
				// 出其不意触发时的额外粒子（类型独立、强度沿用近战强度档）
				MeleeParticles.spawnSurprise(self, target);
				if (target instanceof LivingEntity livingTarget) {
					livingTarget.timeUntilRegen = 0;
				}
				if (target.damage(source, finalAmount)) {
					EnchantmentEffects.applySpellblade(self, target, dealt);
					EnchantmentEffects.applyLifesteal(self, dealt);
				}
			}
		}
		return hit;
	}

	/**
	 * 横扫 AoE：同样乘力量倍率。
	 *
	 * <p>横扫伤害公式是 1.0 + 横扫系数 × 主命中伤害；这里让力量倍率作用于整段横扫伤害
	 * （含锋利加成），属于有意选择：力量就是"放大你打出的伤害"。
	 * 横扫与暴击互斥（暴击要求离地、横扫要求落地），所以不需要暴击系数。
	 *
	 * <p>注：生产环境的原版字节码里，主命中调用 owner 是 Entity、横扫调用 owner 是 LivingEntity；
	 * 而 Loom 在开发环境 remap 时会把两者统一成 Entity.damage，所以这里允许 0 次匹配
	 * （开发环境下横扫由上面那条 Entity.damage 注入一并覆盖）。
	 */
	@WrapOperation(
			method = "attack(Lnet/minecraft/entity/Entity;)V",
			require = 0,
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/LivingEntity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			)
	)
	private boolean enchantmentReforged$applyStrengthToSweep(LivingEntity target, DamageSource source, float amount, Operation<Boolean> original) {
		PlayerEntity self = enchantmentReforged$self();
		boolean hit = original.call(target, source, amount * CombatFormulas.strengthMultiplier(self)
				* EnchantmentEffects.deathsBlessingOutgoing(self)
				* EnchantmentEffects.revengeMultiplier(self));
		return hit;
	}
}
