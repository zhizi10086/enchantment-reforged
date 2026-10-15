package com.summy.enchantment;

import com.summy.enchantment.command.SummyEnchantmentCommand;
import com.summy.enchantment.config.SummyEnchantmentConfig;
import com.summy.enchantment.event.SummyEnchantmentEvents;
import com.summy.enchantment.network.ConfigSync;
import com.summy.enchantment.registry.ModAttributes;
import com.summy.enchantment.registry.ModEnchantments;
import com.summy.enchantment.registry.ModItemGroups;
import com.summy.enchantment.registry.ModStatusEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组入口（Forge 版）：读配置、注册自定义内容、挂事件。
 *
 * <p>注意：这里不覆盖任何原版注册表条目（不用 Registry.set，也不复用原版 id），
 * 原版锋利/力量/保护始终保留自己的注册表项。
 */
@Mod(SummyEnchantment.MOD_ID)
public class SummyEnchantment {
	public static final String MOD_ID = "summy_enchantment";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public SummyEnchantment() {
		// 必须先读配置：自定义附魔的上限、保护上限都在运行时查询配置
		SummyEnchantmentConfig.load();

		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		// 生命护盾属性：玩家默认属性表已建好，用模组总线事件追加一条
		modBus.addListener(ModAttributes::onAttributeModification);

		// 自定义内容的注册统一走 Forge 的 RegisterEvent（模组构造器阶段注册表已锁定）
		ModStatusEffects.register(modBus);
		ModAttributes.register(modBus);
		ModEnchantments.register(modBus);
		ModItemGroups.register(modBus);

		// 网络：三条消息（配置同步 / 粒子上报 / 粒子索要）
		ConfigSync.registerServerReceivers();

		// 斩首、灵魂加护、本能释放、死亡粒子等（Forge 事件总线）
		SummyEnchantmentEvents.register();

		// 客户端专属：注册客户端 tick/tooltip/断线回调，以及 mods 列表里的"配置"入口
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> com.summy.enchantment.client.SummyEnchantmentClient.init());

		SummyEnchantmentConfig config = SummyEnchantmentConfig.get();
		LOGGER.info("[Summy Enchantment] 已加载（Forge 版）：铁砧经验点数={}，锋利Plus={}（{} / 级），"
						+ "力量Plus={}（{} / 级），保护5级={}",
				config.enableAnvilXpCost, config.enableCustomSharpness, config.sharpnessDamagePerLevel,
				config.enableStrengthRework, config.strengthMultiplierPerLevel, config.enableProtectionLevel5);
	}

	/** 构造本模组的命名空间 id */
	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
