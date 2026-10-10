package com.enchantmentreforged.combat;

import com.enchantmentreforged.EnchantmentReforged;
import com.enchantmentreforged.config.DodgeSoundStyle;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.network.ParticlePreferences;
import com.enchantmentreforged.particle.ArrowParticleCarrier;
import com.enchantmentreforged.registry.ModAttributes;
import com.enchantmentreforged.registry.ModEnchantments;
import com.enchantmentreforged.registry.ModStatusEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 13 个自定义附魔的实际行为。
 *
 * <p>所有入口（mixin / 事件）都只是转发到这里，便于集中维护与排查。
 */
public final class EnchantmentEffects {
	/** 生命提升的属性修饰符 id */
	private static final UUID HEALTH_BOOST_MODIFIER_ID = UUID.fromString("3f2a9c14-6b7d-4c8e-9a1b-2d5e7f809a01");
	/** 迅捷打击的属性修饰符 id */
	private static final UUID QUICK_STRIKE_MODIFIER_ID = UUID.fromString("7c1d4e28-9a3b-4f56-8c7d-1e2f3a4b5c02");
	/** 死者之心的最大生命修饰符 id */
	private static final UUID DEAD_MANS_HEART_MODIFIER_ID = UUID.fromString("9b7e5c31-4a2d-4f80-b3c6-8d1e2f304a03");
	/** 复仇窗口：玩家 UUID → 窗口结束的世界 tick */
	private static final Map<UUID, Long> REVENGE_WINDOW = new ConcurrentHashMap<>();
	/** 灵魂加护冷却：玩家 UUID → 可再次触发的世界 tick */
	private static final Map<UUID, Long> SOUL_GRACE_COOLDOWN = new ConcurrentHashMap<>();
	/** 斩杀的伤害类型 id（用于专属死亡信息） */
	private static final ResourceLocation EXECUTION_DAMAGE_ID = EnchantmentReforged.id("execution");
	/** 生命护盾记账缓存：每个实体上一次"预期的吸收总量" */
	/** 生命护盾的记账表：单人世界里客户端与服务端线程共用这个 JVM，所以要线程安全 */
	private static final Map<LivingEntity, Float> SHIELD_TRACK =
			Collections.synchronizedMap(new WeakHashMap<>());

	/** 四件盔甲的槽位（提成常量，避免每次调用都分配数组） */
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** 哪些玩家身上的三叉戟冷却是本模组设置的（只清理自己设置的，不碰其它来源） */
	private static final Set<UUID> LOYALTY_COOLDOWN_OWNERS = new HashSet<>();

	private EnchantmentEffects() {
	}

	// ==================== 装备附魔等级 ====================

	public static int mainHandLevel(LivingEntity entity, Enchantment enchantment) {
		if (entity.tickCount <= 0) {
			return 0;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(enchantment, entity.getMainHandItem());
	}

	public static int chestLevel(LivingEntity entity, Enchantment enchantment) {
		if (entity.tickCount <= 0) {
			return 0;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(enchantment, entity.getItemBySlot(EquipmentSlot.CHEST));
	}

	public static int helmetLevel(LivingEntity entity, Enchantment enchantment) {
		if (entity.tickCount <= 0) {
			return 0;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(enchantment, entity.getItemBySlot(EquipmentSlot.HEAD));
	}

	/** 4 件盔甲的等级之和（生命提升用） */
	public static int armorLevelSum(LivingEntity entity, Enchantment enchantment) {
		if (entity.tickCount <= 0) {
			// 构造期间装备容器尚未建立，直接视为没有附魔
			return 0;
		}
		int total = 0;
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			total += EnchantmentHelper.getItemEnchantmentLevel(enchantment, entity.getItemBySlot(slot));
		}
		return total;
	}

	/**
	 * 指定物品上的附魔等级。
	 *
	 * <p>近战取攻击者主手，投掷三叉戟取"三叉戟本体"（此时主手未必拿着它），
	 * 所以战斗入口统一用这个方法取等级，而不是一律读主手。
	 */
	public static int levelOf(ItemStack stack, Enchantment enchantment) {
		return stack == null || stack.isEmpty() ? 0 : EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
	}

	// ==================== 死神祝福 ====================

	/** 已损失生命百分比（0~100） */
	public static float healthLossPercent(LivingEntity entity) {
		float max = entity.getMaxHealth();
		if (max <= 0.0F) {
			return 0.0F;
		}
		float ratio = Math.max(0.0F, Math.min(1.0F, entity.getHealth() / max));
		return (1.0F - ratio) * 100.0F;
	}

	private static boolean hasDeathsBlessing(LivingEntity entity) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		return config.enableDeathsBlessing && ModEnchantments.DEATHS_BLESSING != null
				&& chestLevel(entity, ModEnchantments.DEATHS_BLESSING) > 0;
	}

	/** 死神祝福：造成伤害倍率 */
	public static float deathsBlessingOutgoing(LivingEntity attacker) {
		return hasDeathsBlessing(attacker) ? deathsBlessingOutgoingAt(healthLossPercent(attacker)) : 1.0F;
	}

	/** 死神祝福：受到伤害倍率（与其它来源同乘区） */
	public static float deathsBlessingIncoming(LivingEntity victim) {
		return hasDeathsBlessing(victim) ? deathsBlessingIncomingAt(healthLossPercent(victim)) : 1.0F;
	}

	/** 死神祝福：按"已损失生命百分比"换算造成伤害倍率（战斗与附魔描述共用这一处公式） */
	public static float deathsBlessingOutgoingAt(float lossPercent) {
		return 1.0F + EnchantmentReforgedConfig.get().deathsBlessingDamageBonusPerPercent * lossPercent;
	}

	/** 死神祝福：按"已损失生命百分比"换算受到伤害倍率（下限 0.05，与战斗一致） */
	public static float deathsBlessingIncomingAt(float lossPercent) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		float multiplier = 1.0F + config.deathsBlessingDamageTakenBonus
				- config.deathsBlessingResistPerPercent * lossPercent;
		return Math.max(0.05F, multiplier);
	}

	// ==================== 魔剑 / 嗜血 ====================

	/** 魔剑：近战入口，等级取自攻击者主手 */
	public static void applySpellblade(LivingEntity attacker, Entity target, float dealtDamage) {
		applySpellblade(attacker, attacker.getMainHandItem(), target, dealtDamage);
	}

	/**
	 * 魔剑：按本次最终伤害的百分比追加一次魔法伤害。
	 *
	 * <p>{@code sourceStack} 决定附魔等级：近战传主手，投掷三叉戟传三叉戟本体，
	 * 这样三叉戟投出去命中也能吃到魔剑加成。
	 */
	public static void applySpellblade(LivingEntity attacker, ItemStack sourceStack, Entity target, float dealtDamage) {
		applySpellbladeReport(attacker, sourceStack, target, dealtDamage);
	}

