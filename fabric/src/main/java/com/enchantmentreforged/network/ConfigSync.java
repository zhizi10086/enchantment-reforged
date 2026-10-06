package com.enchantmentreforged.network;

import com.enchantmentreforged.EnchantmentReforged;
import com.enchantmentreforged.config.DodgeSoundStyle;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 → 客户端 的配置同步。
 *
 * <p>伤害、附魔上限这些都在服务端结算，客户端的药水提示与配置界面却读的是本端配置，
 * 两边不一致时会出现"提示写 +130%、实际却是 +200%"的情况。
 * 所以玩家一进服就把服务端配置发过去，客户端只在内存里应用（不改本地文件），
 * 断开连接后再恢复自己的配置。
 */
public final class ConfigSync {
	public static final Identifier CHANNEL = new Identifier(EnchantmentReforged.MOD_ID, "config_sync");
	/** 客户端 → 服务端：上报自己的箭矢粒子档位（每名玩家各自一份，不参与上面的整份配置同步） */
	public static final Identifier PARTICLE_CHANNEL = new Identifier(EnchantmentReforged.MOD_ID, "particle_style");
	/** 服务端 → 客户端：请上报粒子偏好；载荷是协议版本（客户端据此判断服务端是否支持远距离代发） */
	public static final Identifier PARTICLE_REQUEST_CHANNEL =
			new Identifier(EnchantmentReforged.MOD_ID, "particle_style_request");

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

	/** 服务端索要状态：玩家 UUID → { 上次索要的 tick, 已索要次数 } */
	private static final Map<UUID, int[]> REQUEST_STATE = new ConcurrentHashMap<>();

	private ConfigSync() {
	}

	/**
	 * 服务端侧：接收客户端上报的箭矢粒子档位，并注册"玩家下线时忘记档位"。
	 *
	 * <p>客户端没装本模组时不会发这个包，服务端也就没有该玩家的记录，
	 * 此时他射出的箭不带射手档位，观者按自己的设置渲染。
	 */
	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(PARTICLE_CHANNEL, (server, player, handler, buffer, responseSender) -> {
			int style = buffer.readVarInt();
			// 容错读取：旧版本客户端只发档位，缺的字段用默认值
			boolean longDistance = buffer.isReadable() && buffer.readBoolean();
			int distance = buffer.isReadable() ? buffer.readVarInt() : ParticlePreferences.MAX_DISTANCE;
			int meleeStyle = buffer.isReadable() ? buffer.readVarInt() : 0;
			int sweepStyle = buffer.isReadable() ? buffer.readVarInt() : 0;
			int meleeEffect = buffer.isReadable() ? buffer.readVarInt() : 1;
			int surpriseStyle = buffer.isReadable() ? buffer.readVarInt() : 2;
			int deathStyle = buffer.isReadable() ? buffer.readVarInt() : 12;
			int dodgeSound = buffer.isReadable() ? buffer.readVarInt() : DodgeSoundStyle.DEFAULT;
			ParticlePreferences.Preference preference =
					ParticlePreferences.set(player.getUuid(), style, longDistance, distance, meleeStyle, sweepStyle,
							meleeEffect, surpriseStyle, deathStyle, dodgeSound);
			REQUEST_STATE.remove(player.getUuid());
			if (preference != null) {
				EnchantmentReforged.LOGGER.info(
						"[Enchantment Reforged] 玩家 {} 的粒子档位 = 箭矢 {} / 近战 {} / 横扫 {} / 强度 {}"
								+ " / 出其不意 {} / 死亡 {} / 闪避音效 {}（远距离={}，半径={}）",
						player.getGameProfile().getName(), preference.style(), preference.meleeStyle(),
						preference.sweepStyle(), preference.meleeEffect(),
						preference.surpriseStyle(), preference.deathStyle(), preference.dodgeSound(),
						preference.longDistance() ? "开" : "关", preference.distance());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID playerId = handler.getPlayer().getUuid();
			ParticlePreferences.clear(playerId);
			REQUEST_STATE.remove(playerId);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ParticlePreferences.clearAll());
	}

	/**
	 * 服务端：请某位玩家上报粒子偏好（客户端没装本模组时不会发送）。
	 *
	 * @return 是否真的发出去了
	 */
	public static boolean requestParticlePreference(ServerPlayerEntity player) {
		if (!ServerPlayNetworking.canSend(player, PARTICLE_REQUEST_CHANNEL)) {
			return false;
		}
		PacketByteBuf buffer = PacketByteBufs.create();
		buffer.writeVarInt(PARTICLE_PROTOCOL_VERSION);
		ServerPlayNetworking.send(player, PARTICLE_REQUEST_CHANNEL, buffer);
		return true;
	}

	/**
	 * 服务端每 tick 调用：还没登记偏好的玩家每 5 秒重新索要一次，最多 6 次。
	 *
	 * <p>兜住"客户端主动上报时握手还没就绪"这类时序问题；对端是旧版本（没有应答逻辑）时
	 * 会在若干次之后安静放弃。
	 */
	public static void tickParticlePreferenceRequests(ServerPlayerEntity player, int serverTicks) {
		UUID playerId = player.getUuid();
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
		if (requestParticlePreference(player)) {
			state[0] = serverTicks;
			++state[1];
			if (state[1] == MAX_REQUEST_ATTEMPTS) {
				EnchantmentReforged.LOGGER.info(
						"[Enchantment Reforged] 未收到玩家 {} 的箭矢粒子偏好（对端可能是旧版本），"
								+ "其射出的箭将按观者自己的设置渲染",
						player.getGameProfile().getName());
			}
		}
	}

	/** 把服务端当前配置发给某个玩家 */
	public static void sendTo(ServerPlayerEntity player) {
		PacketByteBuf buffer = PacketByteBufs.create();
		buffer.writeString(EnchantmentReforgedConfig.get().toJsonString());
		ServerPlayNetworking.send(player, CHANNEL, buffer);
	}

	/** 广播给所有在线玩家（命令或界面改完配置后调用） */
	public static void broadcast(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			sendTo(player);
		}
	}
}
