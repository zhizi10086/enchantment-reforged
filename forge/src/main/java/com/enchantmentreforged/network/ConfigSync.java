package com.enchantmentreforged.network;

import com.enchantmentreforged.EnchantmentReforged;
import com.enchantmentreforged.client.EnchantmentReforgedClient;
import com.enchantmentreforged.config.DodgeSoundStyle;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 配置同步与"每名玩家自己的粒子偏好"的网络层（Forge 版）。
 *
 * <p>与原 Fabric 版保持同样的三条通道与载荷：
 * <ul>
 *     <li>{@code config_sync}（S2C）：服务端把整份配置发给客户端，客户端只在内存里应用；</li>
 *     <li>{@code particle_style}（C2S）：客户端上报自己的 9 个粒子/音效档位字段；</li>
 *     <li>{@code particle_style_request}（S2C）：服务端索要偏好，载荷是协议版本。</li>
 * </ul>
 * 协议版本仍为 8；Forge 两侧都必须装本模组，因此不需要 Fabric 那套 canSend 探测。
 */
public final class ConfigSync {
	public static final ResourceLocation CHANNEL = new ResourceLocation(EnchantmentReforged.MOD_ID, "config_sync");
	/** 客户端 → 服务端：上报自己的箭矢粒子档位（每名玩家各自一份） */
	public static final ResourceLocation PARTICLE_CHANNEL = new ResourceLocation(EnchantmentReforged.MOD_ID, "particle_style");
	/** 服务端 → 客户端：请上报粒子偏好；载荷是协议版本（客户端据此判断服务端是否支持远距离代发） */
	public static final ResourceLocation PARTICLE_REQUEST_CHANNEL =
			new ResourceLocation(EnchantmentReforged.MOD_ID, "particle_style_request");

	/**
	 * 粒子上报协议版本：1 = 只有档位；2 = +远距离开关与半径；3 = +近战档位；4 = +横扫档位；
	 * 5 = +近战强度；7 = +出其不意档位与死亡粒子档位；8 = +闪避音效档位。
	 */
	public static final int PARTICLE_PROTOCOL_VERSION = 8;
	/** 支持"远距离代发"的最低协议版本 */
	public static final int LONG_DISTANCE_PROTOCOL_VERSION = 2;
	/** 注册表里没有记录时，每多少 tick 重新索要一次 */
	private static final int REQUEST_INTERVAL_TICKS = 100;
	/** 最多索要几次后放弃（客户端可能是旧版本，没有应答逻辑） */
	private static final int MAX_REQUEST_ATTEMPTS = 6;

	private static final String PROTOCOL_VERSION = Integer.toString(PARTICLE_PROTOCOL_VERSION);

	public static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder
			.named(new ResourceLocation(EnchantmentReforged.MOD_ID, "main"))
			.networkProtocolVersion(() -> PROTOCOL_VERSION)
			.clientAcceptedVersions(PROTOCOL_VERSION::equals)
			.serverAcceptedVersions(PROTOCOL_VERSION::equals)
			.simpleChannel();

	/** 服务端索要状态：玩家 UUID → { 上次索要的 tick, 已索要次数 } */
	private static final Map<UUID, int[]> REQUEST_STATE = new ConcurrentHashMap<>();

	private ConfigSync() {
	}

	/** 注册三条消息（模组构造时调用一次） */
	public static void registerServerReceivers() {
		int id = 0;
		INSTANCE.registerMessage(id++, ParticleStyleMsg.class, ParticleStyleMsg::encode, ParticleStyleMsg::decode,
				ConfigSync::handleParticleStyle);
		INSTANCE.registerMessage(id++, ConfigMsg.class, ConfigMsg::encode, ConfigMsg::decode, ConfigSync::handleConfig);
		INSTANCE.registerMessage(id++, ParticleRequestMsg.class, ParticleRequestMsg::encode,
				ParticleRequestMsg::decode, ConfigSync::handleParticleRequest);
	}

	private static void handleParticleStyle(ParticleStyleMsg msg, Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context context = contextSupplier.get();
		context.enqueueWork(() -> {
			ServerPlayer player = context.getSender();
			if (player == null) {
				return;
			}
			ParticlePreferences.Preference preference = ParticlePreferences.set(player.getUUID(), msg.style(),
					msg.longDistance(), msg.distance(), msg.meleeStyle(), msg.sweepStyle(), msg.meleeEffect(),
					msg.surpriseStyle(), msg.deathStyle(), msg.dodgeSound());
			REQUEST_STATE.remove(player.getUUID());
			if (preference != null) {
				EnchantmentReforged.LOGGER.info(
						"[Enchantment Reforged] 玩家 {} 的粒子档位 = 箭矢 {} / 近战 {} / 横扫 {} / 强度 {}"
								+ " / 出其不意 {} / 死亡 {} / 闪避音效 {}（远距离={}，半径={}）",
						player.getGameProfile().getName(), preference.style(), preference.meleeStyle(),
						preference.sweepStyle(), preference.meleeEffect(), preference.surpriseStyle(),
						preference.deathStyle(), preference.dodgeSound(),
						preference.longDistance() ? "开" : "关", preference.distance());
			}
		});
		context.setPacketHandled(true);
	}

