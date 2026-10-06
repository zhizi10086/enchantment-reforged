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
 * 矛模组兼容 Mixin 的配置插件。
 *
 * <p>没装矛模组时直接跳过整份配置，避免启动日志里出现"目标类找不到"的警告；
 * 装了才让兼容 Mixin 参与加载。
 *
 * <p>MixinExtras 在 Forge 上会通过自己 jar 里的 {@code mixinextras.init.mixins.json} 自动引导，
 * 所以这里只需要做"装没装矛模组"的开关判断。
 */
public class SpearMixinPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("enchantment_reforged_spear_compat");
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
		// 注意：这里绝对不能 Class.forName 目标类！那会在 Mixin 准备阶段就把 SpearItem
		// 加载进来，导致它错过之后的注入（表现为整份兼容 Mixin 静默失效）。
		boolean classVisible = hasSpearClassFile();
		boolean modLoaded = isModPresent();
		if (!logged) {
			logged = true;
			LOGGER.info("[ER] 矛兼容检查：类文件可见={}，mod 已加载={}，目标={}", classVisible, modLoaded, targetClassName);
		}
		return classVisible || modLoaded;
	}

	/** 只看类文件在不在，不加载类 */
	private static boolean hasSpearClassFile() {
		try {
			ClassLoader loader = SpearMixinPlugin.class.getClassLoader();
			return loader.getResource("com/notunanancyowen/spears/items/SpearItem.class") != null;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 先问 Forge 的"加载中 mod 列表"（比 ModList 早得多且不需要加载类），再退回 ModList */
	private static boolean isModPresent() {
		try {
			if (net.minecraftforge.fml.loading.FMLLoader.getLoadingModList().getModFileById("spears") != null) {
				return true;
			}
		} catch (Throwable ignored) {
			// 落回下面的 ModList 判定
		}
		ModList modList = ModList.get();
		return modList != null && modList.isLoaded("spears");
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
}
