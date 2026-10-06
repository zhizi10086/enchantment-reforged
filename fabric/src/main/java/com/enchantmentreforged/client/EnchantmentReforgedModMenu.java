package com.enchantmentreforged.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Mod Menu 集成入口点。
 *
 * <p>只有当玩家装了 Mod Menu 时，Fabric Loader 才会加载这个类（fabric.mod.json 的 modmenu 入口点），
 * 因此没装 Mod Menu 的环境不会因为缺少相关类而报错。
 */
@Environment(EnvType.CLIENT)
public class EnchantmentReforgedModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return EnchantmentReforgedConfigScreen::new;
	}
}
