package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
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
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
		boolean hit = original.call(target, source, amount);
		Entity self = (Entity) (Object) this;
		if (!hit && target instanceof LivingEntity living && EnchantmentEffects.isExtraHitArrow(self)) {
			living.invulnerableTime = 0;
			return original.call(target, source, amount);
		}
		return hit;
	}
}
