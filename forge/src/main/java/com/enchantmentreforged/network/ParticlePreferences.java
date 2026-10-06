package com.enchantmentreforged.network;

import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.config.DodgeSoundStyle;
import com.enchantmentreforged.particle.MeleeParticleLevels;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端保存的"每名玩家的箭矢粒子偏好"（档位 + 远距离开关 + 半径）。
 *
 * <p>客户端进服与每次修改设置时上报，箭生成时由服务端写进箭本体并随出生包同步，
 * 于是别人的视角里能看到你选的粒子，而不会改变他们自己射出的箭的粒子。
 * 客户端也会加载这个类，但客户端里这张表始终是空的（客户端不接收、也不写入）。
 */
public final class ParticlePreferences {
	/** 半径下限（格） */
	public static final int MIN_DISTANCE = 16;
	/** 半径上限（格）：原版强制发粒子的硬上限就是 512 */
	public static final int MAX_DISTANCE = 512;

	/** 一名玩家的粒子偏好 */
	public record Preference(int style, boolean longDistance, int distance, int meleeStyle, int sweepStyle,
			int meleeEffect, int surpriseStyle, int deathStyle, int dodgeSound) {
	}

	private static final Map<UUID, Preference> PREFERENCES = new ConcurrentHashMap<>();
	/** 开启了远距离显示的玩家数量（用于"没人开启就整段跳过"的快速路径） */
	private static volatile int longDistanceUsers;

	private ParticlePreferences() {
	}

	/** 记录某名玩家的偏好（档位夹到 0~12，半径夹到 16~512） */
	public static Preference set(UUID playerId, int style, boolean longDistance, int distance, int meleeStyle,
			int sweepStyle, int meleeEffect, int surpriseStyle, int deathStyle, int dodgeSound) {
		if (playerId == null) {
			return null;
		}
		Preference preference = new Preference(
				Math.max(0, Math.min(ArrowParticleStyle.COUNT - 1, style)),
				longDistance,
				Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance)),
				Math.max(0, Math.min(ArrowParticleStyle.COUNT - 1, meleeStyle)),
				Math.max(0, Math.min(ArrowParticleStyle.COUNT - 1, sweepStyle)),
				Math.max(0, Math.min(MeleeParticleLevels.MAX, meleeEffect)),
				Math.max(0, Math.min(ArrowParticleStyle.COUNT - 1, surpriseStyle)),
				Math.max(0, Math.min(ArrowParticleStyle.COUNT - 1, deathStyle)),
				DodgeSoundStyle.clamp(dodgeSound));
		Preference previous = PREFERENCES.put(playerId, preference);
		updateLongDistanceCount(previous, preference);
		return preference;
	}

	/** 玩家下线时忘记他的偏好 */
	public static void clear(UUID playerId) {
		if (playerId != null) {
			updateLongDistanceCount(PREFERENCES.remove(playerId), null);
		}
	}

	/** 服务端停止时清空 */
	public static void clearAll() {
		PREFERENCES.clear();
		longDistanceUsers = 0;
	}

	/** 开启远距离显示的玩家数量 */
	public static int longDistanceUsers() {
		return longDistanceUsers;
	}

	private static void updateLongDistanceCount(Preference previous, Preference current) {
		boolean wasOn = previous != null && previous.longDistance();
		boolean isOn = current != null && current.longDistance();
		if (wasOn == isOn) {
			return;
		}
		// 只在服务端写入，单线程访问；用同步块保证计数与表内容一致
		synchronized (PREFERENCES) {
			longDistanceUsers += isOn ? 1 : -1;
			if (longDistanceUsers < 0) {
				longDistanceUsers = 0;
			}
		}
	}

	/** 该玩家的偏好；没有记录时返回 null */
	public static Preference preferenceOf(UUID playerId) {
		return playerId == null ? null : PREFERENCES.get(playerId);
	}

	/** 是否已经登记过这名玩家的偏好 */
	public static boolean isKnown(UUID playerId) {
		return playerId != null && PREFERENCES.containsKey(playerId);
	}

	/** 该玩家的档位；没有记录时返回 {@link ArrowParticleStyle#UNSET} */
	public static int styleOf(UUID playerId) {
		Preference preference = preferenceOf(playerId);
		return preference == null ? ArrowParticleStyle.UNSET : preference.style();
	}
}
