package com.enchantmentreforged.registry;

import com.enchantmentreforged.EnchantmentReforged;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * 自定义玩家属性。
 *
 * <p>生命护盾的数值存在这里，不再借用状态效果的 amplifier：
 * 属性值随玩家 NBT 持久化、可用 {@code /attribute} 查询，也不会在效果栏占一格图标。
 *
 * <p>刻意<b>不</b>调用 {@code setTracked(true)}：客户端不需要这个值（黄心来自原版同步的
 * 伤害吸收值），不参与属性同步就少一份网络负担。
 */
public final class ModAttributes {
	/** 护盾值上限：最大生命 × 20%/级 × 5 级 加上生命提升/死者之心也远小于这个数 */
	public static final double LIFE_SHIELD_MAX = 2048.0D;

	public static EntityAttribute LIFE_SHIELD;

	private ModAttributes() {
	}

	public static void register() {
		LIFE_SHIELD = Registry.register(
				Registries.ATTRIBUTE,
				EnchantmentReforged.id("life_shield"),
				new ClampedEntityAttribute("attribute.name.enchantment_reforged.life_shield",
						0.0D, 0.0D, LIFE_SHIELD_MAX)
		);
		// 玩家的默认属性表在游戏 bootstrap 阶段就已经建好（早于模组初始化），
		// 所以这里用 Fabric 的接口整体替换一份：内容仍是原版 createPlayerAttributes()
		// 的全部属性，只多挂我们这一条。若其它模组在此之后也注册玩家默认属性，
		// 会以它的为准（我们只记一行日志并安全退化）。
		FabricDefaultAttributeRegistry.register(EntityType.PLAYER,
				PlayerEntity.createPlayerAttributes().add(LIFE_SHIELD, 0.0D));
	}
}
