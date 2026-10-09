package com.enchantmentreforged.compat;

import net.minecraftforge.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Summy Reliquary 兼容 Mixin 的配置插件。
 *
 * <p>没装 SR 时直接跳过整份配置，避免启动日志里出现"目标类找不到"的警告。
 *
 * <p>注意：这里**绝不能用 {@code Class.forName} 去探测目标类** —— 那会在 Mixin 准备阶段就把
 * SR 的类加载进来，使它错过之后的注入（表现为整份兼容 Mixin 静默失效，没有任何报错）。
 * 只查类文件资源 + 模组列表。
 */
public class SummyReliquaryMixinPlugin implements IMixinConfigPlugin {
	private static final String MOD_ID = "summy_reliquary";
	private static final Logger LOGGER = LoggerFactory.getLogger("enchantment_reforged_summy_reliquary_compat");
	private static boolean logged;

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		// 返回 null 表示使用配置文件里声明的 refmap
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		boolean classVisible = enchantmentReforged$hasReliquaryClassFile();
		boolean modLoaded = enchantmentReforged$isModPresent();
		if (!logged) {
			logged = true;
			LOGGER.info("[ER] Summy Reliquary 兼容检查：类文件可见={}，mod 已加载={}，目标={}",
					classVisible, modLoaded, targetClassName);
		}
		return classVisible || modLoaded;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	/** 只看类文件在不在，不加载类 */
	private static boolean enchantmentReforged$hasReliquaryClassFile() {
		try {
			ClassLoader loader = SummyReliquaryMixinPlugin.class.getClassLoader();
			return loader.getResource("com/summy/reliquary/effect/ShadowDash.class") != null;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 先问 Forge 的"加载中 mod 列表"（比 ModList 早且不需要加载类），再退回 ModList */
	private static boolean enchantmentReforged$isModPresent() {
		try {
			if (net.minecraftforge.fml.loading.FMLLoader.getLoadingModList()
					.getModFileById(MOD_ID) != null) {
				return true;
			}
		} catch (Throwable ignored) {
			// 落回下面的 ModList 判定
		}
		ModList modList = ModList.get();
		return modList != null && modList.isLoaded(MOD_ID);
	}
}
