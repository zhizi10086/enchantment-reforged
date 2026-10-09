package com.enchantmentreforged.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
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
	/** 用来探测 SR 是否存在的类文件路径（只查资源，不加载类） */
	private static final String PROBE_CLASS = "com/summy/reliquary/effect/ShadowDash.class";

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
		boolean classVisible = false;
		try {
			classVisible = SummyReliquaryMixinPlugin.class.getClassLoader().getResource(PROBE_CLASS) != null;
		} catch (Throwable ignored) {
			// 退回到下面的模组列表判断
		}
		return classVisible || FabricLoader.getInstance().isModLoaded(MOD_ID);
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
