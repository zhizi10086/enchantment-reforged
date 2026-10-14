package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.combat.ArrowTraits;
import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.network.ParticlePreferences;
import com.enchantmentreforged.particle.ArrowParticleCarrier;
import com.enchantmentreforged.particle.ArrowParticleTrails;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;
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
 * <p>档位借用了原版同步字节 {@code ID_FLAGS} 里没被用到的 4 个高位
 * （暴击/无碰撞/弩箭只占低 3 位）。这样不必新增同步字段——新增字段会让原版
 * 字段的 id 整体后移，客户端进原版服务器时会读错数据。
 */
@Mixin(AbstractArrow.class)
public abstract class PersistentProjectileEntityMixin implements ArrowParticleCarrier {
	@Shadow
	@Final
	private static EntityDataAccessor<Byte> ID_FLAGS;
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
	 * <p>原版几个构造函数最后都会调用 {@code setOwner}，在这里写入同样能赶上实体出生包；
	 * 相比注入 {@code <init>}，注入普通方法不会踩到 Mixin 对多重载构造函数的匹配问题。
	 * 客户端侧这张档位表恒为空，所以这里只在服务端写入。
	 */
	@Inject(
			method = "setOwner",
			at = @At("TAIL"),
			remap = true
	)
	private void enchantmentReforged$inheritShooterParticleStyle(Entity owner, CallbackInfo ci) {
		if (owner == null) {
			return;
		}
		if (((Entity) (Object) this).level().isClientSide) {
			return;
		}
		int style = ParticlePreferences.styleOf(owner.getUUID());
		if (style != ArrowParticleStyle.UNSET) {
			enchantmentReforged$setParticleStyle(style);
		}
	}

	@Override
	public int enchantmentReforged$getParticleStyle() {
		int code = (enchantmentReforged$rawFlags() & ENCHANTMENT_REFORGED$STYLE_MASK)
				>> ENCHANTMENT_REFORGED$STYLE_SHIFT;
		// 0 表示这支箭没有携带射手档位
		return code == 0 ? ArrowParticleStyle.UNSET : code - 1;
	}

	@Override
	public void enchantmentReforged$setParticleStyle(int style) {
		int code = style == ArrowParticleStyle.UNSET
				? 0
				: Math.max(0, Math.min(ArrowParticleStyle.COUNT - 1, style)) + 1;
		int flags = enchantmentReforged$rawFlags() & ~ENCHANTMENT_REFORGED$STYLE_MASK;
		flags |= (code << ENCHANTMENT_REFORGED$STYLE_SHIFT) & ENCHANTMENT_REFORGED$STYLE_MASK;
		((Entity) (Object) this).getEntityData().set(ID_FLAGS, (byte) flags);
	}

	/** 原版同步字节的原始值（无符号） */
	@Unique
	private int enchantmentReforged$rawFlags() {
		return ((Entity) (Object) this).getEntityData().get(ID_FLAGS) & 0xFF;
	}

	// 官方映射里 Yarn 的 onEntityHit = onHitEntity
	@Inject(method = "onHitEntity", at = @At("HEAD"))
	private void enchantmentReforged$ensureExtraHit(EntityHitResult hitResult, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (EnchantmentEffects.isExtraHitArrow(self) && hitResult.getEntity() instanceof LivingEntity target) {
			target.invulnerableTime = 0;
		}
	}

	/** 远距离显示：服务端给开启该开关的观者代发轨迹粒子（客户端侧由本地渲染负责） */
	@Inject(method = "tick", at = @At("TAIL"))
	private void enchantmentReforged$serverParticleTrail(CallbackInfo ci) {
		ArrowParticleTrails.tickServerTrail((AbstractArrow) (Object) this);
	}

	/**
	 * 轻盈（1.3.7）：把原版箭的重力常量按"这支箭的轻盈等级"缩放。
	 *
	 * <p>已用 javap 核对：{@code tick} 里真正生效的重力是那个 double 常量
	 * {@code 0.05000000074505806}（同名的 float 局部是死代码）；两端都读各自的表，
	 * 等级由服务端在箭生成时通过 S2C 包同步，因此射手与旁观者看到的弹道一致。
	 */
	@ModifyConstant(method = "tick", constant = @Constant(doubleValue = 0.05000000074505806D))
	private double enchantmentReforged$lightnessGravity(double gravity) {
		return gravity * ArrowTraits.gravityFactor((AbstractArrow) (Object) this);
	}

	/**
	 * 死亡之眼（1.3.7）：整箭落空结算 + 实体移除时清表。
	 *
	 * <p><b>硬规则</b>：只有"整段飞行未命中任何生物"且已插地 / 已移除的箭才掷清空概率；
	 * 命中过生物（包含穿透一箭多目标）后落地一律不掷、不清空。
	 */
	@Inject(method = "tick", at = @At("TAIL"))
	private void enchantmentReforged$settleDeathEye(CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		if (self.level().isClientSide()) {
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
	 * <p>本树原本在 {@code setOwner} 末尾继承档位，正常路径已经覆盖；这道补写是兜底——例如 owner
	 * 在构造阶段还是 null、或者射手档位当时尚未上报的情况。服务端前 200 tick 内幂等尝试一次，
	 * 写入后 {@code getParticleStyle() != UNSET}，后续 tick 自然短路。
	 */
	@Inject(method = "tick", at = @At("HEAD"))
	private void enchantmentReforged$backfillShooterStyle(CallbackInfo ci) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		if (self.level().isClientSide() || self.tickCount > 200) {
			return;
		}
		if (enchantmentReforged$getParticleStyle() != ArrowParticleStyle.UNSET) {
			return;
		}
		if (!(self.getOwner() instanceof ServerPlayer shooter)) {
			return;
		}
		if (!ArrowParticleTrails.isArrow(self)) {
			return;
		}
		int style = ParticlePreferences.styleOf(shooter.getUUID());
		if (style != ArrowParticleStyle.UNSET) {
			enchantmentReforged$setParticleStyle(style);
		}
	}

	/**
	 * 保险：标记过的箭若这次伤害仍被免疫（返回 false），清掉冷却重试一次。
	 *
	 * <p>原版只有伤害成功才会调用 {@code onHit}，而药水效果正是在那里施加的；
	 * 重试一次能保证"伤害 + 药水效果"都落地（多重射击每支箭都带药水箭时尤其重要）。
	 */
	@WrapOperation(
			method = "onHitEntity",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			)
	)
	private boolean enchantmentReforged$retryExtraHit(Entity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		AbstractArrow self = (AbstractArrow) (Object) this;
		// 死亡之眼（1.3.7）：命中时先用"当前层数"放大本次伤害（穿透一箭多目标时每个目标各结算一次）
		float adjusted = amount * ArrowTraits.deathEyeMultiplier(self);
		boolean hit = original.call(target, source, adjusted);
		if (!hit && target instanceof LivingEntity living && EnchantmentEffects.isExtraHitArrow(self)) {
			living.invulnerableTime = 0;
			hit = original.call(target, source, adjusted);
		}
		if (hit && target instanceof LivingEntity) {
			// 命中生物后才加层（先算伤害再加层），并清掉连续落空计数
			ArrowTraits.recordHit(self);
		}
		return hit;
	}
}
