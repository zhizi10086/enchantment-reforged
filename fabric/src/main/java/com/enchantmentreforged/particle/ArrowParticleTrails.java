package com.enchantmentreforged.particle;

import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.network.ParticlePreferences;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

/**
 * 箭矢粒子轨迹：服务端代发部分（"远距离显示"用）。
 *
 * <p>客户端只会同步到 64 格内的箭（实体同步范围），再远的箭客户端根本没有实体，
 * 本地渲染自然就断了。开启远距离显示后改由服务端逐玩家代发粒子，最远到原版强制
 * 发粒子的上限（512 格），这样远处的箭也能看到轨迹。
 */
public final class ArrowParticleTrails {
	/** 每 tick 给每位观者发送的粒子数量（与客户端本地渲染保持一致的观感） */
	private static final int PARTICLES_PER_TICK = 2;
	/** 速度低于这个平方值视为"停下/插在方块上"，不再发粒子 */
	private static final double MOVING_EPSILON = 1.0E-4D;

	/** 包含标签：命中就一定接手（整合包/模组可以把自己"不是继承箭基类"的投掷物登记进来，也能把默认被排除的加回来） */
	private static final TagKey<EntityType<?>> INCLUDE_TAG = TagKey.of(RegistryKeys.ENTITY_TYPE,
			new Identifier("enchantment_reforged", "arrow_particles"));
	/** 排除标签：命中就保持原版（整合包用它踢掉个别投掷物，例如某个模组的标枪） */
	private static final TagKey<EntityType<?>> EXCLUDE_TAG = TagKey.of(RegistryKeys.ENTITY_TYPE,
			new Identifier("enchantment_reforged", "no_arrow_particles"));
	/**
	 * 代码内固定排除的实体 id（不依赖标签）。
	 *
	 * <p>联机时客户端的数据包标签会被服务器下发的标签包整体覆盖，所以"我们本来就不想要的投掷物"
	 * 必须写在代码里才不会因为标签丢失而误伤；想反悔时把它们写进包含标签即可。
	 */
	private static final Set<String> ALWAYS_EXCLUDED_IDS = Set.of(
			"summy-reliquary:thrown_spear");

	private ArrowParticleTrails() {
	}

	/**
	 * 这支投掷物是否算"箭"（决定是否生成我们配置的轨迹粒子、以及是否屏蔽原版箭类粒子）。
	 *
	 * <p>判定优先级（1.3.5 起）：
	 * <ol>
	 *     <li>不是 {@code PersistentProjectileEntity} 家族的实体：本方法进不来，等同不介入；</li>
	 *     <li>命中包含标签 {@code #enchantment_reforged:arrow_particles} → 介入（可把默认排除的实体加回来）；</li>
	 *     <li>命中排除标签 {@code #enchantment_reforged:no_arrow_particles} → 不介入；</li>
	 *     <li>原版箭 / 光灵箭 → 介入（写死在代码里：联机时客户端标签会被服务器覆盖）；</li>
	 *     <li>代码内固定排除：原版三叉戟与 {@link #ALWAYS_EXCLUDED_IDS} 里的长矛类投掷物 → 不介入；</li>
	 *     <li>其余（Quark 火把箭、匠魂箭、冰火龙箭这类"继承箭基类但不是原版 Arrow"的模组箭）
	 *     → 由 {@code arrow_particle_other_projectiles} 开关决定，默认介入。</li>
	 * </ol>
	 */
	public static boolean isArrow(PersistentProjectileEntity arrow) {
		EntityType<?> type = arrow.getType();
		// 1) 包含标签优先：显式登记的一律介入
		if (type.isIn(INCLUDE_TAG)) {
			return true;
		}
		// 2) 排除标签：整合包显式踢掉的保持原版
		if (type.isIn(EXCLUDE_TAG)) {
			return false;
		}
		// 3) 原版两箭恒定介入
		if (type == EntityType.ARROW || type == EntityType.SPECTRAL_ARROW) {
			return true;
		}
		// 4) 代码内固定排除：原版三叉戟与已知长矛类投掷物
		if (arrow instanceof TridentEntity) {
			return false;
		}
		Identifier key = Registries.ENTITY_TYPE.getId(type);
		if (key != null && ALWAYS_EXCLUDED_IDS.contains(key.toString())) {
			return false;
		}
		// 5) 宽松兜底：参数类型本身就是"箭基类家族"，于是模组箭（继承该类但不是原版 Arrow）也能吃上
		return EnchantmentReforgedConfig.get().arrowParticleOtherProjectiles;
	}

	/**
	 * 服务端每 tick 对一支箭调用：给"开启了远距离显示"的观者代发轨迹粒子。
	 *
	 * <p>客户端调用时直接返回（客户端用本地渲染）。粒子类型只认箭上携带的射手档位：
	 * 没有携带（生物 / 发射器 / 指令生成 / 射手档位尚未上报）时整段跳过，不按观者档位代偿；
	 * 档位 0（原版默认）与 1（无粒子）也都不发。
	 */
	public static void tickServerTrail(PersistentProjectileEntity arrow) {
		if (arrow.getWorld().isClient || !isArrow(arrow)) {
			return;
		}
		// 快速路径：没有人开启"远距离显示"时整段跳过（不分配、不遍历玩家）
		if (ParticlePreferences.longDistanceUsers() == 0) {
			return;
		}
		Vec3d velocity = arrow.getVelocity();
		if (velocity.lengthSquared() < MOVING_EPSILON) {
			return;
		}
		if (!(arrow.getWorld() instanceof ServerWorld world)) {
			return;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return;
		}
		int arrowStyle = arrow instanceof ArrowParticleCarrier carrier
				? carrier.enchantmentReforged$getParticleStyle()
				: ArrowParticleStyle.UNSET;
		if (arrowStyle == ArrowParticleStyle.UNSET) {
			// 非玩家射出的箭（或射手档位未知）：本模组不介入，保持原版
			return;
		}
		// 与客户端本地渲染一致：位置沿速度反方向拖一小段
		Vec3d position = arrow.getPos().subtract(velocity.normalize().multiply(0.25D));
		for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
			if (viewer.getWorld() != world) {
				continue;
			}
			ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(viewer.getUuid());
			if (preference == null || !preference.longDistance()) {
				continue;
			}
			int style = arrowStyle;
			ParticleEffect particle = ArrowParticleStyle.byIndex(style).particle();
			if (particle == null) {
				continue;
			}
			double radius = preference.distance();
			if (viewer.squaredDistanceTo(position) > radius * radius) {
				continue;
			}
			// force = true：原版会把半径上限放到 512 格（同时也带上包里的 longDistance 标记）
			world.spawnParticles(viewer, particle, true, position.x, position.y, position.z,
					PARTICLES_PER_TICK, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}
}
