package com.enchantmentreforged.mixin;

import com.enchantmentreforged.compat.SpearCompat;
import com.enchantmentreforged.particle.MeleeParticles;
import com.notunanancyowen.spears.dataholders.SpearUser;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 与矛模组 Backported Spears（mod id: spears）的可选兼容。
 *
 * <p>目标用字符串指定，所以**不需要把矛模组当依赖**：没装它时这个类永远不会有目标可应用，
 * 等于不存在；配置也标成非必需（{@code required: false}），其它版本上注入失败只记日志不报错。
 *
 * <p>矛模组 1.4.7 的玩家分支把基础伤害算了两遍，这里在它两次攻击的 {@code SpearUser.pierce}
 * 调用点上把伤害参数反解成"我们期望的值"，并顺带结算魔剑/嗜血。左键刺击（stab）额外吃
 * 力量Plus，右键充能刺击不吃；死神祝福两条路径都吃。
 */
// 必须用 targets 字符串：写成 @Mixin(SpearItem.class) 会在 Mixin 准备阶段就把该类加载进来，
// 使它错过注入（探针实测确认过这一行为）
@Mixin(targets = "com.notunanancyowen.spears.items.SpearItem")
public abstract class SpearItemCompatMixin {
	/** 左键刺击：SpearItem.stab 里的 pierce 调用 */
	@WrapOperation(
			method = "stab",
			at = @At(
					value = "INVOKE",
					target = "Lcom/notunanancyowen/spears/dataholders/SpearUser;pierce(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/Entity;FZZZ)Z"
			),
			require = 0,
			// 目标是矛模组自己的方法（不是原版成员），不需要也不应该做映射重写
			remap = false
	)
	private boolean enchantmentReforged$stabPierce(SpearUser spearUser, EquipmentSlot slot, Entity target, float damage,
			boolean dealDamage, boolean knockback, boolean dismount, Operation<Boolean> original) {
		// pierce 是接口方法，包装方法的第一个参数就是这个接收者；它同时也是攻击者本身
		LivingEntity attacker = (LivingEntity) spearUser;
		ItemStack spear = attacker.getItemBySlot(slot);
		float adjusted = SpearCompat.adjustPierceDamage(attacker, spear, target, damage, true);
		// 接口调用：接收者也算一个参数，必须一起传回原方法
		boolean hit = original.call(spearUser, slot, target, adjusted, dealDamage, knockback, dismount);
		if (hit && dealDamage) {
			SpearCompat.applyHitEnchantments(attacker, spear, target, adjusted);
			// 近战粒子：左键刺击与普通近战同一套档位（右键充能刺击不生成）
			MeleeParticles.spawnOnHit(attacker, target);
		}
		return hit;
	}

	/**
	 * 右键充能刺击：SpearItem 里 {@code Item#onUseTick} 覆盖方法中的 pierce 调用。
	 *
	 * <p>矛模组是已发布的正式 jar，该方法在它里面是 SRG 名 {@code m_5929_}
	 * （1.20.1 的 {@code Item#onUseTick}），因此这里直接写 SRG 名并 {@code remap = false}；
	 * 若写官方名 "onUseTick"，生产环境会因为方法名对不上而静默失效。
	 */
	@WrapOperation(
			method = "m_5929_",
			at = @At(
					value = "INVOKE",
					target = "Lcom/notunanancyowen/spears/dataholders/SpearUser;pierce(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/Entity;FZZZ)Z"
			),
			require = 0,
			// 目标方法与接口都属于矛模组自身，不走原版映射表
			remap = false
	)
	private boolean enchantmentReforged$chargePierce(SpearUser spearUser, EquipmentSlot slot, Entity target, float damage,
			boolean dealDamage, boolean knockback, boolean dismount, Operation<Boolean> original) {
		LivingEntity user = (LivingEntity) spearUser;
		ItemStack spear = user.getItemBySlot(slot);
		float adjusted = SpearCompat.adjustPierceDamage(user, spear, target, damage, false);
		boolean hit = original.call(spearUser, slot, target, adjusted, dealDamage, knockback, dismount);
		if (hit && dealDamage) {
			SpearCompat.applyHitEnchantments(user, spear, target, adjusted);
		}
		return hit;
	}
}
