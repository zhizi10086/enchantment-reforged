package com.enchantmentreforged.mixin;

import com.enchantmentreforged.compat.SummyReliquaryCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 与 Summy Reliquary 的投掷长矛命中对齐到 ER 的近战管线（可选兼容）。
 *
 * <p><b>方法选择器只写 dev 名 {@code onHitEntity}</b>：它虽然在生产 jar 里被重命名为 {@code m_5790_}，
 * 但构建期的注解处理器会把这条选择器写进 refmap（实测 refmap 里就是
 * {@code onHitEntity(...) => Lcom/summy/reliquary/entity/ThrownSpear;m_5790_(...)V}），
 * 生产环境照 refmap 解析即可。原先额外写的那条字面量 {@code m_5790_} 在生产环境与它指向同一个方法，
 * 属冗余写法，已删除。
 *
 * <p><b>{@code @At} 的 owner 必须是 {@code Entity}、不能写 {@code LivingEntity}</b>：
 * SR 那句是 {@code Entity target = result.getEntity(); target.hurt(source, damage);}，
 * 局部变量的静态类型就是 {@code Entity} —— 生产 jar 里实测为
 * {@code invokevirtual net/minecraft/world/entity/Entity.m_6469_(...)}，
 * 写 {@code LivingEntity} 会匹配不到（{@code require = 0} 会静默跳过）。
 * handler 的第一个参数同理必须是 {@code Entity}（栈上就是它，收窄成子类在字节码层面不合法）。
 *
 * <p>SR 投掷后本体不消耗（只扣耐久），所以命中时主手仍是那把长矛，复仇的武器判定照常通过。
 */
@Mixin(targets = "com.summy.reliquary.entity.ThrownSpear")
public abstract class SummyReliquaryThrownSpearMixin {
	/** 覆写 {@code AbstractArrow#onHitEntity}：dev 名 {@code onHitEntity}（生产名由 refmap 解析） */
	@WrapOperation(
			method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			),
			require = 0
	)
	private boolean enchantmentReforged$thrownSpear(Entity target, DamageSource source, float amount,
			Operation<Boolean> original) {
		return enchantmentReforged$applyThrownSpear(target, source, amount, original);
	}

	/** 投掷命中：乘 ER 的伤害乘区，并吃全部命中后效果（与左键近战同档） */
	@Unique
	private static boolean enchantmentReforged$applyThrownSpear(Entity target, DamageSource source,
			float amount, Operation<Boolean> original) {
		// SR 对非玩家也有一个 thrown(...) 兜底伤害源，那种情况原样放行
		if (!(source.getDirectEntity() instanceof Player player)) {
			return original.call(target, source, amount);
		}
		float adjusted = amount * SummyReliquaryCompat.meleeMultiplier(player);
		boolean hit = original.call(target, source, adjusted);
		if (hit) {
			SummyReliquaryCompat.onMeleeHit(player, player.getMainHandItem(), target, source,
					Math.max(adjusted, 0.0F), true, true);
		}
		return hit;
	}
}
