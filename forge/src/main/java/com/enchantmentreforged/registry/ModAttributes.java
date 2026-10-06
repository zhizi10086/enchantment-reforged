package com.enchantmentreforged.registry;

import com.enchantmentreforged.EnchantmentReforged;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 自定义玩家属性。
 *
 * <p>生命护盾的数值存在这里，不再借用状态效果的 amplifier：
 * 属性值随玩家 NBT 持久化、可用 {@code /attribute} 查询，也不会在效果栏占一格图标。
 *
 * <p>Forge 侧不需要 Fabric 那套"整体替换玩家默认属性"：直接用
 * {@link EntityAttributeModificationEvent} 往玩家默认属性表里追加一条即可。
 *
 * <p>属性本身必须在 {@link RegisterEvent} 里注册：模组构造器阶段原版注册表已锁定。
 */
public final class ModAttributes {
	/** 护盾值上限：最大生命 × 20%/级 × 5 级 加上生命提升/死者之心也远小于这个数 */
	public static final double LIFE_SHIELD_MAX = 2048.0D;

	public static Attribute LIFE_SHIELD;

	/** 注册事件期间的临时引用，仅用于 register 辅助方法 */
	private static RegisterEvent pendingEvent;

	private ModAttributes() {
	}

	public static void register(IEventBus bus) {
		bus.addListener(ModAttributes::onRegister);
	}

	private static void onRegister(RegisterEvent event) {
		if (!ForgeRegistries.Keys.ATTRIBUTES.equals(event.getRegistryKey())) {
			return;
		}
		pendingEvent = event;
		try {
			LIFE_SHIELD = register("life_shield",
					new RangedAttribute("attribute.name.enchantment_reforged.life_shield",
							0.0D, 0.0D, LIFE_SHIELD_MAX));
		} finally {
			pendingEvent = null;
		}
	}

	/** 注册并返回实例，方便直接写回静态字段 */
	private static <T extends Attribute> T register(String path, T attribute) {
		pendingEvent.register(ForgeRegistries.Keys.ATTRIBUTES, EnchantmentReforged.id(path), () -> attribute);
		return attribute;
	}

	/** 模组总线事件：给玩家挂上护盾属性（在 EnchantmentReforged 构造里注册） */
	public static void onAttributeModification(EntityAttributeModificationEvent event) {
		if (LIFE_SHIELD != null) {
			event.add(EntityType.PLAYER, LIFE_SHIELD, 0.0D);
		}
	}
}
