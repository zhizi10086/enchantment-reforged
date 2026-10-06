package com.enchantmentreforged.combat;

import net.minecraft.entity.player.PlayerEntity;

/**
 * 饥饿值组件的"主人"。
 *
 * <p>1.20.1 的 {@code HungerManager} 不持有玩家引用，而"大胃袋"这类
 * 与饥饿上限相关的逻辑必须能查到胸甲附魔，所以由 mixin 把这个引用挂上去。
 */
public interface HungerOwner {
	/** 绑定所属玩家（由 PlayerEntity 的构造完成时调用） */
	void enchantmentReforged$setOwner(PlayerEntity player);
}
