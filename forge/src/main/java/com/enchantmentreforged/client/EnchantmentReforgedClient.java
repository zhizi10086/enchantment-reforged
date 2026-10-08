package com.enchantmentreforged.client;

import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.network.ConfigSync;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * 客户端入口（Forge 版）。
 *
 * <p>由主类通过 {@code DistExecutor} 在客户端调用 {@link #init()}：
 * 注册物品提示、客户端 tick（本能释放 + 粒子偏好重试）、断线回调，
 * 并把配置界面挂到 Forge 的 mods 列表"配置"按钮上（取代 Fabric 的 Mod Menu 集成）。
 */
public final class EnchantmentReforgedClient {
	/** 粒子偏好是否还没成功上报（改设置或进服时置位，成功发送后清零） */
	private static boolean particlePreferenceDirty = true;
	/** 服务端是否支持"远距离代发轨迹粒子"（收到协议版本 ≥ 2 的索要包时置位） */
	private static boolean serverSupportsLongDistanceParticles;
	/** 联机时是否正在使用服务端下发的配置（断线后恢复本地文件） */
	private static boolean usingServerConfig;
	/** 是否装了 Enchantment Descriptions（装了就把附魔简介让给它渲染，避免重复） */
	private static Boolean enchDescLoaded;

	private EnchantmentReforgedClient() {
	}

	public static void init() {
		// 物品提示（附魔书 / 装备 Shift 展开）
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedClient::onItemTooltip);
		// 本能释放：客户端也做同样的检测，保证拉弓动画与使用状态即时结束/重开
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedClient::onClientTick);
		// 离开服务器/世界后，恢复成本地配置文件里的设置
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedClient::onLoggingOut);

		// 大胃袋的第二行饥饿值：走 Forge 官方 GUI overlay，注册在气泡层之上，
		// 确保画在原版 HUD 与苹果皮之上、不被覆盖（详见 ExtraHungerOverlay 的类注释）
		FMLJavaModLoadingContext.get().getModEventBus()
				.addListener(EnchantmentReforgedClient::onRegisterGuiOverlays);

		// mods 列表里的"配置"按钮
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory(
						(mc, parent) -> new EnchantmentReforgedConfigScreen(parent)));
	}

	private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
		// 锚点放在 ITEM_NAME 之上：经典状态条是注册在 ITEM_NAME 之下的，
		// 这样能确保我们的行在它之后绘制，读到的 ForgeGui 堆叠偏移已经包含它所有的条。
		event.registerAbove(VanillaGuiOverlay.ITEM_NAME.id(), ExtraHungerOverlay.ID, ExtraHungerOverlay.INSTANCE);
	}

	/**
	 * 是否装了 Enchantment Descriptions（mod id: enchdesc）。
	 *
	 * <p>它会在每个附魔的名字行后面无条件插入 {@code <附魔ID>.desc} 的描述，
	 * 而我们的附魔现在也提供了这个键（一句话简介），所以装了它时就把简介让给它渲染，
	 * 我们自己的数值明细改为"按住 Shift 展开"，避免同一个附魔出现两行描述。
	 */
	private static boolean enchantmentReforged$enchDescLoaded() {
		if (enchDescLoaded == null) {
			enchDescLoaded = ModList.get().isLoaded("enchdesc");
		}
		return enchDescLoaded;
	}

	private static void onItemTooltip(ItemTooltipEvent event) {
		ItemStack stack = event.getItemStack();
		List<Component> lines = event.getToolTip();
		if (stack.is(Items.ENCHANTED_BOOK)) {
			// 附魔书：追加本模组附魔的说明（数值跟着配置走）
			List<Component> description = EnchantmentDescriptions.describe(stack);
			if (!description.isEmpty()) {
				if (!enchantmentReforged$enchDescLoaded()
						|| net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
					lines.add(Component.empty());
					for (Component line : description) {
						lines.add(line.copy().withStyle(ChatFormatting.GRAY));
					}
				} else {
					// 装了 Enchantment Descriptions：简介由它用 <附魔ID>.desc 渲染，
					// 我们这边只提示"可以按 Shift 看数值明细"，避免同一个附魔出现两行描述
					lines.add(Component.translatable("tooltip.enchantment_reforged.desc.hint_shift")
							.withStyle(ChatFormatting.DARK_GRAY));
				}
			}
			return;
		}
		// 装备：默认只提示"按住 Shift"，按住后展开全部描述（数值跟着配置走）
		List<Component> description = EnchantmentDescriptions.describeEquipment(stack);
		if (!description.isEmpty()) {
			if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
				lines.add(Component.empty());
				for (Component line : description) {
					lines.add(line.copy().withStyle(ChatFormatting.GRAY));
				}
			} else {
				lines.add(Component.translatable("tooltip.enchantment_reforged.desc.hint_shift")
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		// 带忠诚的物品（主要是三叉戟）：这一行常显，方便一眼看出"不会再丢"
		if (EnchantmentReforgedConfig.get().enableLoyaltyRework
				&& EnchantmentHelper.getLoyalty(stack) > 0) {
			lines.add(Component.empty());
			lines.add(Component.translatable("tooltip.enchantment_reforged.desc.loyalty_rework")
					.withStyle(ChatFormatting.GRAY));
		}
	}

	private static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		EnchantmentEffects.tickInstinctiveRelease(client.player);
		// 进服后尽快上报粒子偏好；通道还没就绪就下一 tick 继续重试
		if (particlePreferenceDirty && sendParticlePreference()) {
			particlePreferenceDirty = false;
		}
	}

	private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		particlePreferenceDirty = true;
		serverSupportsLongDistanceParticles = false;
		if (usingServerConfig) {
			usingServerConfig = false;
			EnchantmentReforgedConfig.load();
		}
	}

	/** 服务端下发的整份配置：只改内存，不写本地文件 */
	public static void onServerConfig(String json) {
		EnchantmentReforgedConfig.applyRemote(json);
		usingServerConfig = true;
	}

	/** 服务端索要粒子偏好：能收到就说明通道通了，直接回报（不做额外探测） */
	public static void onParticleRequest(int protocolVersion) {
		serverSupportsLongDistanceParticles = protocolVersion >= ConfigSync.LONG_DISTANCE_PROTOCOL_VERSION;
		sendParticlePreferenceNow();
	}

	/** 配置页改动后调用：立刻尝试上报，失败则挂起并在客户端 tick 里重试 */
	public static void publishParticlePreference() {
		particlePreferenceDirty = true;
		if (sendParticlePreference()) {
			particlePreferenceDirty = false;
		}
	}

	/** 服务端是否支持远距离代发轨迹粒子（旧版服务端为 false，此时开关不生效） */
	public static boolean serverSupportsLongDistanceParticles() {
		return serverSupportsLongDistanceParticles;
	}

	private static boolean sendParticlePreference() {
		if (Minecraft.getInstance().getConnection() == null) {
			return false;
		}
		sendParticlePreferenceNow();
		return true;
	}

	/** 无条件发送当前粒子偏好（9 个字段，与 Fabric 版协议 8 一致） */
	private static void sendParticlePreferenceNow() {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		ConfigSync.sendParticlePreferenceToServer(
				config.arrowParticle, config.arrowParticleLongDistance, config.arrowParticleDistance,
				config.meleeParticle, config.meleeSweepParticle, config.meleeParticleEffect,
				config.surpriseParticle, config.deathParticle, config.nimbleStepsSound);
	}
}
