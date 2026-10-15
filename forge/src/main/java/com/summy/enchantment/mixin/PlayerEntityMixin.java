package com.summy.enchantment.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.summy.enchantment.combat.CombatFormulas;
import com.summy.enchantment.combat.ElectricArc;
import com.summy.enchantment.combat.EnchantmentEffects;
import com.summy.enchantment.combat.HungerOwner;
import com.summy.enchantment.compat.SummyReliquaryCompat;
import com.summy.enchantment.particle.MeleeParticles;
import com.summy.enchantment.registry.ModAttributes;
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

import java.util.HashSet;
import java.util.Set;

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
	private float summyEnchantment$attackCharge;

	/** 本次攻击的目标（穿刺的基岩版判定需要它） */
	@Unique
	private Entity summyEnchantment$attackTarget;

	/**
	 * 本次挥砍已经伤害过的实体 id（主命中 + 横扫受害者）。
	 *
	 * <p>电刀要排除"这一刀已经打过的目标"，所以收集在这里、等 {@code attack} 结束时一起交给
	 * {@code ElectricArc.start}——启动放到末尾是因为横扫发生在主命中之后，中途启动就漏掉横扫受害者了。
	 */
	@Unique
	private final Set<Integer> summyEnchantment$swingHitIds = new HashSet<>();
	/** 本次挥砍的主命中是否落地（落地方为电刀启动条件） */
	@Unique
	private boolean summyEnchantment$mainHitLanded;
	/** 本次挥砍主命中的攻击侧金额（电刀的弹射基数） */
	@Unique
	private float summyEnchantment$mainHitAmount;

	@Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
	private void summyEnchantment$captureAttackTarget(Entity target, CallbackInfo ci) {
		this.summyEnchantment$attackTarget = target;
		this.summyEnchantment$swingHitIds.clear();
		this.summyEnchantment$mainHitLanded = false;
		this.summyEnchantment$mainHitAmount = 0.0F;
	}

	/** 大胃袋的"主人"是否已登记过（每个玩家实例只登记一次） */
	@Unique
	private boolean summyEnchantment$hungerOwnerBound;

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
	private void summyEnchantment$bindHungerOwner(CallbackInfo ci) {
		if (this.summyEnchantment$hungerOwnerBound) {
			return;
		}
		this.summyEnchantment$hungerOwnerBound = true;
		Player self = summyEnchantment$self();
		if (self.getFoodData() instanceof HungerOwner owner) {
			owner.summyEnchantment$setOwner(self);
		}
	}

	/**
	 * 近战粒子（普通/暴击通道）：档位 ≥1 时屏蔽原版暴击星。
	 *
	 * <p>原版暴击星是服务端生成的，直接取消这个方法即可对所有观者生效；
	 * 自定义粒子在下面的命中包装点生成（每次命中一颗）。
	 */
	@Inject(method = "crit", at = @At("HEAD"), cancellable = true)
	private void summyEnchantment$hideCritParticles(Entity target, CallbackInfo ci) {
		if (MeleeParticles.suppressesCritParticles(summyEnchantment$self())) {
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
	private void summyEnchantment$replaceSweepParticles(CallbackInfo ci) {
		Player self = summyEnchantment$self();
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
	private int summyEnchantment$hideDamageIndicator(ServerLevel world, ParticleOptions particle,
			double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed,
			Operation<Integer> original) {
		if (MeleeParticles.suppressesCritParticles(summyEnchantment$self())) {
			return 0;
		}
		return original.call(world, particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
	}

	@Unique
	private Player summyEnchantment$self() {
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
	private float summyEnchantment$captureAttackCharge(Player instance, float baseTime, Operation<Float> original) {
		float charge = original.call(instance, baseTime);
		this.summyEnchantment$attackCharge = charge;
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
	private float summyEnchantment$scaleEnchantmentBonusByCrit(ItemStack stack, MobType group, Operation<Float> original) {
		float factor = CombatFormulas.critFactor(summyEnchantment$self(), this.summyEnchantment$attackCharge);
		// 三叉戟的穿刺按基岩版规则判定（接触水或淋雨），用替换生物类型的方式复用原版加成
		MobType adjusted = group;
		if (stack.is(Items.TRIDENT) && this.summyEnchantment$attackTarget instanceof LivingEntity livingTarget) {
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
	private boolean summyEnchantment$applyStrengthToHit(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
		Player self = summyEnchantment$self();
		// 力量倍率 × 死神祝福的"造成伤害"倍率 × 复仇的窗口加成（三者同乘区）
		// 1.3.7 起"死神祝福·造成侧"统一在受伤侧按攻击者结算，这里只留力量 × 复仇
		// 1.3.8 起藏剑的蓄力加成也并进这个乘区，但它只作用于"本刀的主命中"（横扫目标不算）
		boolean mainHit = target == this.summyEnchantment$attackTarget;
		float bladeBonus = mainHit ? EnchantmentEffects.hiddenBladeBonus(self) : 0.0F;
		float finalAmount = amount * CombatFormulas.strengthMultiplier(self)
				* EnchantmentEffects.revengeMultiplier(self) * (1.0F + bladeBonus);
		// 藏剑补强（1.3.8）：主命中且带藏剑加成时开"穿透窗口"——这一刀无视目标闪避、
		// 并按充能进度穿透护甲（只削护甲层，保护/抗性/吸收照常）
		boolean pierced = mainHit && bladeBonus > 0.0F;
		float pierceRatio = pierced ? EnchantmentEffects.hiddenBladeChargeProgress(self) : 0.0F;
		boolean hit;
		if (pierceRatio > 0.0F) {
			EnchantmentEffects.pushHiddenBladePierce(pierceRatio);
			try {
				hit = original.call(target, source, finalAmount);
			} finally {
				EnchantmentEffects.popHiddenBladePierce();
			}
		} else {
			hit = original.call(target, source, finalAmount);
		}
		if (hit) {
			// 记下"这一刀伤害过的实体"：电刀会排除它们（主命中 + dev 环境下被合流过来的横扫）
			this.summyEnchantment$swingHitIds.add(target.getId());
		}
		// 电刀的主命中信息先记下来，等 attack 结束（横扫也结算完）再启动链式弹射
		if (mainHit && hit) {
			this.summyEnchantment$mainHitLanded = true;
			this.summyEnchantment$mainHitAmount = finalAmount;
		}
		// 命中后效果统一走 SummyReliquaryCompat.settleHit：与 SR 兼容路径共用同一套口径与同一份诊断日志。
		// （魔剑/嗜血恒定；主命中粒子与斩杀只在"主命中"；出其不意两条路径都掷）
		SummyReliquaryCompat.settleHit("melee", self, self.getMainHandItem(), target, source,
				amount, finalAmount, hit, true, true, target == this.summyEnchantment$attackTarget);
		return hit;
	}

	/**
	 * 藏剑：一次近战挥砍结束时清空蓄力并进入冷却（含打空、砍空气）。
	 *
	 * <p>放在 {@code attack} 的 TAIL：此时主命中的加成已经被读取过，清空不会影响本刀伤害；
	 * 而"打空也清空"正是这一附魔的代价。
	 */
	@Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
	private void summyEnchantment$consumeHiddenBlade(Entity target, CallbackInfo ci) {
		Player self = summyEnchantment$self();
		EnchantmentEffects.onHiddenBladeSwing(self);
		// 电刀：主命中落地才启动；起始排除集合 = 本次挥砍伤害过的全部目标（含横扫受害者）
		if (this.summyEnchantment$mainHitLanded && this.summyEnchantment$attackTarget != null) {
			ElectricArc.start(self, this.summyEnchantment$attackTarget, self.getMainHandItem(),
					this.summyEnchantment$mainHitAmount, new HashSet<>(this.summyEnchantment$swingHitIds));
		}
		this.summyEnchantment$swingHitIds.clear();
		this.summyEnchantment$mainHitLanded = false;
		this.summyEnchantment$mainHitAmount = 0.0F;
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
	private boolean summyEnchantment$applyStrengthToSweep(LivingEntity target, DamageSource source, float amount, Operation<Boolean> original) {
		Player self = summyEnchantment$self();
		boolean hit = original.call(target, source, amount * CombatFormulas.strengthMultiplier(self)
				* EnchantmentEffects.revengeMultiplier(self));
		if (hit) {
			// 横扫受害者也要排除在电刀之外（它们已经被这一刀打过了）
			this.summyEnchantment$swingHitIds.add(target.getId());
		}
		return hit;
	}
}
