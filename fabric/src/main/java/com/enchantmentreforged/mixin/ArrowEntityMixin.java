package com.enchantmentreforged.mixin;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * 药水箭的瞬间伤害段不再被箭矢的无敌帧吞掉。
 *
 * <p>原版顺序：箭矢物理伤害先结算（设下 20 tick 受击冷却），随后 {@code onHit} 才施加药水效果；
 * 瞬间伤害走 {@code applyInstantEffect → target.damage(...)}，会被
 * {@code timeUntilRegen > 10 && amount ≤ lastDamageTaken} 整段免疫。
 *
 * <p>这里在施加药水效果之前（此时物理伤害已生效）先清掉目标的受击冷却，
 * 让"瞬间伤害"（以及"瞬间治疗打亡灵"）那一段完整落地；不带这两种效果的箭一律不动冷却。
 */
@Mixin(ArrowEntity.class)
public abstract class ArrowEntityMixin {
	@Shadow
	private Potion potion;

	@Shadow
	@Final
	private Set<StatusEffectInstance> effects;

	@Inject(method = "onHit", at = @At("HEAD"))
	private void enchantmentReforged$potionArrowFullDamage(LivingEntity target, CallbackInfo ci) {
		if (!EnchantmentReforgedConfig.get().enablePotionArrowFullDamage) {
			return;
		}
		if (hasInstantEffect(this.potion, target) || this.effects.stream().anyMatch(effect -> isRelevant(effect, target))) {
			target.timeUntilRegen = 0;
		}
	}

	private static boolean hasInstantEffect(Potion potion, LivingEntity target) {
		for (StatusEffectInstance effect : potion.getEffects()) {
			if (isRelevant(effect, target)) {
				return true;
			}
		}
		return false;
	}

	/** 瞬间伤害永远算；瞬间治疗只有在目标是不死生物时才算（它会掉血） */
	private static boolean isRelevant(StatusEffectInstance effect, LivingEntity target) {
		if (effect.getEffectType() == StatusEffects.INSTANT_DAMAGE) {
			return true;
		}
		return effect.getEffectType() == StatusEffects.INSTANT_HEALTH && target.isUndead();
	}
}
