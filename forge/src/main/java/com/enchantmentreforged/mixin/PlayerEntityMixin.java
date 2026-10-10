package com.enchantmentreforged.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.enchantmentreforged.combat.CombatFormulas;
import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.combat.HungerOwner;
import com.enchantmentreforged.compat.SummyReliquaryCompat;
import com.enchantmentreforged.particle.MeleeParticles;
import com.enchantmentreforged.registry.ModAttributes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
@Mixin(Player.class)
public abstract class PlayerEntityMixin {
	/**
	 * attack() 中 getAttackStrengthScale(0.5F) 的结果。
	 *
	 * <p>原版读到蓄力值后立刻 resetAttackStrengthTicker()，之后再查会得到"刚重置"的值，
	 * 所以必须在调用点截获，暴击判定才能与原版一致。
	 */
	@Unique
	private float enchantmentReforged$attackCharge;

	/** 本次攻击的目标（穿刺的基岩版判定需要它） */
	@Unique
	private Entity enchantmentReforged$attackTarget;

	@Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
	private void enchantmentReforged$captureAttackTarget(Entity target, CallbackInfo ci) {
		this.enchantmentReforged$attackTarget = target;
	}

	/** 大胃袋的"主人"是否已登记过（每个玩家实例只登记一次） */
	@Unique
	private boolean enchantmentReforged$hungerOwnerBound;

	/**
	 * 大胃袋：把饥饿值组件的"主人"绑定到玩家身上。
	 *
	 * <p>1.20.1 的 {@code FoodData} 自己不持有玩家引用，而提升饥饿上限必须知道
	 * 胸甲上的附魔等级，所以要把玩家登记进去。
	 *
	 * <p>刻意不用构造器注入：Player 的构造器在 Forge/Connector 环境下容易出现额外的
	 * 重载与映射反查问题（实测会在生产客户端注入失败）。改成首个 tick 惰性登记，
	 * {@code FoodData} 的实际使用都发生在玩家 tick 之后，行为完全等价；
	 * 服务端玩家与客户端 LocalPlayer 都会各自完成一次登记。
	 */
	@Inject(method = "tick", at = @At("HEAD"))
	private void enchantmentReforged$bindHungerOwner(CallbackInfo ci) {
		if (this.enchantmentReforged$hungerOwnerBound) {
			return;
		}
		this.enchantmentReforged$hungerOwnerBound = true;
		Player self = enchantmentReforged$self();
		if (self.getFoodData() instanceof HungerOwner owner) {
			owner.enchantmentReforged$setOwner(self);
		}
	}

	/**
	 * 近战粒子（普通/暴击通道）：档位 ≥1 时屏蔽原版暴击星。
	 *
	 * <p>原版暴击星是服务端生成的，直接取消这个方法即可对所有观者生效；
	 * 自定义粒子在下面的命中包装点生成（每次命中一颗）。
	 */
	@Inject(method = "crit", at = @At("HEAD"), cancellable = true)
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
	@Inject(method = "sweepAttack", at = @At("HEAD"), cancellable = true)
	private void enchantmentReforged$replaceSweepParticles(CallbackInfo ci) {
		Player self = enchantmentReforged$self();
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
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"
			)
	)
	private int enchantmentReforged$hideDamageIndicator(ServerLevel world, ParticleOptions particle,
			double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed,
			Operation<Integer> original) {
		if (MeleeParticles.suppressesCritParticles(enchantmentReforged$self())) {
			return 0;
		}
		return original.call(world, particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
	}

	@Unique
	private Player enchantmentReforged$self() {
		return (Player) (Object) this;
	}

	/** 截获蓄力值（原版在调用的下一行就重置了计时器） */
	@WrapOperation(
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/player/Player;getAttackStrengthScale(F)F"
			)
	)
	private float enchantmentReforged$captureAttackCharge(Player instance, float baseTime, Operation<Float> original) {
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
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getDamageBonus(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/MobType;)F",
					ordinal = 0
			)
	)
	private float enchantmentReforged$scaleEnchantmentBonusByCrit(ItemStack stack, MobType group, Operation<Float> original) {
		float factor = CombatFormulas.critFactor(enchantmentReforged$self(), this.enchantmentReforged$attackCharge);
		// 三叉戟的穿刺按基岩版规则判定（接触水或淋雨），用替换生物类型的方式复用原版加成
		MobType adjusted = group;
		if (stack.is(Items.TRIDENT) && this.enchantmentReforged$attackTarget instanceof LivingEntity livingTarget) {
			adjusted = EnchantmentEffects.impalingGroupFor(livingTarget, group);
		}
		return original.call(stack, adjusted) * factor;
	}

	/** 主命中：乘力量倍率 */
	@WrapOperation(
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			)
	)
	private boolean enchantmentReforged$applyStrengthToHit(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
		Player self = enchantmentReforged$self();
		// 力量倍率 × 死神祝福的"造成伤害"倍率 × 复仇的窗口加成（三者同乘区）
		float finalAmount = amount * CombatFormulas.strengthMultiplier(self)
				* EnchantmentEffects.deathsBlessingOutgoing(self)
				* EnchantmentEffects.revengeMultiplier(self);
		boolean hit = original.call(target, source, finalAmount);
		// 命中后效果统一走 SummyReliquaryCompat.settleHit：与 SR 兼容路径共用同一套口径与同一份诊断日志。
		// （魔剑/嗜血恒定；主命中粒子与斩杀只在"主命中"；出其不意两条路径都掷）
		SummyReliquaryCompat.settleHit("melee", self, self.getMainHandItem(), target, source,
				amount, finalAmount, hit, true, true, target == this.enchantmentReforged$attackTarget);
		return hit;
	}

	/**
	 * 横扫 AoE：同样乘力量倍率。
	 *
	 * <p>横扫伤害公式是 1.0 + 横扫系数 × 主命中伤害；这里让力量倍率作用于整段横扫伤害
	 * （含锋利加成），属于有意选择：力量就是"放大你打出的伤害"。
	 * 横扫与暴击互斥（暴击要求离地、横扫要求落地），所以不需要暴击系数。
	 *
	 * <p>注：原版字节码里主命中调用的 owner 是 Entity、横扫调用的 owner 是 LivingEntity，
	 * 两条 WrapOperation 各自精确匹配一处，因此这里不再放宽容忍度（匹配不到即报错）。
	 */
	@WrapOperation(
			method = "attack(Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			)
	)
	private boolean enchantmentReforged$applyStrengthToSweep(LivingEntity target, DamageSource source, float amount, Operation<Boolean> original) {
		Player self = enchantmentReforged$self();
		boolean hit = original.call(target, source, amount * CombatFormulas.strengthMultiplier(self)
				* EnchantmentEffects.deathsBlessingOutgoing(self)
				* EnchantmentEffects.revengeMultiplier(self));
		return hit;
	}
}
