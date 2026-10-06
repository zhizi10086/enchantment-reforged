package com.enchantmentreforged;

import com.enchantmentreforged.command.EnchantmentReforgedCommand;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.event.EnchantmentReforgedEvents;
import com.enchantmentreforged.network.ConfigSync;
import com.enchantmentreforged.registry.ModAttributes;
import com.enchantmentreforged.registry.ModEnchantments;
import com.enchantmentreforged.registry.ModItemGroups;
import com.enchantmentreforged.registry.ModStatusEffects;
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
@Mod(EnchantmentReforged.MOD_ID)
public class EnchantmentReforged {
	public static final String MOD_ID = "enchantment_reforged";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public EnchantmentReforged() {
		// 必须先读配置：自定义附魔的上限、保护上限都在运行时查询配置
		EnchantmentReforgedConfig.load();

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
		EnchantmentReforgedEvents.register();

		// 客户端专属：注册客户端 tick/tooltip/断线回调，以及 mods 列表里的"配置"入口
		DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> com.enchantmentreforged.client.EnchantmentReforgedClient.init());

		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		LOGGER.info("[Enchantment Reforged] 已加载（Forge 版）：铁砧经验点数={}，锋利Plus={}（{} / 级），"
						+ "力量Plus={}（{} / 级），保护5级={}",
				config.enableAnvilXpCost, config.enableCustomSharpness, config.sharpnessDamagePerLevel,
				config.enableStrengthRework, config.strengthMultiplierPerLevel, config.enableProtectionLevel5);
	}

	/** 构造本模组的命名空间 id */
	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
