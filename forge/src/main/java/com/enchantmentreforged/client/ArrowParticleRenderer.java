package com.enchantmentreforged.client;

import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.particle.ArrowParticleCarrier;
import com.enchantmentreforged.particle.ArrowParticleTrails;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;

/**
 * 箭矢粒子的客户端渲染。
 *
 * <p>档位只取"箭上携带的射手档位"（别人射的箭就按别人选的显示）。
 * 箭上没有携带时（生物、发射器、指令生成、以及刚进服还没上报档位的玩家）
 * 一律按<b>原版默认</b>处理——不再回退到观者自己的设置，也就是"本模组对这支箭不介入"。
 */
public final class ArrowParticleRenderer {
	/** 每 tick 生成的轨迹粒子数量 */
	private static final int TRAIL_PARTICLES_PER_TICK = 2;

	private ArrowParticleRenderer() {
	}

	/** 这支箭实际使用的档位；没带射手档位时返回 0（原版默认 = 本模组不介入） */
	public static int styleOf(AbstractArrow arrow) {
		if (arrow instanceof ArrowParticleCarrier carrier) {
			int style = carrier.enchantmentReforged$getParticleStyle();
			if (style != ArrowParticleStyle.UNSET) {
				return style;
			}
		}
		return ArrowParticleStyle.VANILLA.index();
	}

	/**
	 * 是否屏蔽这支箭的原版粒子（暴击星、药水箭颜色漩涡、光灵箭闪粒、插地消散爆发）。
	 *
	 * <p>三叉戟等非箭类投掷物一律不介入；档位 0（原版默认）时也保持原版。
	 */
	public static boolean suppressesVanillaParticles(AbstractArrow arrow) {
		if (!isArrow(arrow)) {
			return false;
		}
		return ArrowParticleStyle.byIndex(styleOf(arrow)).suppressesVanillaParticles();
	}

	/** 生成飞行轨迹粒子（只在客户端调用；插在方块上的箭不再生成） */
	public static void spawnTrail(AbstractArrow arrow) {
		// 开启"远距离显示"且服务端支持时，轨迹完全由服务端代发（否则会出现重复的两份）
		if (EnchantmentReforgedConfig.get().arrowParticleLongDistance
				&& EnchantmentReforgedClient.serverSupportsLongDistanceParticles()) {
			return;
		}
		ArrowParticleStyle style = ArrowParticleStyle.byIndex(styleOf(arrow));
		ParticleOptions particle = style.particle();
		if (particle == null || !isArrow(arrow)) {
			return;
		}
		Vec3 velocity = arrow.getDeltaMovement();
		// 落地/插在方块上的箭速度会被清零，用它区分"还在飞"
		if (velocity.lengthSqr() < 1.0E-4D) {
			return;
		}
		// 沿用原版暴击星的表现：朝速度反方向拖一小段
		double offsetX = -velocity.x * 0.25D;
		double offsetY = -velocity.y * 0.25D + 0.05D;
		double offsetZ = -velocity.z * 0.25D;
		double y = (arrow.getY() + arrow.getBbHeight() * 0.6D);
		for (int i = 0; i < TRAIL_PARTICLES_PER_TICK; i++) {
			arrow.level().addParticle(particle, arrow.getX(), y, arrow.getZ(),
					offsetX, offsetY, offsetZ);
		}
	}

	/** 只作用于箭（含药水箭与光灵箭），三叉戟等其它投掷物不变 */
	private static boolean isArrow(AbstractArrow arrow) {
		return ArrowParticleTrails.isArrow(arrow);
	}
}