	/**
	 * 魔剑：与 {@link #applySpellblade} 完全同一口径，但返回**实际落地的追加魔法伤害**
	 * （0 = 没触发、或目标已死没落地）。近战结算与 `[ER-SR]` 诊断日志用它。
	 */
	public static float applySpellbladeReport(LivingEntity attacker, ItemStack sourceStack, Entity target,
			float dealtDamage) {
		float magic = spellbladeMagic(sourceStack, dealtDamage);
		if (magic <= 0.0F) {
			return 0.0F;
		}
		DamageSource source = attacker.level().damageSources().indirectMagic(attacker, attacker);
		// 同一 tick 内追加的伤害会被主伤害留下的受击冷却吞掉（只会按差值生效甚至完全忽略），
		// 所以先把目标的冷却清零，保证附加魔法伤害真的落地。
		if (target instanceof LivingEntity livingTarget) {
			livingTarget.invulnerableTime = 0;
		}
		return target.hurt(source, magic) ? magic : 0.0F;
	}

	/** 魔剑本次应当追加的魔法伤害（0 = 开关关闭 / 武器没该附魔 / 伤害为 0）；诊断日志也用它 */
	public static float spellbladeMagic(ItemStack sourceStack, float dealtDamage) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableSpellblade || ModEnchantments.SPELLBLADE == null) {
			return 0.0F;
		}
		int level = levelOf(sourceStack, ModEnchantments.SPELLBLADE);
		if (level <= 0) {
			return 0.0F;
		}
		return Math.max(0.0F, dealtDamage) * config.spellbladeMagicPercentPerLevel * level;
	}

	/** 嗜血：近战入口，等级取自攻击者主手 */
	public static void applyLifesteal(LivingEntity attacker, float dealtDamage) {
		applyLifesteal(attacker, attacker.getMainHandItem(), dealtDamage);
	}

	/** 嗜血：按造成的伤害回复生命（{@code sourceStack} 决定等级，投掷三叉戟传本体） */
	public static void applyLifesteal(LivingEntity attacker, ItemStack sourceStack, float dealtDamage) {
		applyLifestealReport(attacker, sourceStack, dealtDamage);
	}

	/** 嗜血：与 {@link #applyLifesteal} 完全同一口径，返回本次实际回复量（0 = 没触发） */
	public static float applyLifestealReport(LivingEntity attacker, ItemStack sourceStack, float dealtDamage) {
		float heal = lifestealHeal(sourceStack, dealtDamage);
		if (heal <= 0.0F) {
			return 0.0F;
		}
		attacker.heal(heal);
		return heal;
	}

	/** 嗜血本次应当回复的生命（0 = 开关关闭 / 武器没该附魔 / 伤害为 0）；诊断日志也用它 */
	public static float lifestealHeal(ItemStack sourceStack, float dealtDamage) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableLifesteal || ModEnchantments.LIFESTEAL == null) {
			return 0.0F;
		}
		int level = levelOf(sourceStack, ModEnchantments.LIFESTEAL);
		if (level <= 0) {
			return 0.0F;
		}
		return Math.max(0.0F, dealtDamage) * config.lifestealPercentPerLevel * level;
	}

	// ==================== 忠诚改版 ====================

	/** 忠诚改版是否对该三叉戟生效（配置开启 + 三叉戟带忠诚） */
	public static boolean isLoyaltyReworkActive(ItemStack stack) {
		return EnchantmentReforgedConfig.get().enableLoyaltyRework
				&& !stack.isEmpty()
				&& EnchantmentHelper.getLoyalty(stack) > 0;
	}

	/**
	 * 把刚投出的三叉戟标成"假三叉戟"。
	 *
	 * <p>同时把拾取权限改成 DISALLOWED：主人死亡、卡进方块等分支本来会掉落一份复制品
	 * （本体还在物品栏里），改掉权限后这些分支只会让它消失。
	 */
	public static void markPhantomTrident(ThrownTrident trident) {
		if (trident instanceof PhantomTrident phantom) {
			phantom.enchantmentReforged$setPhantom(true);
		}
		trident.pickup = AbstractArrow.Pickup.DISALLOWED;
	}

	/**
	 * 玩家是否还有"正在返回途中"的假三叉戟。
	 *
	 * <p>忠诚改版下，投掷出去的必然是假三叉戟，所以"忠诚三叉戟在飞"就等于
	 * "假三叉戟还没回来"，用它拦截再次投掷。
	 *
	 * <p>三条判定缺一不可，目的都是"保证最终一定放行"：
	 * <ul>
	 *     <li>范围限制在 64 格：与实体同步范围一致，避免客户端允许、服务端拒绝；</li>
	 *     <li>只认飞行时间在 {@link #PHANTOM_BLOCK_TICKS} 以内的：超时一律放行；</li>
	 *     <li>忠诚数据走同步字段：两端结论一致。</li>
	 * </ul>
	 */
	public static boolean hasFlyingLoyalTrident(Player player) {
		AABB box = player.getBoundingBox().inflate(PHANTOM_SEARCH_RANGE);
		for (ThrownTrident trident : player.level().getEntitiesOfClass(ThrownTrident.class, box, entity -> true)) {
			if (trident.isRemoved() || trident.getOwner() != player) {
				continue;
			}
			if (trident instanceof PhantomTrident phantom
					&& phantom.enchantmentReforged$isLoyal()
					&& phantom.enchantmentReforged$flightTicks() <= PHANTOM_BLOCK_TICKS) {
				return true;
			}
		}
		return false;
	}

	/** 拦截投掷的最长时间（30 秒）：超过它就一定是异常情况，必须放行 */
	public static final int PHANTOM_BLOCK_TICKS = 600;

	/** 假三叉戟的总寿命上限（60 秒）：超过就强制清除，避免孤儿实体永久堆积 */
	public static final int PHANTOM_CLEANUP_TICKS = 1200;

	/**
	 * 搜索半径（格）。
	 *
	 * <p>原版 {@code EntityType.TRIDENT} 的同步范围是 4 区块（64 格）：
	 * 用同样的半径才能让客户端与服务端看到同一批实体，判定不会打架。
	 */
	private static final double PHANTOM_SEARCH_RANGE = 64.0D;

	/**
	 * 给投出假三叉戟的玩家挂上三叉戟冷却，让"当前无法投掷"可见。
	 *
	 * <p>用的是原版物品冷却：服务端挂上后会自动同步给客户端（快捷栏出现灰色冷却圈），
	 * 且原版 {@code ServerPlayerInteractionManager.interactItem} 在冷却期间会直接拒绝
	 * 右键使用物品。冷却期间近战与挖掘不受影响（原版只拦物品的主动使用）。
	 */
	public static void setLoyaltyThrowCooldown(Player player) {
		if (player.level().isClientSide) {
			return;
		}
		player.getCooldowns().addCooldown(Items.TRIDENT, PHANTOM_BLOCK_TICKS);
		LOYALTY_COOLDOWN_OWNERS.add(player.getUUID());
	}

	/**
	 * 每 tick 维护投掷冷却（服务端）。
	 *
	 * <p>只要假三叉戟已经回收或超时，或者开关被关掉，就立刻撤掉冷却，
	 * 保证"能投掷的时候一定没有冷却圈"。
	 */
	public static void tickLoyaltyCooldown(Player player) {
		if (!LOYALTY_COOLDOWN_OWNERS.contains(player.getUUID())) {
			return;
		}
		if (EnchantmentReforgedConfig.get().enableLoyaltyRework && hasFlyingLoyalTrident(player)) {
			return;
		}
		player.getCooldowns().removeCooldown(Items.TRIDENT);
		LOYALTY_COOLDOWN_OWNERS.remove(player.getUUID());
	}

	/** 玩家断线时清掉冷却登记（否则长期服务器上会缓慢残留已下线的 UUID） */
	public static void clearLoyaltyCooldown(UUID playerId) {
		LOYALTY_COOLDOWN_OWNERS.remove(playerId);
	}

	// ==================== 生命护盾 ====================

	// ==================== 生命修补 ====================

	/** 生命修补可作用的装备槽（只修"带附魔的那件"） */
	private static final EquipmentSlot[] LIFE_MENDING_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};

	/**
	 * 生命修补：按"本次治疗量"修复"带该附魔的那件"装备的耐久。
	 *
	 * <p>受伤时调用方传的是<b>实际回复量</b>，满血时传的是<b>本次治疗量</b>（满血时前者为 0），
	 * 所以这个参数统一叫"本次治疗量"。
	 *
	 * @return 实际修复的总耐久点数（0 表示什么都没修，调用方据此决定是否阻断这次治疗）
	 */
	public static int repairWithLifeMending(LivingEntity entity, float healAmount) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableLifeMending || ModEnchantments.LIFE_MENDING == null || healAmount <= 0.0F) {
			return 0;
		}
		int repaired = 0;
		for (EquipmentSlot slot : LIFE_MENDING_SLOTS) {
			ItemStack stack = entity.getItemBySlot(slot);
			if (stack.isEmpty() || !stack.isDamageableItem() || stack.getDamageValue() <= 0) {
				continue;
			}
			int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.LIFE_MENDING, stack);
			if (level <= 0) {
				continue;
			}
			// 每 1 点治疗量回"每级数值 × 等级"点耐久
			int amount = (int) Math.floor(healAmount * config.lifeMendingDurabilityPerLevel * level);
			if (amount <= 0) {
				continue;
			}
			int before = stack.getDamageValue();
			stack.setDamageValue(Math.max(0, before - amount));
			repaired += before - stack.getDamageValue();
		}
		return repaired;
	}

	// ==================== 坚甲 ====================

	/** 坚甲：这一件装备单次允许的耐久损耗上限（没附魔时返回 Integer.MAX_VALUE） */
	public static int sturdyCap(ItemStack stack) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableSturdy || ModEnchantments.STURDY == null || stack.isEmpty()) {
			return Integer.MAX_VALUE;
		}
		int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.STURDY, stack);
		if (level <= 0) {
			return Integer.MAX_VALUE;
		}
		return sturdyCapFor(stack.getMaxDamage(), level);
	}

	/**
	 * 坚甲上限：{@code max(1, floor(最大耐久 × 等级系数))}。
	 *
	 * <p>等级系数 = 基准 − 每级递减×(等级−1)，默认 5%/3%/1%。
	 */
	public static int sturdyCapFor(int maxDamage, int level) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		float coefficient = config.sturdyCoefficient - config.sturdyCoefficientStep * (level - 1);
		float cap = maxDamage * Math.max(0.0F, coefficient);
		return Math.max(1, (int) Math.floor(cap));
	}

	// ==================== 镀层 ====================

	// ==================== 不死者加护 ====================

	/**
	 * 不死者加护：手持不死图腾时，受到的最终伤害减去配置点数。
	 *
	 * <p>**只生效一层**：主手或副手任一持有不死图腾即生效，两只手各持一个也只减一次；
	 * 放下全部图腾立刻失效。不是附魔，不注册状态效果、不显示图标。
	 */
	public static float undyingGraceReduction(LivingEntity entity) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableUndyingGrace || entity.tickCount <= 0) {
			return 0.0F;
		}
		return holdsTotem(entity) ? config.undyingGraceDamageReduction : 0.0F;
	}

	/** 主手或副手是否持有不死图腾 */
	public static boolean holdsTotem(LivingEntity entity) {
		return entity.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
				|| entity.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
	}

	// ==================== 出其不意 ====================

	/**
	 * 出其不意的一次判定详情。
	 *
	 * @param level  武器上的附魔等级（0 = 没该附魔）
	 * @param chance 触发几率（0 ~ 1）
	 * @param roll   本次掷出的随机数；{@code < 0} 表示"按设计根本没掷"（开关关闭 / 没附魔）
	 * @param hit    是否触发
	 */
	public record SurpriseRoll(int level, float chance, float roll, boolean hit) {
	}

	/** 出其不意：本次攻击是否额外生效一次 */
	public static boolean rollSurprise(LivingEntity attacker, ItemStack weapon) {
		return rollSurpriseRoll(attacker, weapon).hit();
	}

	/**
	 * 出其不意：掷一次并把详情一起返回（近战结算与 `[ER-SR]` 诊断日志用它）。
	 *
	 * <p>与原实现的随机数消耗完全一致：只有"开关开启 + 武器带该附魔"才会消耗一次 {@code nextFloat()}。
	 */
	public static SurpriseRoll rollSurpriseRoll(LivingEntity attacker, ItemStack weapon) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableSurprise || ModEnchantments.SURPRISE == null) {
			return new SurpriseRoll(0, 0.0F, -1.0F, false);
		}
		int level = levelOf(weapon, ModEnchantments.SURPRISE);
		if (level <= 0) {
			return new SurpriseRoll(0, 0.0F, -1.0F, false);
		}
		float chance = config.surpriseChancePerLevel * level;
		float roll = attacker.getRandom().nextFloat();
		return new SurpriseRoll(level, chance, roll, roll < chance);
	}

	// ==================== 幻影箭 / 箭矢兼容 ====================

	/** 打在箭上的命令 tag：带它的箭命中时先清目标受击冷却，保证"第二支"也能生效 */
	public static final String EXTRA_HIT_TAG = "enchantment_reforged.extra_hit";

	/** 给箭打上"需要保证本段生效"的标记（命令 tag 会随 NBT 持久化） */
	public static void markExtraHitArrow(Entity arrow) {
		arrow.addTag(EXTRA_HIT_TAG);
	}

	/** 这支箭是否被标记为"需要保证本段生效" */
	public static boolean isExtraHitArrow(Entity arrow) {
		return arrow.getTags().contains(EXTRA_HIT_TAG);
	}

	/** 幻影箭：本次射击是否额外射出一支箭 */
	public static boolean rollPhantomArrow(ItemStack weapon) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enablePhantomArrow || ModEnchantments.PHANTOM_ARROW == null) {
			return false;
		}
		int level = levelOf(weapon, ModEnchantments.PHANTOM_ARROW);
		if (level <= 0) {
			return false;
		}
		return RANDOM.nextFloat() < config.phantomArrowChancePerLevel * level;
	}

	private static final java.util.Random RANDOM = new java.util.Random();

	/**
	 * 多重射击"三支都生效"：带多重射击的弩每射出一支箭都打标记（受开关控制）。
	 *
	 * <p>这样三支打同一目标时不会因为受击冷却被吞掉第二、第三支。
	 */
	public static void markMultishotArrow(ItemStack weapon, Entity arrow) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableMultishotAllHit || weapon.isEmpty()) {
			return;
		}
		if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, weapon) > 0) {
			markExtraHitArrow(arrow);
		}
	}

	/**
	 * 复制一支"额外箭"：同类型实体 + NBT 复制 + 小角度偏移 + 不可拾取。
	 *
	 * <p>属性（伤害/暴击/击退/穿透/着火等）全部随 NBT 带走，因此额外箭与主箭表现一致。
	 */
	public static void spawnExtraArrow(AbstractArrow original) {
		if (original.level().isClientSide) {
			return;
		}
		Entity copy = original.getType().create(original.level());
		if (!(copy instanceof AbstractArrow extra)) {
			return;
		}
		CompoundTag nbt = new CompoundTag();
		original.addAdditionalSaveData(nbt);
		extra.readAdditionalSaveData(nbt);
		extra.moveTo(original.getX(), original.getY(), original.getZ(), original.getYRot(), original.getXRot());
		// 小角度偏移，避免与主箭完全重叠
		double spread = Math.toRadians(2.5D);
		Vec3 velocity = original.getDeltaMovement();
		double cos = Math.cos(spread);
		double sin = Math.sin(spread);
		Vec3 rotated = new Vec3(velocity.x * cos - velocity.z * sin, velocity.y, velocity.x * sin + velocity.z * cos);
		extra.setDeltaMovement(rotated);
		// 额外箭不可拾取，也不占用弹药
		extra.pickup = AbstractArrow.Pickup.DISALLOWED;
		markExtraHitArrow(extra);
		// 额外箭要继承"射手选定的粒子档位"：档位写在同步字节里，不会随上面的 NBT 复制过去
		if (original instanceof ArrowParticleCarrier source && extra instanceof ArrowParticleCarrier target) {
			target.enchantmentReforged$setParticleStyle(source.enchantmentReforged$getParticleStyle());
		}
		original.level().addFreshEntity(extra);
	}

	/** 弓：这件弓是否"无弹药也能射出普通箭"（创造/无限/无尽箭袋） */
	public static boolean allowsEmptyAmmo(Player player, ItemStack weapon) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, weapon) > 0 || hasEndlessQuiver(weapon);
	}

	/** 弩：这件弩是否"无弹药也能射出普通箭"（创造模式由原版判定，这里只管无限/无尽箭袋） */
	public static boolean allowsEmptyAmmoCrossbow(ItemStack weapon) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableCrossbowCompat || weapon.isEmpty()) {
			return false;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, weapon) > 0 || hasEndlessQuiver(weapon);
	}

	/** 这次射击的弹药是否不被消耗（无限仅普通箭；无尽箭袋则任何箭矢） */
	public static boolean sparesAmmo(ItemStack weapon, ItemStack ammo) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (config.enableEndlessQuiver && hasEndlessQuiver(weapon)) {
			return true;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, weapon) > 0 && ammo.is(Items.ARROW);
	}

	/** 是否带本模组的"无尽箭袋"（且开关打开） */
	public static boolean hasEndlessQuiver(ItemStack weapon) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		return config.enableEndlessQuiver && ModEnchantments.ENDLESS_QUIVER != null
				&& EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ENDLESS_QUIVER, weapon) > 0;
	}

	/** 弩上的"无限"是否生效（受弩兼容开关控制，与原版弓语义一致：仅普通箭） */
	public static boolean crossbowInfinityApplies(ItemStack weapon, ItemStack ammo) {
		return EnchantmentReforgedConfig.get().enableCrossbowCompat
				&& EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, weapon) > 0
				&& ammo.is(Items.ARROW);
	}

	/** 弩射出时的疾矢加速（等级取自弩本身） */
	public static float arrowSpeedMultiplier(ItemStack weapon) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableArrowVelocity || ModEnchantments.ARROW_VELOCITY == null) {
			return 1.0F;
		}
		int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ARROW_VELOCITY, weapon);
		return level <= 0 ? 1.0F : 1.0F + config.arrowVelocityBonusPerLevel * level;
	}

	/** 属性缺失（被其它模组整体替换了玩家默认属性）时只提示一次 */
	private static boolean lifeShieldAttributeMissingLogged;

	/** 当前护盾值（存在自定义属性里、只由服务端读写） */
	public static float lifeShield(LivingEntity entity) {
		AttributeInstance instance = lifeShieldInstance(entity);
		return instance == null ? 0.0F : (float) instance.getBaseValue();
	}

	/** 取护盾属性实例；没有（其它模组替换了玩家默认属性）时返回 null */
	private static AttributeInstance lifeShieldInstance(LivingEntity entity) {
		if (ModAttributes.LIFE_SHIELD == null) {
			return null;
		}
		AttributeInstance instance = entity.getAttribute(ModAttributes.LIFE_SHIELD);
		// 属性只挂在玩家身上，生物走到这里是正常的，不做提示
		if (instance == null && entity instanceof Player && !lifeShieldAttributeMissingLogged) {
			lifeShieldAttributeMissingLogged = true;
			EnchantmentReforged.LOGGER.warn(
					"[Enchantment Reforged] 玩家实体上找不到 life_shield 属性（可能被其它模组替换了玩家默认属性），生命护盾将不生效");
		}
		return instance;
	}

	/** 服务端首次 tick 时自检一次：确认护盾属性真的挂到了玩家默认属性上（只打一行日志） */
	private static boolean lifeShieldAttributeChecked;

	public static void verifyLifeShieldAttribute() {
		if (lifeShieldAttributeChecked) {
			return;
		}
		lifeShieldAttributeChecked = true;
		if (ModAttributes.LIFE_SHIELD == null) {
			EnchantmentReforged.LOGGER.warn("[Enchantment Reforged] life_shield 属性未注册，生命护盾将不生效");
			return;
		}
		boolean attached = net.minecraft.world.entity.ai.attributes.DefaultAttributes
				.getSupplier(net.minecraft.world.entity.EntityType.PLAYER)
				.hasAttribute(ModAttributes.LIFE_SHIELD);
		EnchantmentReforged.LOGGER.info("[Enchantment Reforged] 生命护盾属性已挂到玩家默认属性：{}", attached);
		if (!attached) {
			EnchantmentReforged.LOGGER.warn("[Enchantment Reforged] 生命护盾属性未挂到玩家默认属性，护盾将不生效"
					+ "（可能被其它模组覆盖了玩家默认属性注册）");
		}
	}

	/**
	 * 护盾上限 = 最大生命 × 每级百分比 × 等级。
	 *
	 * <p>结果向下取到半颗心：0.20f × 5 × 40 在浮点里等于 40.0000006，而 HUD 用
	 * {@code ceil(吸收值)} 决定画几颗黄心，多出的那点尾数会把 20 颗画成 21 颗。
	 * "上限"不允许被误差撑大，所以这里向下取整。
	 */
	public static float lifeShieldCap(LivingEntity entity, int level) {
		float raw = entity.getMaxHealth() * EnchantmentReforgedConfig.get().lifeShieldPercentPerLevel * level;
		return floorToHalfHeart(raw);
	}

	/** 护盾值与吸收值统一量化到半颗心（0.5）精度，避免 HUD 多画半颗心 */
	public static float quantizeShield(float value) {
		return Math.max(0.0F, Math.round(value * 2.0F) / 2.0F);
	}

	/** 向下取到半颗心（只用于"上限"这类不允许超出的数值） */
	private static float floorToHalfHeart(float value) {
		return (float) Math.max(0.0D, Math.floor(value * 2.0D) / 2.0D);
	}

	/** 写回护盾值（仅服务端；写进属性基础值，会随玩家 NBT 持久化） */
	public static void setLifeShield(LivingEntity entity, float shield) {
		if (entity.level().isClientSide) {
			return;
		}
		AttributeInstance instance = lifeShieldInstance(entity);
		if (instance == null) {
			return;
		}
		double value = Math.max(0.0D, Math.min(ModAttributes.LIFE_SHIELD_MAX, shield));
		if (Math.abs(instance.getBaseValue() - value) < 1.0E-4D) {
			return;
		}
		instance.setBaseValue(value);
	}

	/** 回复生命时累积护盾（healedAmount 由调用方按实际回复量算好） */
	public static void onHeal(LivingEntity entity, float healedAmount) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableLifeShield || healedAmount <= 0.0F || ModEnchantments.LIFE_SHIELD == null) {
			return;
		}
		// 服务端权威：客户端等效果同步即可，避免两端各加一份
		if (entity.level().isClientSide) {
			return;
		}
		int level = chestLevel(entity, ModEnchantments.LIFE_SHIELD);
		if (level <= 0) {
			return;
		}
		float cap = lifeShieldCap(entity, level);
		float current = lifeShield(entity);
		// 先量化再夹上限：保证"目标值"永远是 0.5 的整数倍且不超过上限
		float target = Math.min(cap,
				quantizeShield(current + healedAmount * config.lifeShieldPercentPerLevel * level));
		if (target > current + 1.0E-3F) {
			// 只把"我方护盾的增量"加到吸收值上（纯加法）：
			// 吸收值是原版与各个模组共用的池子，加法写入不会挤占别人的份额
			setLifeShield(entity, target);
			addShieldToPool(entity, target - current);
		}
	}

	/**
	 * 把我方护盾的增量加到吸收值上（只加不减）。
	 *
	 * <p>并记录"我方写入后的池子值"，供之后的消耗记账使用。
	 */
	private static void addShieldToPool(LivingEntity entity, float delta) {
		if (delta <= 0.0F) {
			return;
		}
		float total = quantizeShield(Math.max(0.0F, entity.getAbsorptionAmount() + delta));
		entity.setAbsorptionAmount(total);
		SHIELD_TRACK.put(entity, total);
	}

	/**
	 * 每 tick 维护生命护盾（由 {@code LivingEntityMixin} 在服务端驱动）。
	 *
	 * <p>约定：吸收值是"原版效果 + 各个模组"共用的池子，**我方只做加法与回收自己那一份**，
	 * 绝不把总量重写成"原版 + 我方护盾"——那是旧版挤占其它模组（例如饰品模组的魂心黄血）的原因。
	 *
	 * <p>本方法只做两件事：① 上限/配置/附魔变化导致我方护盾缩小时，从池子里扣掉缩减量；
	 * ② 记账——池子比我方上次记录少了多少，就当我方护盾被消耗了多少（即"先扣生命护盾"）。
	 *
	 * <p>快速路径：完全没有我方护盾（护盾值 0 且附魔等级 0）时直接返回，连吸收值都不碰。
	 */
	public static void tickLifeShield(LivingEntity entity) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		float shieldValue = lifeShield(entity);
		int level = ModEnchantments.LIFE_SHIELD == null ? 0 : chestLevel(entity, ModEnchantments.LIFE_SHIELD);
		if (shieldValue <= 0.0F && level <= 0) {
			SHIELD_TRACK.remove(entity);
			return;
		}

		// 按当前配置与等级裁剪护盾（调低数值、换下胸甲、关开关都会立刻生效）
		float cap = (config.enableLifeShield && level > 0) ? lifeShieldCap(entity, level) : 0.0F;
		float shield = Math.min(quantizeShield(shieldValue), Math.max(0.0F, cap));
		float tracked = SHIELD_TRACK.getOrDefault(entity, entity.getAbsorptionAmount());

		// 1) 我方护盾缩小：只从池子里扣掉"缩减的那一份"，不动其它来源
		if (shield < shieldValue - 1.0E-3F) {
			setLifeShield(entity, shield);
			float shrink = shieldValue - shield;
			float total = quantizeShield(Math.max(0.0F, entity.getAbsorptionAmount() - shrink));
			entity.setAbsorptionAmount(total);
			SHIELD_TRACK.put(entity, total);
			tracked = total;
		}
		if (shield <= 0.0F) {
			SHIELD_TRACK.remove(entity);
			return;
		}

		// 2) 记账：池子比上次记录少了多少 = 我方护盾被消耗了多少（先扣生命护盾）
		float observed = entity.getAbsorptionAmount();
		float consumed = quantizeShield(Math.max(0.0F, tracked - observed));
		if (consumed > 0.0F) {
			setLifeShield(entity, quantizeShield(Math.max(0.0F, shield - consumed)));
			SHIELD_TRACK.put(entity, observed);
		} else if (observed > tracked) {
			// 池子被第三方抬高（例如饰品模组的魂心补满）：跟随记录，不夺权、不重算总量
			SHIELD_TRACK.put(entity, observed);
		}
	}

	// ==================== 生命提升 / 迅捷打击 ====================

	/** 每 tick 维护"装备附魔带来的属性修饰符"（只在数值变化时写入） */
	public static void updateEquipmentAttributes(LivingEntity entity) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();

		double healthBonus = 0.0D;
		if (config.enableHealthBoost && ModEnchantments.HEALTH_BOOST != null) {
			healthBonus = armorLevelSum(entity, ModEnchantments.HEALTH_BOOST) * config.healthBoostPerLevel;
		}
		applyModifier(entity, Attributes.MAX_HEALTH, HEALTH_BOOST_MODIFIER_ID,
				"enchantment_reforged:health_boost", AttributeModifier.Operation.ADDITION, healthBonus);

		// 死者之心：最大生命 ×1.5（乘法修饰符，与生命提升的加法项叠加）
		double heartMultiplier = 0.0D;
		if (config.enableDeadMansHeart && ModEnchantments.DEAD_MANS_HEART != null
				&& chestLevel(entity, ModEnchantments.DEAD_MANS_HEART) > 0) {
			heartMultiplier = config.deadMansHeartHealthBonus;
		}
		applyModifier(entity, Attributes.MAX_HEALTH, DEAD_MANS_HEART_MODIFIER_ID,
				"enchantment_reforged:dead_mans_heart",
				AttributeModifier.Operation.MULTIPLY_TOTAL, heartMultiplier);

		double speedBonus = 0.0D;
		if (config.enableQuickStrike && ModEnchantments.QUICK_STRIKE != null) {
			speedBonus = mainHandLevel(entity, ModEnchantments.QUICK_STRIKE) * config.quickStrikeBonusPerLevel;
		}
		applyModifier(entity, Attributes.ATTACK_SPEED, QUICK_STRIKE_MODIFIER_ID,
				"enchantment_reforged:quick_strike", AttributeModifier.Operation.ADDITION, speedBonus);
	}

	private static void applyModifier(LivingEntity entity, Attribute attribute, UUID id, String name,
			AttributeModifier.Operation operation, double value) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier existing = instance.getModifier(id);

		if (Math.abs(value) < 1.0E-6D) {
			if (existing != null) {
				instance.removeModifier(id);
			}
			return;
		}
		if (existing != null && Math.abs(existing.getAmount() - value) < 1.0E-6D) {
			return;
		}
		if (existing != null) {
			instance.removeModifier(id);
		}
		instance.addPermanentModifier(new AttributeModifier(id, name, value, operation));
	}

	// ==================== 恶咒 ====================

	/** 恶咒：最大生命不变，但当前生命超过 1 就压回 1（回血逻辑照常执行） */
	public static void applyCalamity(LivingEntity entity) {
		if (entity.isDeadOrDying() || entity.getHealth() <= 1.0F || !isCalamityActive(entity)) {
			return;
		}
		entity.setHealth(1.0F);
	}

	/** 恶咒是否在生效（开关开启 + 胸甲带附魔） */
	public static boolean isCalamityActive(LivingEntity entity) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		return config.enableCalamity && ModEnchantments.CALAMITY != null
				&& chestLevel(entity, ModEnchantments.CALAMITY) > 0;
	}

	// ==================== 穿刺（基岩版判定） ====================

	/**
	 * 穿刺的基岩版判定：三叉戟攻击时把目标的"生物类型"换成水生，
	 * 让原版穿刺（对水生生物加成）按新规则生效。
	 *
	 * <p>三叉戟上只有穿刺会用到生物类型，所以这个替换不会干扰其它附魔。
	 * 目标"接触水或淋雨"视为水生；但"泡在水里且乘船"不算。
	 */
	public static MobType impalingGroupFor(@Nullable LivingEntity target, MobType original) {
		if (!EnchantmentReforgedConfig.get().enableImpalingBedrockRule || target == null) {
			return original;
		}
		// "淋雨"用世界判定：该位置正在下雨且没有被遮挡
		boolean wet = target.isInWater() || target.level().isRainingAt(target.blockPosition());
		boolean onBoatInWater = target.isInWater() && target.isPassenger();
		return wet && !onBoatInWater ? MobType.WATER : MobType.UNDEFINED;
	}

	// ==================== 弩兼容 ====================

	/** 弩射出的箭：把原版弓的附魔（力量/火矢/冲击）搬到弩上，数值与原版弓一致 */
	public static void applyCrossbowEnchantments(ItemStack crossbow, AbstractArrow arrow) {
		if (!EnchantmentReforgedConfig.get().enableCrossbowCompat) {
			return;
		}
		int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, crossbow);
		if (power > 0) {
			arrow.setBaseDamage(arrow.getBaseDamage() + power * 0.5 + 0.5);
		}
		int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, crossbow);
		if (punch > 0) {
			arrow.setKnockback(punch);
		}
		if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, crossbow) > 0) {
			arrow.setSecondsOnFire(5);
		}
	}

	// ==================== 斩首 ====================

	/** 击杀时按几率掉落头颅 */
	public static void onDeathForBeheading(LivingEntity victim, DamageSource source) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableBeheading || ModEnchantments.BEHEADING == null) {
			return;
		}
		Entity attacker = source.getEntity();
		if (!(attacker instanceof ServerPlayer player)) {
			return;
		}
		int level = mainHandLevel(player, ModEnchantments.BEHEADING);
		if (level <= 0) {
			return;
		}
		if (victim.getRandom().nextFloat() >= config.beheadingHeadChancePerLevel * level) {
			return;
		}
		ItemStack head = headFor(victim);
		if (!head.isEmpty()) {
			victim.spawnAtLocation(head);
		}
	}

	/** 头颅映射：变种映射到同一头颅；末影龙无效；玩家掉落带皮肤的玩家头 */
	public static ItemStack headFor(LivingEntity victim) {
		if (victim instanceof Player player) {
			ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
			CompoundTag nbt = stack.getOrCreateTag();
			NbtUtils.writeGameProfile(nbt, player.getGameProfile());
			return stack;
		}
		EntityType<?> type = victim.getType();
		if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.ZOMBIE_VILLAGER) {
			return new ItemStack(Items.ZOMBIE_HEAD);
		}
		if (type == EntityType.SKELETON || type == EntityType.STRAY) {
			return new ItemStack(Items.SKELETON_SKULL);
		}
		if (type == EntityType.WITHER_SKELETON) {
			return new ItemStack(Items.WITHER_SKELETON_SKULL);
		}
		if (type == EntityType.CREEPER) {
			return new ItemStack(Items.CREEPER_HEAD);
		}
		if (type == EntityType.PIGLIN || type == EntityType.PIGLIN_BRUTE) {
			return new ItemStack(Items.PIGLIN_HEAD);
		}
		return ItemStack.EMPTY;
	}

	// ==================== 速射 / 本能释放 ====================

	/** 拉满弓所需 tick 数（速射会缩短，最低 1 tick） */
	public static int requiredDrawTicks(ItemStack bow) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		int level = ModEnchantments.QUICK_DRAW == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.QUICK_DRAW, bow);
		float seconds = 1.0F - config.quickDrawSecondsPerLevel * level;
		return Math.max(1, Math.round(seconds * 20.0F));
	}

	/** 服务端与客户端每 tick 调用：拉满即自动射出并立刻重新蓄力 */
	public static void tickInstinctiveRelease(LivingEntity entity) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableInstinctiveRelease || ModEnchantments.INSTINCTIVE_RELEASE == null) {
			return;
		}
		if (!(entity instanceof Player player) || !player.isUsingItem()) {
			return;
		}
		ItemStack active = player.getUseItem();
		if (!(active.getItem() instanceof BowItem)) {
			return;
		}
		if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.INSTINCTIVE_RELEASE, active) <= 0) {
			return;
		}
		if (player.getTicksUsingItem() < requiredDrawTicks(active)) {
			return;
		}
		InteractionHand hand = player.getUsedItemHand();
		int remainingUseTicks = Math.max(0, active.getUseDuration() - player.getTicksUsingItem());
		player.stopUsingItem();      // 结束使用状态（内部那次 onStoppedUsing 的计时已被重置，威力为 0 会直接返回）
		// 用正确的剩余时间再触发一次释放，箭才会真正射出去
		active.releaseUsing(player.level(), player, remainingUseTicks);
		player.startUsingItem(hand);   // 立刻开始下一次蓄力
	}

	/**
	 * 玩家重生时清掉护盾残留（原版会把吸收值复制到新角色，否则会出现"旧护盾没清 + 新护盾"）。
	 *
	 * <p>只扣掉"我方护盾那一份"，不碰原版或其它模组给出的吸收。
	 */
	public static void clearLifeShield(LivingEntity entity) {
		float shield = lifeShield(entity);
		SHIELD_TRACK.remove(entity);
		setLifeShield(entity, 0.0F);
		if (shield > 0.0F) {
			entity.setAbsorptionAmount(quantizeShield(Math.max(0.0F, entity.getAbsorptionAmount() - shield)));
		}
	}

	// ==================== 经验学者 ====================

	/** 拾取经验球时的经验加成 */
	public static int scholarBonus(LivingEntity entity, int amount) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableScholar || amount <= 0 || ModEnchantments.SCHOLAR == null) {
			return amount;
		}
		int level = helmetLevel(entity, ModEnchantments.SCHOLAR);
		if (level <= 0) {
			return amount;
		}
		return amount + (int) Math.floor(amount * config.scholarBonusPerLevel * level);
	}

	// ==================== 斩杀 ====================

	/**
	 * 斩杀：目标当前生命不高于阈值（默认 15%）时补一记必杀。
	 *
	 * <p>补刀走自定义伤害类型 {@code enchantment_reforged:execution}（自带专属死亡信息），
	 * 攻击者仍然是我们，所以掉落、经验与抢夺都按原版规则照常结算。
	 *
	 * <p>伤害量取 {@code (当前生命 + 吸收) × 5 + 10}：足够穿掉抗性药水最高 80% 的减免，
	 * 又远小于 {@code Float.MAX_VALUE}，不会污染装备耐久与统计数值。
	 */
	public static void tryExecute(LivingEntity attacker, Entity target, ItemStack weapon) {
		tryExecuteReport(attacker, target, weapon);
	}

	/** 斩杀：与 {@link #tryExecute} 完全同一口径，返回是否真的补了这一刀（诊断日志用它） */
	public static boolean tryExecuteReport(LivingEntity attacker, Entity target, ItemStack weapon) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableExecution || ModEnchantments.EXECUTION == null) {
			return false;
		}
		if (!(target instanceof LivingEntity victim) || victim.isDeadOrDying()) {
			return false;
		}
		if (levelOf(weapon, ModEnchantments.EXECUTION) <= 0) {
			return false;
		}
		if (victim.getHealth() > victim.getMaxHealth() * config.executionThreshold) {
			return false;
		}
		DamageSource source = executionDamageSource(attacker, victim);
		// 清掉受击冷却，否则这一击会被无敌帧吞掉
		victim.invulnerableTime = 0;
		victim.hurt(source, (victim.getHealth() + victim.getAbsorptionAmount()) * 5.0F + 10.0F);
		return true;
	}

	/** 斩杀的伤害源：优先用自定义伤害类型，取不到时退回普通攻击来源 */
	private static DamageSource executionDamageSource(LivingEntity attacker, LivingEntity victim) {
		Optional<Holder.Reference<DamageType>> entry = victim.level().registryAccess()
				.registryOrThrow(Registries.DAMAGE_TYPE)
				.getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, EXECUTION_DAMAGE_ID));
		if (entry.isPresent()) {
			// 同时记录"直接来源"与"攻击者"，死亡信息才能带上攻击者名字
			return new DamageSource(entry.get(), attacker, attacker);
		}
		return attacker instanceof Player player
				? victim.damageSources().playerAttack(player)
				: victim.damageSources().mobAttack(attacker);
	}

	// ==================== 速食 ====================

	/** 速食的时长下限（tick） */
	public static final int QUICK_EAT_MIN_TICKS = 8;

	/**
	 * 速食：缩短"使用食物"的时长（每级 -25%，最低 8 tick）。
	 *
	 * <p>只改 {@code startUsingItem} 里写进 {@code itemUseTimeLeft} 的那个值，
	 * 双端都执行同一段逻辑，所以进食节奏两端一致。
	 */
	public static int quickEatUseTicks(LivingEntity entity, ItemStack stack, int originalTicks) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableQuickEat || ModEnchantments.QUICK_EAT == null || stack.isEmpty()) {
			return originalTicks;
		}
		if (!stack.getItem().isEdible()) {
			return originalTicks;
		}
		int level = helmetLevel(entity, ModEnchantments.QUICK_EAT);
		if (level <= 0) {
			return originalTicks;
		}
		float factor = Math.max(0.0F, 1.0F - config.quickEatReductionPerLevel * level);
		return Math.max(QUICK_EAT_MIN_TICKS, Math.round(originalTicks * factor));
	}

	// ==================== 大胃袋 ====================

	/** 大胃袋带来的额外饥饿值上限（每级默认 +4，可叠加等级） */
	public static float bigStomachBonus(LivingEntity entity) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableBigStomach || ModEnchantments.BIG_STOMACH == null || entity.tickCount <= 0) {
			return 0.0F;
		}
		int level = chestLevel(entity, ModEnchantments.BIG_STOMACH);
		return level <= 0 ? 0.0F : level * config.bigStomachBonusPerLevel;
	}

	/** 饥饿值上限 = 原版 20 + 大胃袋加成 */
	public static int foodLevelCap(LivingEntity entity) {
		return 20 + Math.round(bigStomachBonus(entity));
	}

	// ==================== 死者之心 ====================

	/** 死者之心：受到治疗时的缩放系数（默认 ×0.5） */
	public static float deadMansHeartHealFactor(LivingEntity entity) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableDeadMansHeart || ModEnchantments.DEAD_MANS_HEART == null || entity.tickCount <= 0) {
			return 1.0F;
		}
		if (chestLevel(entity, ModEnchantments.DEAD_MANS_HEART) <= 0) {
			return 1.0F;
		}
		return Math.max(0.0F, 1.0F - config.deadMansHeartHealingPenalty);
	}

	// ==================== 复仇 ====================

	/** 复仇窗口时长（tick）：3 秒 */
	private static final int REVENGE_WINDOW_TICKS = 60;

	/** 玩家受伤且伤害真的落地时刷新复仇窗口 */
	public static void onDamaged(LivingEntity entity) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableRevenge || ModEnchantments.REVENGE == null) {
			return;
		}
		if (chestLevel(entity, ModEnchantments.REVENGE) <= 0) {
			return;
		}
		REVENGE_WINDOW.put(entity.getUUID(), entity.level().getGameTime() + REVENGE_WINDOW_TICKS);
	}

	/**
	 * 复仇的伤害倍率（1.0 = 没有加成）。
	 *
	 * <p>只在"窗口内 + 胸甲带附魔 + 本次是剑/斧/三叉戟近战"时生效；
	 * 弓、空手与矛（矛模组）都不参与。
	 */
	public static float revengeMultiplier(LivingEntity attacker) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableRevenge || ModEnchantments.REVENGE == null || attacker.tickCount <= 0) {
			return 1.0F;
		}
		Long until = REVENGE_WINDOW.get(attacker.getUUID());
		if (until == null || until < attacker.level().getGameTime()) {
			return 1.0F;
		}
		int level = chestLevel(attacker, ModEnchantments.REVENGE);
		if (level <= 0 || !isMeleeWeapon(attacker.getMainHandItem())) {
			return 1.0F;
		}
		return 1.0F + revengeBonus(level);
	}

	/**
	 * 复仇在指定等级下的增伤比例：{@code 基础 + 每级递增 × (等级 − 1)}。
	 *
	 * <p>默认 0.05 + 0.10 × (等级−1) → 1/2/3 级 = +5% / +15% / +25%。
	 * 附魔描述与配置说明都从这里取值，保证"算出来的"和"写出来的"永远一致。
	 */
	public static float revengeBonus(int level) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (level <= 0) {
			return 0.0F;
		}
		return config.revengeBonusBase + config.revengeBonusPerLevel * (level - 1);
	}

	/** 剑 / 斧 / 三叉戟（复仇的口径，不含矛模组的矛与弓） */
	public static boolean isMeleeWeapon(ItemStack stack) {
		return !stack.isEmpty()
				&& (stack.getItem() instanceof SwordItem
						|| stack.getItem() instanceof AxeItem
						|| stack.is(Items.TRIDENT));
	}

	// ==================== 灵魂加护 ====================

	/**
	 * 灵魂加护：取消这次死亡并原地复活。
	 *
	 * <p>满血 + 清掉全部负面效果 + 2 秒抗性，并给出原版图腾的音效与粒子（明显、不提供开关）；
	 * 冷却按服务端世界时间计时（重登可绕过，属已知取舍），同时挂一个只作显示的冷却效果。
	 *
	 * @return true 表示已经复活（调用方应阻止死亡）
	 */
	public static boolean trySoulGrace(LivingEntity entity) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableSoulGrace || ModEnchantments.SOUL_GRACE == null) {
			return false;
		}
		if (chestLevel(entity, ModEnchantments.SOUL_GRACE) <= 0) {
			return false;
		}
		long now = entity.level().getGameTime();
		Long until = SOUL_GRACE_COOLDOWN.get(entity.getUUID());
		if (until != null && now < until) {
			return false;
		}
		SOUL_GRACE_COOLDOWN.put(entity.getUUID(), now + (long) config.soulGraceCooldownSeconds * 20L);

		entity.setHealth(entity.getMaxHealth());
		clearNegativeEffects(entity);
		// 2 秒抗性（等级 4 = 80% 减免），足够让玩家脱离危险
		entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, true));
		spawnSoulGraceEffects(entity);
		syncSoulGraceCooldown(entity);
		return true;
	}

	/** 免死瞬间的表现：原版图腾音效 + 一团明显的图腾粒子 */
	private static void spawnSoulGraceEffects(LivingEntity entity) {
		Level world = entity.level();
		world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		if (world instanceof ServerLevel serverWorld) {
			serverWorld.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
					entity.getX(), (entity.getY() + entity.getBbHeight() * 0.6D), entity.getZ(),
					60, 0.6D, 0.9D, 0.6D, 0.35D);
		}
	}

	/**
	 * 让"冷却效果"与服务端的冷却计时保持一致（每 20 tick 调一次）。
	 *
	 * <p>效果只作显示，所以喝牛奶、{@code /effect clear} 把图标清掉也不会绕过冷却：
	 * 下一次同步会把剩余时长重新挂上；冷却结束后计时表项也会被清掉。
	 */
	public static void syncSoulGraceCooldown(LivingEntity entity) {
		if (ModStatusEffects.SOUL_GRACE == null || entity.level().isClientSide) {
			return;
		}
		Long until = SOUL_GRACE_COOLDOWN.get(entity.getUUID());
		long now = entity.level().getGameTime();
		if (until == null || now >= until) {
			if (until != null) {
				SOUL_GRACE_COOLDOWN.remove(entity.getUUID());
			}
			if (entity.hasEffect(ModStatusEffects.SOUL_GRACE)) {
				entity.removeEffect(ModStatusEffects.SOUL_GRACE);
			}
			return;
		}
		int remaining = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, until - now));
		MobEffectInstance existing = entity.getEffect(ModStatusEffects.SOUL_GRACE);
		if (existing != null && Math.abs(existing.getDuration() - remaining) <= 20) {
			return;
		}
		// 用"移除 + 重挂"保证剩余时长一定被写进去（同效果直接再 add 不会刷新时长）
		if (existing != null) {
			entity.removeEffect(ModStatusEffects.SOUL_GRACE);
		}
		entity.addEffect(new MobEffectInstance(ModStatusEffects.SOUL_GRACE,
				remaining, 0, false, false, true));
	}

	/** 玩家断线时清掉灵魂加护的冷却登记 */
	public static void clearSoulGraceCooldown(UUID playerId) {
		if (playerId != null) {
			SOUL_GRACE_COOLDOWN.remove(playerId);
		}
	}

	/** 清掉全部负面（非增益）效果；遍历副本，避免边遍历边删 */
	public static void clearNegativeEffects(LivingEntity entity) {
		for (MobEffectInstance effect : new java.util.ArrayList<>(entity.getActiveEffects())) {
			if (!effect.getEffect().isBeneficial()) {
				entity.removeEffect(effect.getEffect());
			}
		}
	}

	// ==================== 灵动步伐 ====================

	/**
	 * 灵动步伐：按几率完全无效化这次伤害。
	 *
	 * <p>没有来源限制（虚空、指令同样可以闪避），也没有内置冷却；闪避时按被攻击者
	 * 自己设置的档位播一声音效（0 = 关闭）。
	 *
	 * @return true 表示这次伤害被闪避掉
	 */
	public static boolean rollNimbleSteps(LivingEntity victim) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableNimbleSteps || ModEnchantments.NIMBLE_STEPS == null || victim.tickCount <= 0) {
			return false;
		}
		int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.NIMBLE_STEPS,
				victim.getItemBySlot(EquipmentSlot.FEET));
		if (level <= 0) {
			return false;
		}
		if (victim.getRandom().nextFloat() >= config.nimbleStepsChancePerLevel * level) {
			return false;
		}
		if (!victim.level().isClientSide) {
			playDodgeSound(victim);
		}
		return true;
	}

	/**
	 * 播放闪避音效：优先用被攻击者上报的档位，未上报（旧客户端）时用服务端本地配置值。
	 */
	private static void playDodgeSound(LivingEntity victim) {
		int index = EnchantmentReforgedConfig.get().nimbleStepsSound;
		ParticlePreferences.Preference preference = ParticlePreferences.preferenceOf(victim.getUUID());
		if (preference != null) {
			index = preference.dodgeSound();
		}
		SoundEvent sound = DodgeSoundStyle.soundAt(index);
		if (sound == null) {
			return;
		}
		victim.level().playSound(null, victim.getX(), victim.getY() + 1.0D, victim.getZ(),
				sound, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}