	private static void handleConfig(ConfigMsg msg, Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context context = contextSupplier.get();
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> EnchantmentReforgedClient.onServerConfig(msg.json())));
		context.setPacketHandled(true);
	}

	private static void handleParticleRequest(ParticleRequestMsg msg, Supplier<NetworkEvent.Context> contextSupplier) {
		NetworkEvent.Context context = contextSupplier.get();
		context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
				() -> () -> EnchantmentReforgedClient.onParticleRequest(msg.protocolVersion())));
		context.setPacketHandled(true);
	}

	/** 服务端：请某位玩家上报粒子偏好 */
	public static void requestParticlePreference(ServerPlayer player) {
		INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new ParticleRequestMsg(PARTICLE_PROTOCOL_VERSION));
	}

	/** 服务端每 tick 调用：还没登记偏好的玩家每 5 秒重新索要一次，最多 6 次 */
	public static void tickParticlePreferenceRequests(ServerPlayer player, int serverTicks) {
		UUID playerId = player.getUUID();
		if (ParticlePreferences.isKnown(playerId)) {
			REQUEST_STATE.remove(playerId);
			return;
		}
		int[] state = REQUEST_STATE.computeIfAbsent(playerId, key -> new int[]{-1, 0});
		if (state[1] >= MAX_REQUEST_ATTEMPTS) {
			return;
		}
		if (state[0] >= 0 && serverTicks - state[0] < REQUEST_INTERVAL_TICKS) {
			return;
		}
		requestParticlePreference(player);
		state[0] = serverTicks;
		++state[1];
		if (state[1] == MAX_REQUEST_ATTEMPTS) {
			EnchantmentReforged.LOGGER.info(
					"[Enchantment Reforged] 未收到玩家 {} 的箭矢粒子偏好（对端可能是旧版本），"
							+ "其射出的箭将按原版处理",
					player.getGameProfile().getName());
		}
	}

	/** 把服务端当前配置发给某个玩家 */
	public static void sendTo(ServerPlayer player) {
		INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
				new ConfigMsg(EnchantmentReforgedConfig.get().toJsonString()));
	}

	/** 广播给所有在线玩家（命令或界面改完配置后调用） */
	public static void broadcast(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendTo(player);
		}
	}

	/** 客户端：上报自己的粒子偏好 */
	public static void sendParticlePreferenceToServer(int style, boolean longDistance, int distance, int meleeStyle,
			int sweepStyle, int meleeEffect, int surpriseStyle, int deathStyle, int dodgeSound) {
		INSTANCE.sendToServer(new ParticleStyleMsg(style, longDistance, distance, meleeStyle, sweepStyle,
				meleeEffect, surpriseStyle, deathStyle, DodgeSoundStyle.clamp(dodgeSound)));
	}

	// ==================== 载荷 ====================

	/** C2S：粒子偏好（9 个字段，与 Fabric 版协议 8 一致） */
	public record ParticleStyleMsg(int style, boolean longDistance, int distance, int meleeStyle, int sweepStyle,
			int meleeEffect, int surpriseStyle, int deathStyle, int dodgeSound) {
		static void encode(ParticleStyleMsg msg, FriendlyByteBuf buf) {
			buf.writeVarInt(msg.style());
			buf.writeBoolean(msg.longDistance());
			buf.writeVarInt(msg.distance());
			buf.writeVarInt(msg.meleeStyle());
			buf.writeVarInt(msg.sweepStyle());
			buf.writeVarInt(msg.meleeEffect());
			buf.writeVarInt(msg.surpriseStyle());
			buf.writeVarInt(msg.deathStyle());
			buf.writeVarInt(msg.dodgeSound());
		}

		static ParticleStyleMsg decode(FriendlyByteBuf buf) {
			return new ParticleStyleMsg(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
		}
	}

	/** S2C：整份配置（JSON 字符串） */
	public record ConfigMsg(String json) {
		static void encode(ConfigMsg msg, FriendlyByteBuf buf) {
			buf.writeUtf(msg.json(), 262144);
		}

		static ConfigMsg decode(FriendlyByteBuf buf) {
			return new ConfigMsg(buf.readUtf(262144));
		}
	}

	/** S2C：索要粒子偏好 + 告知协议版本 */
	public record ParticleRequestMsg(int protocolVersion) {
		static void encode(ParticleRequestMsg msg, FriendlyByteBuf buf) {
			buf.writeVarInt(msg.protocolVersion());
		}

		static ParticleRequestMsg decode(FriendlyByteBuf buf) {
			return new ParticleRequestMsg(buf.readVarInt());
		}
	}
}
