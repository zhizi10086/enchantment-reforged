package com.summy.enchantment.event;

import com.summy.enchantment.combat.CombatFormulas;
import com.summy.enchantment.combat.ArrowTraits;
import com.summy.enchantment.combat.ElectricArc;
import com.summy.enchantment.combat.EnchantmentEffects;
import com.summy.enchantment.command.SummyEnchantmentCommand;
import com.summy.enchantment.network.ConfigSync;
import com.summy.enchantment.network.ParticlePreferences;
import com.summy.enchantment.particle.DeathParticles;
import com.summy.enchantment.particle.MeleeParticles;
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
public final class SummyEnchantmentEvents {
	private SummyEnchantmentEvents() {
	}

	public static void register() {
		MinecraftForge.EVENT_BUS.addListener(SummyEnchantmentEvents::onRegisterCommands);
		MinecraftForge.EVENT_BUS.addListener(SummyEnchantmentEvents::onLivingDeath);
		MinecraftForge.EVENT_BUS.addListener(SummyEnchantmentEvents::onPlayerClone);
		MinecraftForge.EVENT_BUS.addListener(SummyEnchantmentEvents::onPlayerLoggedIn);
		MinecraftForge.EVENT_BUS.addListener(SummyEnchantmentEvents::onPlayerLoggedOut);
		MinecraftForge.EVENT_BUS.addListener(SummyEnchantmentEvents::onEntityJoinLevel);
		MinecraftForge.EVENT_BUS.addListener(SummyEnchantmentEvents::onServerTick);
		MinecraftForge.EVENT_BUS.addListener(SummyEnchantmentEvents::onServerStopped);
	}

	private static void onRegisterCommands(RegisterCommandsEvent event) {
		SummyEnchantmentCommand.register(event.getDispatcher());
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
		// 死亡之眼：死亡即清空层数（重生后重新累计）
		ArrowTraits.clearPlayer(entity.getUUID());
		// 藏剑：死亡即清空蓄力
		EnchantmentEffects.clearHiddenBlade(entity.getUUID());
	}

	/** 重生（含死亡重生）：清掉生命护盾残留（原版会把吸收值带到新角色身上） */
	private static void onPlayerClone(PlayerEvent.Clone event) {
		if (event.isWasDeath()) {
			EnchantmentEffects.clearLifeShield(event.getEntity());
			// 藏剑：重生同样清零（死亡事件已清一次，这里做兜底）
			EnchantmentEffects.clearHiddenBlade(event.getEntity().getUUID());
		} else {
			// 非死亡换维度等路径：属性随玩家保留，但吸收池可能只剩残余值，重新对账。
			EnchantmentEffects.restoreLifeShieldToPool(event.getEntity());
		}
	}

	/** 进服：把服务端配置同步给客户端，并索要一次"每名玩家自己的粒子偏好" */
	private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ConfigSync.sendTo(player);
			ConfigSync.requestParticlePreference(player);
			// 重登后把存档里的生命护盾属性重新写回吸收池（属性与吸收值分开持久化，可能脱节）。
			EnchantmentEffects.restoreLifeShieldToPool(player);
		}
	}

	/** 断线：清掉忠诚投掷冷却与灵魂加护冷却的登记，并忘记粒子偏好 */
	private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			EnchantmentEffects.clearLoyaltyCooldown(player.getUUID());
			EnchantmentEffects.clearSoulGraceCooldown(player.getUUID());
			ParticlePreferences.clear(player.getUUID());
			// 死亡之眼：断线即清空层数（重登不保留）
			ArrowTraits.clearPlayer(player.getUUID());
			// 藏剑：断线同样清空
			EnchantmentEffects.clearHiddenBlade(player.getUUID());
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
		// 电刀：段与段之间的延迟队列
		ElectricArc.tick();
		int serverTicks = event.getServer().getTickCount();
		for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
			EnchantmentEffects.tickInstinctiveRelease(player);
			// 忠诚改版：假三叉戟一回收/超时就撤掉投掷冷却
			EnchantmentEffects.tickLoyaltyCooldown(player);
			// 箭矢粒子偏好：没登记到的玩家定期重新索要（每 5 秒一次，最多 6 次）
			ConfigSync.tickParticlePreferenceRequests(player, serverTicks);
			// 死亡之眼：层数满时在射手身边持续播提示粒子（状态式，与是否正在射击无关）
			EnchantmentEffects.tickDeathEyeFullHint(player);
			// 藏剑：累积蓄力（主手持剑才涨）与满额提示
			EnchantmentEffects.tickHiddenBlade(player);
			EnchantmentEffects.tickHiddenBladeFullHint(player);
			// 灵魂加护：每秒把"冷却效果"与服务端计时对齐
			if (serverTicks % 20 == 0) {
				EnchantmentEffects.syncSoulGraceCooldown(player);
			}
		}
	}

	private static void onServerStopped(ServerStoppedEvent event) {
		MeleeParticles.clearBursts();
		ParticlePreferences.clearAll();
		ArrowTraits.clearAll();
		EnchantmentEffects.clearHiddenBladeAll();
		ElectricArc.clearAll();
	}
}
