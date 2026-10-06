package com.enchantmentreforged.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.enchantmentreforged.combat.CombatFormulas;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 生物近战伤害。
 *
 * <p>原版力量对生物同样走 ATTACK_DAMAGE 属性修饰符，而自定义力量刻意不带修饰符，
 * 所以要在生物命中处补上同样的乘算，玩家与生物的力量语义才一致。
 */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
	@Unique
	private LivingEntity enchantmentReforged$self() {
		return (LivingEntity) (Object) this;
	}

	@WrapOperation(
			method = "tryAttack(Lnet/minecraft/entity/Entity;)Z",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			)
	)
	private boolean enchantmentReforged$applyStrengthToMobAttack(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
		if (!EnchantmentReforgedConfig.get().applyStrengthToMobs) {
			return original.call(target, source, amount);
		}
		return original.call(target, source, amount * CombatFormulas.strengthMultiplier(enchantmentReforged$self()));
	}
}
