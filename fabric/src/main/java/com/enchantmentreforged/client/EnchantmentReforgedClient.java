package com.enchantmentreforged.client;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.network.ConfigSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

/**
 * 客户端初始化：只做与显示/本地设置有关的事（不依赖 Mod Menu）。
 *
 * <ul>
 *     <li>给"带原版力量的药水/喷溅药水/滞留药水/药箭"追加一行说明；</li>
 *     <li>接受服务端同步的配置（只改内存，退出后恢复本地文件）；</li>
 *     <li>把"每名玩家自己的箭矢粒子偏好"（档位 + 远距离 + 半径）上报给服务端，
 *         并在服务端索要时无条件回报。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class EnchantmentReforgedClient implements ClientModInitializer {
	/** 是否正在使用服务端同步来的配置（断开连接时用来决定要不要恢复本地配置） */
	private static boolean usingServerConfig;
	/** 粒子偏好是否还没成功上报（改设置或进服时置位，成功发送后清零） */
	private static boolean particlePreferenceDirty = true;
	/** 服务端是否支持"远距离代发轨迹粒子"（收到协议版本 ≥ 2 的索要包时置位） */
	private static boolean serverSupportsLongDistanceParticles;

	@Override
	public void onInitializeClient() {
		// 本能释放：客户端也做同样的检测，保证拉弓动画与使用状态即时结束/重开
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null) {
				EnchantmentEffects.tickInstinctiveRelease(client.player);
				// 进服后尽快上报粒子偏好；通道还没就绪就下一 tick 继续重试
				if (particlePreferenceDirty && sendParticlePreference()) {
					particlePreferenceDirty = false;
				}
			}
		});

		// 服务端主动索要粒子偏好：能收到就说明通道通了，直接回报（不做 canSend 门槛）
		ClientPlayNetworking.registerGlobalReceiver(ConfigSync.PARTICLE_REQUEST_CHANNEL,
				(client, handler, buffer, responseSender) -> {
					int protocolVersion = buffer.readVarInt();
					client.execute(() -> {
						serverSupportsLongDistanceParticles =
								protocolVersion >= ConfigSync.LONG_DISTANCE_PROTOCOL_VERSION;
						sendParticlePreferenceNow();
					});
				});

		// 接收服务端同步的配置：只改内存，不写本地文件
		ClientPlayNetworking.registerGlobalReceiver(ConfigSync.CHANNEL, (client, handler, buffer, responseSender) -> {
			String json = buffer.readString();
			client.execute(() -> {
				EnchantmentReforgedConfig.applyRemote(json);
				usingServerConfig = true;
			});
		});

		// 离开服务器/世界后，恢复成本地配置文件里的设置
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			particlePreferenceDirty = true;
			serverSupportsLongDistanceParticles = false;
			if (usingServerConfig) {
				usingServerConfig = false;
				EnchantmentReforgedConfig.load();
			}
		}));

		ItemTooltipCallback.EVENT.register((stack, tooltipContext, lines) -> {
			// 附魔书：追加本模组附魔的说明（数值跟着配置走）
			if (stack.isOf(Items.ENCHANTED_BOOK)) {
				List<Text> description = EnchantmentDescriptions.describe(stack);
				if (!description.isEmpty()) {
					lines.add(Text.empty());
					for (Text line : description) {
						lines.add(line.copy().formatted(Formatting.GRAY));
					}
				}
			} else {
				// 装备：默认只提示"按住 Shift"，按住后展开全部描述（数值跟着配置走）
				List<Text> description = EnchantmentDescriptions.describeEquipment(stack);
				if (!description.isEmpty()) {
					if (Screen.hasShiftDown()) {
						lines.add(Text.empty());
						for (Text line : description) {
							lines.add(line.copy().formatted(Formatting.GRAY));
						}
					} else {
						lines.add(Text.translatable("tooltip.enchantment_reforged.desc.hint_shift")
								.formatted(Formatting.DARK_GRAY));
					}
				}
				// 带忠诚的物品（主要是三叉戟）：这一行常显，方便一眼看出"不会再丢"
				if (EnchantmentReforgedConfig.get().enableLoyaltyRework
						&& EnchantmentHelper.getLoyalty(stack) > 0) {
					lines.add(Text.empty());
					lines.add(Text.translatable("tooltip.enchantment_reforged.desc.loyalty_rework")
							.formatted(Formatting.GRAY));
				}
			}

			// 不死图腾：说明"不死者加护"这个被动效果（手持时生效、只算一层）
			if (EnchantmentReforgedConfig.get().enableUndyingGrace && stack.isOf(Items.TOTEM_OF_UNDYING)) {
				lines.add(Text.empty());
				lines.add(Text.translatable("tooltip.enchantment_reforged.undying_grace",
						formatValue(EnchantmentReforgedConfig.get().undyingGraceDamageReduction))
						.formatted(Formatting.GRAY));
			}

			if (!EnchantmentReforgedConfig.get().enableStrengthRework) {
				return;
			}
			// 任何"携带药水效果且包含力量"的物品都会被补上说明（药水、喷溅、滞留、药箭，以及其它模组的同类物品）
			List<StatusEffectInstance> effects = PotionUtil.getPotionEffects(stack);
			for (StatusEffectInstance effect : effects) {
				if (effect.getEffectType() == StatusEffects.STRENGTH) {
					// 百分比跟着配置走：配置 1.3 -> +130%，配置 2 -> +200%
					String percent = formatPercent(EnchantmentReforgedConfig.get().strengthMultiplierPerLevel);
					lines.add(Text.translatable("tooltip.enchantment_reforged.strength_potion", percent)
							.formatted(Formatting.GRAY));
					return;
				}
			}
		});
	}

	/** 把"每级倍率增量"格式化成 +130% 这样的文本（整数不带小数点） */
	private static String formatPercent(float perLevel) {
		double percent = perLevel * 100.0D;
		String number;
		if (Math.abs(percent - Math.round(percent)) < 1.0E-6D) {
			number = Long.toString(Math.round(percent));
		} else {
			number = String.format(Locale.ROOT, "%.1f", percent);
		}
		return "+" + number + "%";
	}

	/** 数值文本：整数不带小数点，小数去掉多余的 0（例如 1 -> "1"、1.5 -> "1.5"） */
	private static String formatValue(float value) {
		String text = String.format(Locale.ROOT, "%.3f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}

	/**
	 * 上报自己的箭矢粒子偏好（档位 + 远距离开关 + 半径，每名玩家各一份）。
	 *
	 * <p>服务端没装本模组（通道还没通）时返回 false，调用方会稍后重试。
	 */
	public static boolean sendParticlePreference() {
		if (!ClientPlayNetworking.canSend(ConfigSync.PARTICLE_CHANNEL)) {
			return false;
		}
		sendParticlePreferenceNow();
		return true;
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

	/** 无条件发送当前粒子偏好 */
	private static void sendParticlePreferenceNow() {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		PacketByteBuf buffer = PacketByteBufs.create();
		buffer.writeVarInt(config.arrowParticle);
		buffer.writeBoolean(config.arrowParticleLongDistance);
		buffer.writeVarInt(config.arrowParticleDistance);
		buffer.writeVarInt(config.meleeParticle);
		buffer.writeVarInt(config.meleeSweepParticle);
		buffer.writeVarInt(config.meleeParticleEffect);
		buffer.writeVarInt(config.surpriseParticle);
		buffer.writeVarInt(config.deathParticle);
		buffer.writeVarInt(config.nimbleStepsSound);
		ClientPlayNetworking.send(ConfigSync.PARTICLE_CHANNEL, buffer);
	}
}
