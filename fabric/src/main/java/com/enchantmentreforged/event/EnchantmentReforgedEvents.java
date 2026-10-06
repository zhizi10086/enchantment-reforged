package com.enchantmentreforged.event;

import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.network.ConfigSync;
import com.enchantmentreforged.particle.MeleeParticles;
import com.enchantmentreforged.particle.DeathParticles;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 只需要事件、不需要 Mixin 的两个功能。
 *
 * <ul>
 *     <li>斩首：击杀结算时按几率掉落头颅（AFTER_DEATH）；</li>
 *     <li>本能释放：服务端每 tick 检查"是否拉满"，拉满就自动射出并重新蓄力。</li>
 * </ul>
 * 本能释放的客户端部分在 {@code EnchantmentReforgedClient} 里注册（保证动画同步）。
 */
public final class EnchantmentReforgedEvents {
	private EnchantmentReforgedEvents() {
	}

	public static void register() {
		// 灵魂加护：死亡结算前尝试原地复活；返回 false 表示"这次死亡被取消"
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				!EnchantmentEffects.trySoulGrace(entity));

		ServerLivingEntityEvents.AFTER_DEATH.register(EnchantmentEffects::onDeathForBeheading);
		// 玩家死亡时在原地爆发一团粒子（只对玩家生效）
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> DeathParticles.onPlayerDeath(victim));

		// 重生时清掉生命护盾残留（原版会把吸收值带到新角色身上）
		ServerPlayerEvents.AFTER_RESPAWN.register((newPlayer, oldPlayer, alive) ->
				EnchantmentEffects.clearLifeShield(newPlayer));

		// 断线时清掉忠诚投掷冷却的登记，避免长期服务器残留已下线的 UUID
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			EnchantmentEffects.clearLoyaltyCooldown(handler.getPlayer().getUuid());
			// 灵魂加护的冷却同样只在本次会话里有意义，断线即忘
			EnchantmentEffects.clearSoulGraceCooldown(handler.getPlayer().getUuid());
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// 首次 tick 自检一次：护盾属性是否真的挂到了玩家默认属性上
			EnchantmentEffects.verifyLifeShieldAttribute();
			// 近战粒子的多 tick 余晖队列
			MeleeParticles.tickBursts();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				EnchantmentEffects.tickInstinctiveRelease(player);
				// 忠诚改版：假三叉戟一回收/超时就撤掉投掷冷却
				EnchantmentEffects.tickLoyaltyCooldown(player);
				// 箭矢粒子偏好：没登记到的玩家定期重新索要（每 5 秒一次，最多 6 次）
				ConfigSync.tickParticlePreferenceRequests(player, server.getTicks());
				// 灵魂加护：每秒把"冷却效果"与服务端计时对齐（喝牛奶清掉后会自动回来）
				if (server.getTicks() % 20 == 0) {
					EnchantmentEffects.syncSoulGraceCooldown(player);
				}
			}
		});
	}
}
