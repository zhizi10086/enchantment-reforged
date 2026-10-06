package com.enchantmentreforged.registry;

import com.enchantmentreforged.EnchantmentReforged;
import com.enchantmentreforged.effect.SoulGraceStatusEffect;
import com.enchantmentreforged.effect.StrengthStatusEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 自定义状态效果注册。
 *
 * <p>自定义力量的伤害是"命中时乘算"，所以这里刻意不挂任何属性修饰符——
 * 原版力量靠 ATTACK_DAMAGE 属性每级加 3，如果这里再挂一次就会双重计算。
 *
 * <p>Forge 版必须走 {@link RegisterEvent}：模组构造器执行时原版注册表已经锁定，
 * 直接用 {@code Registry.register} 会抛 "Can not register to a locked registry"。
 * 这里在注册事件里回填静态字段，其余代码照旧按普通字段引用。
 */
public final class ModStatusEffects {
	/** 与原版力量同色（0x932423），视觉上无缝替换 */
	public static final int STRENGTH_COLOR = 0x932423;

	public static MobEffect STRENGTH;
	/** 灵魂加护的冷却（纯可视化，权威计时在服务端） */
	public static MobEffect SOUL_GRACE;

	/** 注册事件期间的临时引用，仅用于 register 辅助方法 */
	private static RegisterEvent pendingEvent;

	private ModStatusEffects() {
	}

	public static void register(IEventBus bus) {
		bus.addListener(ModStatusEffects::onRegister);
	}

	private static void onRegister(RegisterEvent event) {
		if (!ForgeRegistries.Keys.MOB_EFFECTS.equals(event.getRegistryKey())) {
			return;
		}
		pendingEvent = event;
		try {
			STRENGTH = register("strength", new StrengthStatusEffect());
			SOUL_GRACE = register("soul_grace", new SoulGraceStatusEffect());
		} finally {
			pendingEvent = null;
		}
	}

	/** 注册并返回实例，方便直接写回静态字段 */
	private static <T extends MobEffect> T register(String path, T effect) {
		pendingEvent.register(ForgeRegistries.Keys.MOB_EFFECTS, EnchantmentReforged.id(path), () -> effect);
		return effect;
	}
}
