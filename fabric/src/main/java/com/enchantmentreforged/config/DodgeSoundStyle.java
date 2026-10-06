package com.enchantmentreforged.config;

import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * 灵动步伐"闪避音效"的档位表（0 = 关闭）。
 *
 * <p>档位是每名玩家各自的客户端设置，闪避判定在服务端发生时按"被攻击者上报的档位"
 * 在其位置播放，附近玩家都能听到；对端没上报时退回服务端本地的同名配置值。
 */
public final class DodgeSoundStyle {
	/** 档位数量（0 ~ COUNT-1） */
	public static final int COUNT = 9;
	/** 默认档位：幻术师镜影（最明显的"闪开"感） */
	public static final int DEFAULT = 3;

	/** 下标即档位；0 表示关闭 */
	private static final SoundEvent[] SOUNDS = {
			null,
			SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
			SoundEvents.ITEM_SHIELD_BLOCK,
			SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE,
			SoundEvents.ENTITY_ENDERMAN_TELEPORT,
			SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
			SoundEvents.BLOCK_BEACON_ACTIVATE,
			SoundEvents.ENTITY_PLAYER_LEVELUP,
			SoundEvents.ITEM_TOTEM_USE
	};

	private DodgeSoundStyle() {
	}

	/** 夹到合法档位 */
	public static int clamp(int index) {
		return Math.max(0, Math.min(COUNT - 1, index));
	}

	/** 该档位的音效；0（关闭）或越界时返回 null */
	public static SoundEvent soundAt(int index) {
		if (index <= 0 || index >= SOUNDS.length) {
			return null;
		}
		return SOUNDS[index];
	}

	/** 档位名的语言键（界面灰字与悬停列表共用） */
	public static String nameKey(int index) {
		return "text.enchantment_reforged.dodge_sound." + clamp(index);
	}
}
