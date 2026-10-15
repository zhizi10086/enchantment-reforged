package com.summy.enchantment.mixin;

import com.summy.enchantment.combat.EnchantmentEffects;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 经验学者：只在拾取经验球时加成。
 *
 * <p>注入点是经验球给玩家的那一次 {@code addExperience}，因此铁砧消耗、交易、
 * 熔炉取出经验等其它来源完全不受影响。
 */
@Mixin(ExperienceOrbEntity.class)
public abstract class ExperienceOrbEntityMixin {
	@Unique
	private static final ThreadLocal<PlayerEntity> summyEnchantment$currentPlayer = new ThreadLocal<>();

	@Inject(method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"))
	private void summyEnchantment$capturePlayer(PlayerEntity player, CallbackInfo ci) {
		summyEnchantment$currentPlayer.set(player);
	}

	@Inject(method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("RETURN"))
	private void summyEnchantment$clearPlayer(PlayerEntity player, CallbackInfo ci) {
		summyEnchantment$currentPlayer.remove();
	}

	/** 经验球给玩家的经验按头盔上的经验学者放大 */
	@ModifyArg(
			method = "onPlayerCollision(Lnet/minecraft/entity/player/PlayerEntity;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addExperience(I)V"),
			index = 0
	)
	private int summyEnchantment$scholarBonus(int amount) {
		PlayerEntity player = summyEnchantment$currentPlayer.get();
		return player == null ? amount : EnchantmentEffects.scholarBonus(player, amount);
	}
}
