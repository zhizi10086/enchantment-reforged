package com.summy.enchantment.mixin;

import com.summy.enchantment.combat.EnchantmentEffects;
import com.summy.enchantment.combat.ArrowTraits;
import com.summy.enchantment.config.ArrowParticleStyle;
import com.summy.enchantment.network.ParticlePreferences;
import com.summy.enchantment.particle.ArrowParticleCarrier;
import com.summy.enchantment.particle.ArrowParticleTrails;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让"第二支箭"也能生效。
 *
 * <p>原版 {@code LivingEntity.damage} 里有一条硬规则：受击冷却期内、这次伤害不高于上次时
 * 直接返回 false（完全免疫）。所以同一 tick 射出的第二支箭（幻影箭额外箭、多重射击的
 * 第 2/3 支）默认打不出伤害。
 *
 * <p>需要生效的箭在生成时被打了命令 tag：命中前先清掉目标的受击冷却，让原版照常结算。
 *
 * <p>另外这里让箭携带"射手选定的箭矢粒子档位"：1.20.1 的客户端拿不到"这支箭是谁射的"
 * （出生包不带 NBT），所以档位必须写在箭本体上才能让其他玩家看到。
 *
 * <p>档位借用了原版同步字节 {@code PROJECTILE_FLAGS} 里没被用到的 4 个高位
 * （暴击/无碰撞/弩箭只占低 3 位）。这样不必新增同步字段——新增字段会让原版
 * 字段的 id 整体后移，客户端进原版服务器时会读错数据。
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin implements ArrowParticleCarrier {
	@Shadow
	@Final
	private static TrackedData<Byte> PROJECTILE_FLAGS;
	/** 原版"已插在方块里"标记（protected 字段，没有对应的 getter，用 @Shadow 读） */
	@Shadow
	protected boolean inGround;

	/** 档位占用的 4 个高位（8 / 16 / 32 / 64） */
	@Unique
	private static final int ENCHANTMENT_REFORGED$STYLE_MASK = 8 | 16 | 32 | 64;
	@Unique
	private static final int ENCHANTMENT_REFORGED$STYLE_SHIFT = 3;

	/**
	 * 用射手构造的投掷物（弓、弩、三叉戟等）继承射手的粒子档位。
	 *
	 * <p>在构造函数末尾写入，实体出生包就会把该值带给所有客户端；
	 * 客户端侧这张档位表恒为空，所以这里在客户端不生效。
	 */
	@Inject(
			method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;)V",
			at = @At("TAIL")
	)
	private void summyEnchantment$inheritShooterParticleStyle(EntityType<?> type, LivingEntity shooter,
			World world, CallbackInfo ci) {
		if (shooter == null) {
			return;
		}
		int style = ParticlePreferences.styleOf(shooter.getUuid());
		if (style != ArrowParticleStyle.UNSET) {
			summyEnchantment$setParticleStyle(style);
		}
	}

	@Override
	public int summyEnchantment$getParticleStyle() {
		int code = (summyEnchantment$rawFlags() & ENCHANTMENT_REFORGED$STYLE_MASK)
				>> ENCHANTMENT_REFORGED$STYLE_SHIFT;
		// 0 表示这支箭没有携带射手档位
		return code == 0 ? ArrowParticleStyle.UNSET : code - 1;
	}

	@Override
	public void summyEnchantment$setParticleStyle(int style) {
		int code = style == ArrowParticleStyle.UNSET
				? 0
				: Math.max(0, Math.min(ArrowParticleStyle.COUNT - 1, style)) + 1;
		int flags = summyEnchantment$rawFlags() & ~ENCHANTMENT_REFORGED$STYLE_MASK;
		flags |= (code << ENCHANTMENT_REFORGED$STYLE_SHIFT) & ENCHANTMENT_REFORGED$STYLE_MASK;
		((Entity) (Object) this).getDataTracker().set(PROJECTILE_FLAGS, (byte) flags);
	}

	/** 原版同步字节的原始值（无符号） */
	@Unique
	private int summyEnchantment$rawFlags() {
		return ((Entity) (Object) this).getDataTracker().get(PROJECTILE_FLAGS) & 0xFF;
	}

	@Inject(method = "onEntityHit", at = @At("HEAD"))
	private void summyEnchantment$ensureExtraHit(EntityHitResult hitResult, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (EnchantmentEffects.isExtraHitArrow(self) && hitResult.getEntity() instanceof LivingEntity target) {
			target.timeUntilRegen = 0;
		}
	}

	/** 远距离显示：服务端给开启该开关的观者代发轨迹粒子（客户端侧由本地渲染负责） */
	@Inject(method = "tick", at = @At("TAIL"))
	private void summyEnchantment$serverParticleTrail(CallbackInfo ci) {
		ArrowParticleTrails.tickServerTrail((PersistentProjectileEntity) (Object) this);
	}

	/**
	 * 轻盈（1.3.7）：把原版箭的重力常量按"这支箭的轻盈等级"缩放。
	 *
	 * <p>已用 javap 核对：{@code tick} 里真正生效的重力是那个 double 常量
	 * {@code 0.05000000074505806}（同名的 float 局部是死代码）；两端都读各自的表，
	 * 等级由服务端在箭生成时通过 S2C 包同步，因此射手与旁观者看到的弹道一致。
	 */
	@ModifyConstant(method = "tick", constant = @Constant(doubleValue = 0.05000000074505806D))
	private double summyEnchantment$lightnessGravity(double gravity) {
		return gravity * ArrowTraits.gravityFactor((PersistentProjectileEntity) (Object) this);
	}

	/**
	 * 死亡之眼（1.3.7）：整箭落空结算 + 实体移除时清表。
	 *
	 * <p><b>硬规则</b>：只有"整段飞行未命中任何生物"且已插地 / 已移除的箭才掷清空概率；
	 * 命中过生物（包含穿透一箭多目标）后落地一律不掷、不清空。
	 */
	@Inject(method = "tick", at = @At("TAIL"))
	private void summyEnchantment$settleDeathEye(CallbackInfo ci) {
		PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
		if (self.getWorld().isClient) {
			return;
		}
		if (ArrowTraits.hasDeathEye(self) && !ArrowTraits.hitLiving(self.getId())
				&& (this.inGround || self.isRemoved()) && ArrowTraits.markSettled(self.getId())) {
			ArrowTraits.recordMiss(self);
		}
		if (self.isRemoved()) {
			ArrowTraits.forget(self.getId());
		}
	}

	/**
	 * 档位补写（1.3.5）：覆盖"先构造出实体、之后才 {@code setOwner(player)}"的自定义发射路径。
	 *
	 * <p>本树原本只在构造器末尾继承档位，所以那条路径上的箭不携带档位、整条粒子链路都不介入。
	 * 这里在服务端前 200 tick 内做一次幂等补写：箭类 + 档位仍未知 + owner 是已登记档位的玩家 → 写入一次。
	 * 写入后 {@code getParticleStyle() != UNSET}，后续 tick 自然短路。
	 */
	@Inject(method = "tick", at = @At("HEAD"))
	private void summyEnchantment$backfillShooterStyle(CallbackInfo ci) {
		PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
		if (self.getWorld().isClient || self.age > 200) {
			return;
		}
		if (summyEnchantment$getParticleStyle() != ArrowParticleStyle.UNSET) {
			return;
		}
		if (!(self.getOwner() instanceof ServerPlayerEntity shooter)) {
			return;
		}
		if (!ArrowParticleTrails.isArrow(self)) {
			return;
		}
		int style = ParticlePreferences.styleOf(shooter.getUuid());
		if (style != ArrowParticleStyle.UNSET) {
			summyEnchantment$setParticleStyle(style);
		}
	}

	/**
	 * 保险：标记过的箭若这次伤害仍被免疫（返回 false），清掉冷却重试一次。
	 *
	 * <p>原版只有伤害成功才会调用 {@code onHit}，而药水效果正是在那里施加的；
	 * 重试一次能保证"伤害 + 药水效果"都落地（多重射击每支箭都带药水箭时尤其重要）。
	 */
	@WrapOperation(
			method = "onEntityHit",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			)
	)
	private boolean summyEnchantment$retryExtraHit(Entity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
		// 死亡之眼（1.3.7）：命中时先用"当前层数"放大本次伤害（穿透一箭多目标时每个目标各结算一次）
		float adjusted = amount * ArrowTraits.deathEyeMultiplier(self);
		boolean hit = original.call(target, source, adjusted);
		if (!hit && target instanceof LivingEntity living && EnchantmentEffects.isExtraHitArrow(self)) {
			living.timeUntilRegen = 0;
			hit = original.call(target, source, adjusted);
		}
		if (hit && target instanceof LivingEntity) {
			// 命中生物后才加层（先算伤害再加层），并清掉连续落空计数
			ArrowTraits.recordHit(self);
		}
		return hit;
	}
}
