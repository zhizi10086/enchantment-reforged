package com.enchantmentreforged.event;

import com.enchantmentreforged.combat.CombatFormulas;
import com.enchantmentreforged.combat.EnchantmentEffects;
import com.enchantmentreforged.command.EnchantmentReforgedCommand;
import com.enchantmentreforged.network.ConfigSync;
import com.enchantmentreforged.network.ParticlePreferences;
import com.enchantmentreforged.particle.DeathParticles;
import com.enchantmentreforged.particle.MeleeParticles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * 只需要事件、不需要 Mixin 的部分（Forge 版）。
 *
 * <ul>
 *     <li>灵魂加护：死亡结算前尝试原地复活（取消 {@link LivingDeathEvent} 即免死）；</li>
 *     <li>斩首：死亡结算时按几率掉落头颅；玩家死亡粒子；</li>
 *     <li>命令注册、进服同步配置并索要粒子偏好、断线清理、每 tick 维护。</li>
 * </ul>
 */
public final class EnchantmentReforgedEvents {
	private EnchantmentReforgedEvents() {
	}

	public static void register() {
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedEvents::onRegisterCommands);
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedEvents::onLivingDeath);
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedEvents::onPlayerClone);
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedEvents::onPlayerLoggedIn);
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedEvents::onPlayerLoggedOut);
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedEvents::onEntityJoinLevel);
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedEvents::onServerTick);
		MinecraftForge.EVENT_BUS.addListener(EnchantmentReforgedEvents::onServerStopped);
	}

	private static void onRegisterCommands(RegisterCommandsEvent event) {
		EnchantmentReforgedCommand.register(event.getDispatcher());
	}

	/** 灵魂加护（取消死亡）+ 斩首与玩家死亡粒子（真正死亡时） */
	private static void onLivingDeath(LivingDeathEvent event) {
		LivingEntity entity = event.getEntity();
		if (EnchantmentEffects.trySoulGrace(entity)) {
			event.setCanceled(true);
			return;
		}
		EnchantmentEffects.onDeathForBeheading(entity, event.getSource());
		DeathParticles.onPlayerDeath(entity);
	}

	/** 重生（含死亡重生）：清掉生命护盾残留（原版会把吸收值带到新角色身上） */
	private static void onPlayerClone(PlayerEvent.Clone event) {
		if (event.isWasDeath()) {
			EnchantmentEffects.clearLifeShield(event.getEntity());
		}
	}

	/** 进服：把服务端配置同步给客户端，并索要一次"每名玩家自己的粒子偏好" */
	private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ConfigSync.sendTo(player);
			ConfigSync.requestParticlePreference(player);
		}
	}

	/** 断线：清掉忠诚投掷冷却与灵魂加护冷却的登记，并忘记粒子偏好 */
	private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			EnchantmentEffects.clearLoyaltyCooldown(player.getUUID());
			EnchantmentEffects.clearSoulGraceCooldown(player.getUUID());
			ParticlePreferences.clear(player.getUUID());
		}
	}

	/** 旧存档 / 旧药水可能已经带有原版力量，实体加载时替换成自定义力量 */
	private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
		if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living) {
			CombatFormulas.replaceVanillaStrength(living);
		}
	}

	private static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		// 首次 tick 自检一次：护盾属性是否真的挂到了玩家身上
		EnchantmentEffects.verifyLifeShieldAttribute();
		// 近战粒子的多 tick 余晖队列
		MeleeParticles.tickBursts();
		int serverTicks = event.getServer().getTickCount();
		for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
			EnchantmentEffects.tickInstinctiveRelease(player);
			// 忠诚改版：假三叉戟一回收/超时就撤掉投掷冷却
			EnchantmentEffects.tickLoyaltyCooldown(player);
			// 箭矢粒子偏好：没登记到的玩家定期重新索要（每 5 秒一次，最多 6 次）
			ConfigSync.tickParticlePreferenceRequests(player, serverTicks);
			// 灵魂加护：每秒把"冷却效果"与服务端计时对齐
			if (serverTicks % 20 == 0) {
				EnchantmentEffects.syncSoulGraceCooldown(player);
			}
		}
	}

	private static void onServerStopped(ServerStoppedEvent event) {
		MeleeParticles.clearBursts();
		ParticlePreferences.clearAll();
	}
}
