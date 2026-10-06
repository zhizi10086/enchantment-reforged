package com.enchantmentreforged.mixin;

import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.network.ParticlePreferences;
import com.enchantmentreforged.particle.ArrowParticleCarrier;
import com.enchantmentreforged.particle.ArrowParticleTrails;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
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
 * <p>档位借用了原版同步字节 {@code PROJECTILE_FLAGS} 里没被用到的 4 个高位
 * （暴击/无碰撞/弩箭只占低 3 位）。这样不必新增同步字段——新增字段会让原版
 * 字段的 id 整体后移，客户端进原版服务器时会读错数据。
 */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin implements ArrowParticleCarrier {
	@Shadow
	@Final
	private static TrackedData<Byte> PROJECTILE_FLAGS;

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
	private void enchantmentReforged$inheritShooterParticleStyle(EntityType<?> type, LivingEntity shooter,
			World world, CallbackInfo ci) {
		if (shooter == null) {
			return;
		}
		int style = ParticlePreferences.styleOf(shooter.getUuid());
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
		((Entity) (Object) this).getDataTracker().set(PROJECTILE_FLAGS, (byte) flags);
	}

	/** 原版同步字节的原始值（无符号） */
	@Unique
	private int enchantmentReforged$rawFlags() {
		return ((Entity) (Object) this).getDataTracker().get(PROJECTILE_FLAGS) & 0xFF;
	}

	@Inject(method = "onEntityHit", at = @At("HEAD"))
	private void enchantmentReforged$ensureExtraHit(EntityHitResult hitResult, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (EnchantmentEffects.isExtraHitArrow(self) && hitResult.getEntity() instanceof LivingEntity target) {
			target.timeUntilRegen = 0;
		}
	}

	/** 远距离显示：服务端给开启该开关的观者代发轨迹粒子（客户端侧由本地渲染负责） */
	@Inject(method = "tick", at = @At("TAIL"))
	private void enchantmentReforged$serverParticleTrail(CallbackInfo ci) {
		ArrowParticleTrails.tickServerTrail((PersistentProjectileEntity) (Object) this);
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
	private boolean enchantmentReforged$retryExtraHit(Entity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		boolean hit = original.call(target, source, amount);
		Entity self = (Entity) (Object) this;
		if (!hit && target instanceof LivingEntity living && EnchantmentEffects.isExtraHitArrow(self)) {
			living.timeUntilRegen = 0;
			return original.call(target, source, amount);
		}
		return hit;
	}
}
