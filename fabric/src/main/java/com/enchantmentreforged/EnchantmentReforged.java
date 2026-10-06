package com.enchantmentreforged;

import com.enchantmentreforged.combat.CombatFormulas;
import com.enchantmentreforged.command.EnchantmentReforgedCommand;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.event.EnchantmentReforgedEvents;
import com.enchantmentreforged.network.ConfigSync;
import com.enchantmentreforged.particle.MeleeParticles;
import com.enchantmentreforged.registry.ModAttributes;
import com.enchantmentreforged.registry.ModEnchantments;
import com.enchantmentreforged.registry.ModItemGroups;
import com.enchantmentreforged.registry.ModStatusEffects;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模组入口：只做三件事——读配置、注册自定义内容、挂事件。
 *
 * <p>注意：这里不覆盖任何原版注册表条目（不用 Registry.set，也不复用原版 id），
 * 原版锋利/力量/保护始终保留自己的注册表项。
 */
public class EnchantmentReforged implements ModInitializer {
	public static final String MOD_ID = "enchantment_reforged";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 必须先读配置：自定义附魔的上限、保护上限都在运行时查询配置
		EnchantmentReforgedConfig.load();

		ModStatusEffects.register();
		ModAttributes.register();
		ModEnchantments.register();
		ModItemGroups.register();

		// 原版命令接口：/enchantmentreforged status|set|reset
		EnchantmentReforgedCommand.register();

		// 斩首（击杀结算）与本能释放（每 tick 检测拉满）
		EnchantmentReforgedEvents.register();

		// 玩家进服时把服务端配置同步给客户端（联机时客户端以服务端为准显示），
		// 并顺带索要一次"每名玩家自己的箭矢粒子偏好"
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ConfigSync.sendTo(handler.getPlayer());
			ConfigSync.requestParticlePreference(handler.getPlayer());
		});

		// 客户端上报的"每名玩家自己的箭矢粒子档位"（不属于服务端配置，单独一条通道）
		ConfigSync.registerServerReceivers();
		// 服务器停止时清空近战粒子的待喷发队列
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MeleeParticles.clearBursts());

		// 旧存档 / 旧药水可能已经带有原版力量，实体加载时替换成自定义力量，
		// 否则原版的 +3/级属性修饰符会和新的乘算倍率叠加。
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof LivingEntity livingEntity) {
				CombatFormulas.replaceVanillaStrength(livingEntity);
			}
		});

		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		LOGGER.info("[Enchantment Reforged] 已加载：铁砧经验点数={}，锋利Plus={}（{} / 级），力量Plus={}（{} / 级），保护5级={}",
				config.enableAnvilXpCost, config.enableCustomSharpness, config.sharpnessDamagePerLevel,
				config.enableStrengthRework, config.strengthMultiplierPerLevel, config.enableProtectionLevel5);
	}

	/** 构造本模组的命名空间 id */
	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
