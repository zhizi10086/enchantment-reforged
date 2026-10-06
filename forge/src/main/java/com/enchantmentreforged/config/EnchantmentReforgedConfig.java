package com.enchantmentreforged.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.enchantmentreforged.EnchantmentReforged;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 运行时配置（config/enchantment_reforged.json）。
 *
 * <p>所有修改立即对后续计算生效；保存后下次启动继续生效。配置有三种修改途径：
 * Mod Menu 图形配置页、游戏内命令 /enchantmentreforged set ...、直接编辑 JSON 文件。
 */
public final class EnchantmentReforgedConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "enchantment_reforged.json";

	// 两个隐藏项：不在界面/命令中暴露（避免玩家乱调），但保留在文件里供管理员编辑
	private static final String KEY_SHARPNESS_MAX_LEVEL = "sharpness_max_level";
	private static final String KEY_PROTECTION_MAX_LEVEL = "protection_max_level";

	/** 客户端线程（配置同步/配置页）与服务端线程都会读写，声明为 volatile 保证可见性 */
	private static volatile EnchantmentReforgedConfig instance = new EnchantmentReforgedConfig();

	// ---------------- 铁砧 ----------------
	/** 铁砧改为消耗经验点数并移除"过于昂贵" */
	public boolean enableAnvilXpCost = true;
	/** 铁砧成本的等级上限：成本最多换算到该等级对应的经验点数（默认 30 级 = 1395 点） */
	public int anvilMaxCostLevel = 30;

	// ---------------- 锋利 ----------------
	/** 唯一开关：开 = 使用锋利Plus（原版锋利退出随机池且两者互斥）；关 = 恢复原版锋利 */
	public boolean enableCustomSharpness = true;
	public float sharpnessDamagePerLevel = 1.5F;

	// ---------------- 力量 ----------------
	public boolean enableStrengthRework = true;
	public float strengthMultiplierPerLevel = 0.5F;
	public boolean applyStrengthToMobs = true;

	// ---------------- 保护 ----------------
	public boolean enableProtectionLevel5 = true;

	// ---------------- 原版附魔兼容 ----------------
	/** 穿刺改为基岩版判定（目标接触水或淋雨时生效，水里乘船无效） */
	public boolean enableImpalingBedrockRule = true;
	/** 允许"无限"与"经验修补"共存 */
	public boolean enableInfinityMending = true;
	/** 允许力量/无限/火矢/冲击作用于弩 */
	public boolean enableCrossbowCompat = true;
	/** 忠诚改版：投掷保留三叉戟本体，改飞出"假三叉戟"，返回前不能再投 */
	public boolean enableLoyaltyRework = true;
	/** 多重射击散射角（度），原版为 10 */
	public float multishotSpreadDegrees = 10.0F;
	/** 多重射击的三支箭对同一目标都生效 */
	public boolean enableMultishotAllHit = true;
	/** 药水箭的瞬间伤害段不再被箭矢无敌帧吞掉 */
	public boolean enablePotionArrowFullDamage = true;
	/** 原版弓的自然散布（1.0 = 原版） */
	public float bowInaccuracy = 1.0F;

	// ---------------- 魔剑 ----------------
	public boolean enableSpellblade = true;
	public float spellbladeMagicPercentPerLevel = 0.08F;

	// ---------------- 斩首 ----------------
	public boolean enableBeheading = true;
	public float beheadingHeadChancePerLevel = 0.10F;

	// ---------------- 自动熔炼 ----------------
	public boolean enableAutoSmelt = true;

	// ---------------- 疾矢 ----------------
	public boolean enableArrowVelocity = true;
	public float arrowVelocityBonusPerLevel = 0.10F;

	// ---------------- 速射 ----------------
	public boolean enableQuickDraw = true;
	public float quickDrawSecondsPerLevel = 0.10F;

	// ---------------- 本能释放 ----------------
	public boolean enableInstinctiveRelease = true;

	// ---------------- 生命护盾 ----------------
	public boolean enableLifeShield = true;
	public float lifeShieldPercentPerLevel = 0.20F;

	// ---------------- 生命提升 ----------------
	public boolean enableHealthBoost = true;
	public float healthBoostPerLevel = 1.0F;

	// ---------------- 嗜血 ----------------
	public boolean enableLifesteal = true;
	public float lifestealPercentPerLevel = 0.08F;

	// ---------------- 经验学者 ----------------
	public boolean enableScholar = true;
	public float scholarBonusPerLevel = 0.10F;

	// ---------------- 恶咒 ----------------
	public boolean enableCalamity = true;

	// ---------------- 死神祝福 ----------------
	public boolean enableDeathsBlessing = true;
	public float deathsBlessingDamageTakenBonus = 0.20F;
	public float deathsBlessingDamageBonusPerPercent = 0.01F;
	public float deathsBlessingResistPerPercent = 0.005F;

	// ---------------- 迅捷打击 ----------------
	public boolean enableQuickStrike = true;
	public float quickStrikeBonusPerLevel = 0.10F;

	// ---------------- 生命修补 ----------------
	public boolean enableLifeMending = true;
	public float lifeMendingDurabilityPerLevel = 1.0F;

	// ---------------- 坚甲 ----------------
	public boolean enableSturdy = true;
	/** 1 级时"最大耐久"的系数，默认 5% */
	public float sturdyCoefficient = 0.05F;
	/** 每升一级系数递减量，默认 2%（于是 5%/3%/1%） */
	public float sturdyCoefficientStep = 0.02F;

	// ---------------- 不死者加护（手持不死图腾时的被动效果，只生效一层） ----------------
	public boolean enableUndyingGrace = true;
	public float undyingGraceDamageReduction = 1.0F;

	// ---------------- 出其不意 ----------------
	public boolean enableSurprise = true;
	public float surpriseChancePerLevel = 0.10F;

	// ---------------- 幻影箭 ----------------
	public boolean enablePhantomArrow = true;
	public float phantomArrowChancePerLevel = 0.10F;

	// ---------------- 无尽箭袋 ----------------
	public boolean enableEndlessQuiver = true;

	// ---------------- 箭矢粒子（每名玩家各自的本地设置） ----------------
	/**
	 * 箭矢飞行轨迹的粒子档位（0 = 原版，1 = 无粒子，2~12 为原版粒子）。
	 *
	 * <p>这一项属于"客户端作用域"：服务端配置不会同步它，每名玩家在自己机器上各选各的，
	 * 射出的箭会把射手的档位一起带给其他玩家看（见 {@code ArrowParticleStyle}）。
	 */
	public int arrowParticle = 0;
	/** 远距离显示：箭飞出实体同步范围（64 格）后仍能看到轨迹（由服务端代发粒子） */
	public boolean arrowParticleLongDistance = false;
	/** 远距离显示的作用半径（格），上限 512（原版强制发粒子的硬上限） */
	public int arrowParticleDistance = 128;
	/** 近战粒子的档位（普通/暴击命中；0 = 原版默认，1 = 无粒子并屏蔽暴击星，2~12 = 原版粒子） */
	public int meleeParticle = 0;
	/** 横扫粒子的档位（0 = 原版默认，1 = 无粒子并屏蔽原版横扫弧线，2~12 = 原版粒子） */
	public int meleeSweepParticle = 0;
	/** 近战粒子的效果强度：0 = 简化，1 = 标准，2 = 大量（数量/扩散/余晖档位） */
	public int meleeParticleEffect = 1;
	/** 出其不意触发时的粒子档位（0/1 = 不生成，2~12 = 原版粒子；强度沿用 melee_particle_effect） */
	public int surpriseParticle = 2;

	// ---------------- 玩家死亡粒子 ----------------
	public boolean enableDeathParticle = true;
	/** 玩家死亡时爆发的粒子档位（0/1 = 不生成，2~12 = 原版粒子） */
	public int deathParticle = 12;

	// ---------------- 斩杀 ----------------
	public boolean enableExecution = true;
	/** 触发斩杀的生命比例阈值（0.15 = 目标生命 ≤15% 时斩杀） */
	public float executionThreshold = 0.15F;

	// ---------------- 速食 ----------------
	public boolean enableQuickEat = true;
	/** 每级缩短的进食时长比例（0.25 = 每级 -25%） */
	public float quickEatReductionPerLevel = 0.25F;

	// ---------------- 大胃袋 ----------------
	public boolean enableBigStomach = true;
	/** 每级提升的饥饿值上限（4 = 每级 +4，满级 20 → 40） */
	public float bigStomachBonusPerLevel = 4.0F;

	// ---------------- 死者之心 ----------------
	public boolean enableDeadMansHeart = true;
	/** 最大生命倍率增量（0.5 = ×1.5） */
	public float deadMansHeartHealthBonus = 0.5F;
	/** 受到治疗的削减比例（0.5 = -50%） */
	public float deadMansHeartHealingPenalty = 0.5F;

	// ---------------- 复仇 ----------------
	public boolean enableRevenge = true;
	/** 1 级时的加成（0.05 = +5%） */
	public float revengeBonusBase = 0.05F;
	/** 每级递增（0.10 = 每级 +10%，于是 1/2/3 级 = 5%/15%/25%） */
	public float revengeBonusPerLevel = 0.10F;

	// ---------------- 灵魂加护 ----------------
	public boolean enableSoulGrace = true;
	/** 复活冷却（秒） */
	public int soulGraceCooldownSeconds = 180;

	// ---------------- 灵动步伐 ----------------
	public boolean enableNimbleSteps = true;
	/** 每级闪避几率（0.05 = 每级 +5%） */
	public float nimbleStepsChancePerLevel = 0.05F;
	/** 闪避生效时的音效档位（0 = 关闭，默认 3 = 幻术师镜影；每名玩家各自的客户端设置） */
	public int nimbleStepsSound = 3;

	// ---------------- 隐藏项（仅文件可改） ----------------
	public int sharpnessMaxLevel = 5;
	public int protectionMaxLevel = 5;

	private EnchantmentReforgedConfig() {
	}

	public static EnchantmentReforgedConfig get() {
		return instance;
	}

	/** 读取配置文件；缺失字段用默认值补齐并回写，非法值会被夹到范围内 */
	public static void load() {
		Path path = configPath();
		EnchantmentReforgedConfig fresh = new EnchantmentReforgedConfig();

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				fresh.applyJson(JsonParser.parseReader(reader).getAsJsonObject(), false);
			} catch (Exception e) {
				EnchantmentReforged.LOGGER.warn("[Enchantment Reforged] 配置读取失败，改用默认值：{}", e.toString());
			}
		}

		instance = fresh;
		instance.save();
	}

	/** 把当前配置写入磁盘 */
	public void save() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(toJsonObject(false)), StandardCharsets.UTF_8);
		} catch (Exception e) {
			EnchantmentReforged.LOGGER.warn("[Enchantment Reforged] 配置写入失败：{}", e.toString());
		}
	}

	/** 当前配置序列化为 JSON 字符串（服务端同步给客户端用；不含客户端作用域项） */
	public String toJsonString() {
		return GSON.toJson(toJsonObject(true));
	}

	/**
	 * 组装 JSON（界面/命令/同步共用同一套字段）。
	 *
	 * @param forSync true = 用于服务端→客户端同步（跳过客户端作用域项，让每名玩家保留自己的设置）
	 */
	private JsonObject toJsonObject(boolean forSync) {
		JsonObject json = new JsonObject();
		for (Option option : Option.values()) {
			if (forSync && option.clientOnly()) {
				continue;
			}
			switch (option.kind()) {
				case BOOLEAN -> json.addProperty(option.key(), option.get(this) >= 0.5D);
				case DOUBLE -> json.addProperty(option.key(), option.get(this));
				case INTEGER, ENUM -> json.addProperty(option.key(), (int) option.get(this));
			}
		}
		json.addProperty(KEY_SHARPNESS_MAX_LEVEL, sharpnessMaxLevel);
		json.addProperty(KEY_PROTECTION_MAX_LEVEL, protectionMaxLevel);
		return json;
	}

	/**
	 * 应用服务端同步过来的配置：只改内存、不覆盖本地文件，且保留客户端作用域项的本地值。
	 *
	 * <p>这样联机时服务端配置不会覆盖玩家自己的"箭矢粒子"一类的本地设置。
	 */
	public static void applyRemote(String json) {
		try {
			EnchantmentReforgedConfig target = new EnchantmentReforgedConfig();
			target.applyJson(JsonParser.parseString(json).getAsJsonObject(), true);
			// 客户端作用域项沿用本地（当前内存）的值
			for (Option option : Option.values()) {
				if (option.clientOnly()) {
					option.set(target, option.get(instance));
				}
			}
			instance = target;
			EnchantmentReforged.LOGGER.info("[Enchantment Reforged] 已同步服务端配置");
		} catch (Exception e) {
			EnchantmentReforged.LOGGER.warn("[Enchantment Reforged] 服务端配置同步失败：{}", e.toString());
		}
	}

	/**
	 * 从 JSON 读取全部字段。
	 *
	 * <p>逐个字段容错：某个字段写坏了只跳过它并保留默认值；布尔 true/false 也能正确读成 1/0。
	 *
	 * @param fromRemote true = 来自服务端同步（跳过客户端作用域项）
	 */
	private void applyJson(JsonObject json, boolean fromRemote) {
		for (Option option : Option.values()) {
			if (fromRemote && option.clientOnly()) {
				continue;
			}
			JsonElement element = json.get(option.key());
			if (element == null || !element.isJsonPrimitive()) {
				continue;
			}
			try {
				option.set(this, readNumber(element.getAsJsonPrimitive()));
			} catch (Exception e) {
				EnchantmentReforged.LOGGER.warn("[Enchantment Reforged] 选项 {} 的值无法解析，保持默认：{}",
						option.key(), element);
			}
		}
		try {
			readHiddenOptions(json);
		} catch (Exception e) {
			EnchantmentReforged.LOGGER.warn("[Enchantment Reforged] 隐藏项解析失败：{}", e.toString());
		}
	}

	/** JSON 标量 → 内部统一的 double：布尔 1/0，数字直接取，字符串做容错 */
	private static double readNumber(JsonPrimitive primitive) {
		if (primitive.isBoolean()) {
			return primitive.getAsBoolean() ? 1.0D : 0.0D;
		}
		if (primitive.isNumber()) {
			return primitive.getAsDouble();
		}
		String text = primitive.getAsString().trim();
		if (text.equalsIgnoreCase("true")) {
			return 1.0D;
		}
		if (text.equalsIgnoreCase("false")) {
			return 0.0D;
		}
		return Double.parseDouble(text);
	}

	/** 读取两个隐藏项（1~10，越界直接夹紧） */
	private void readHiddenOptions(JsonObject json) {
		if (json.has(KEY_SHARPNESS_MAX_LEVEL) && json.get(KEY_SHARPNESS_MAX_LEVEL).isJsonPrimitive()) {
			sharpnessMaxLevel = Math.max(1, Math.min(10, json.get(KEY_SHARPNESS_MAX_LEVEL).getAsInt()));
		}
		if (json.has(KEY_PROTECTION_MAX_LEVEL) && json.get(KEY_PROTECTION_MAX_LEVEL).isJsonPrimitive()) {
			protectionMaxLevel = Math.max(1, Math.min(10, json.get(KEY_PROTECTION_MAX_LEVEL).getAsInt()));
		}
	}

	/** 把某一项恢复为默认值并保存（单项重置按钮调用） */
	public static void resetOption(Option option) {
		option.set(instance, option.defaultValue());
		instance.save();
	}

	/** 恢复默认值并保存 */
	public void resetToDefaults() {
		EnchantmentReforgedConfig defaults = new EnchantmentReforgedConfig();
		for (Option option : Option.values()) {
			option.set(this, option.defaultValue());
		}
		sharpnessMaxLevel = defaults.sharpnessMaxLevel;
		protectionMaxLevel = defaults.protectionMaxLevel;
		save();
	}

	private static Path configPath() {
		return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
	}

	/**
	 * 配置项定义表：图形界面与命令都遍历这份表。
	 * 构造参数依次为：键名、类型、最小值、最大值、步长、默认值。
	 */
	/** 配置分组：界面按它分成 9 个分类页（枚举顺序 = 标签顺序，标签栏两行 5+4） */
	public enum Category {
		CORE("core"),
		VANILLA_ENCHANTMENTS("vanilla_enchantments"),
		WEAPONS("weapons"),
		BOWS("bows"),
		SURVIVAL("survival"),
		COMBAT_STATE("combat_state"),
		DEFENSE("defense"),
		FOOD_GATHERING("food_gathering"),
		PARTICLES("particles");

		private final String key;

		Category(String key) {
			this.key = key;
		}

		public String key() {
			return key;
		}

		public String translationKey() {
			return "text.enchantment_reforged.category." + key;
		}
	}

	public enum Option {
		// ================= 核心机制 =================
		ENABLE_ANVIL_XP_COST(Category.CORE, "enable_anvil_xp_cost", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		ANVIL_MAX_COST_LEVEL(Category.CORE, "anvil_max_cost_level", Kind.INTEGER,
				1.0D, 100.0D, 1.0D, 30.0D),
		ENABLE_CUSTOM_SHARPNESS(Category.CORE, "enable_custom_sharpness", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		SHARPNESS_DAMAGE_PER_LEVEL(Category.CORE, "sharpness_damage_per_level", Kind.DOUBLE, 0.0D, 10.0D, 0.1D, 1.5D),
		ENABLE_STRENGTH_REWORK(Category.CORE, "enable_strength_rework", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		STRENGTH_MULTIPLIER_PER_LEVEL(Category.CORE, "strength_multiplier_per_level", Kind.DOUBLE, 0.0D, 10.0D, 0.05D, 0.5D),
		APPLY_STRENGTH_TO_MOBS(Category.CORE, "apply_strength_to_mobs", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),

		// ================= 原版附魔 =================
		ENABLE_PROTECTION_LEVEL_5(Category.VANILLA_ENCHANTMENTS, "enable_protection_level_5", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		ENABLE_IMPALING_BEDROCK_RULE(Category.VANILLA_ENCHANTMENTS, "enable_impaling_bedrock_rule", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		ENABLE_INFINITY_MENDING(Category.VANILLA_ENCHANTMENTS, "enable_infinity_mending", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		ENABLE_CROSSBOW_COMPAT(Category.VANILLA_ENCHANTMENTS, "enable_crossbow_compat", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		ENABLE_LOYALTY_REWORK(Category.VANILLA_ENCHANTMENTS, "enable_loyalty_rework", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),

		// ================= 武器附魔 =================
		ENABLE_SPELLBLADE(Category.WEAPONS, "enable_spellblade", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		SPELLBLADE_MAGIC_PERCENT_PER_LEVEL(Category.WEAPONS, "spellblade_magic_percent_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.08D),
		ENABLE_BEHEADING(Category.WEAPONS, "enable_beheading", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		BEHEADING_HEAD_CHANCE_PER_LEVEL(Category.WEAPONS, "beheading_head_chance_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.10D),
		ENABLE_EXECUTION(Category.WEAPONS, "enable_execution", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		EXECUTION_THRESHOLD(Category.WEAPONS, "execution_threshold", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.15D),
		ENABLE_LIFESTEAL(Category.WEAPONS, "enable_lifesteal", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		LIFESTEAL_PERCENT_PER_LEVEL(Category.WEAPONS, "lifesteal_percent_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.08D),
		ENABLE_QUICK_STRIKE(Category.WEAPONS, "enable_quick_strike", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		QUICK_STRIKE_BONUS_PER_LEVEL(Category.WEAPONS, "quick_strike_bonus_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.10D),
		ENABLE_SURPRISE(Category.WEAPONS, "enable_surprise", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		SURPRISE_CHANCE_PER_LEVEL(Category.WEAPONS, "surprise_chance_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.10D),

		// ================= 弓弩与箭矢 =================
		MULTISHOT_SPREAD_DEGREES(Category.BOWS, "multishot_spread_degrees", Kind.DOUBLE, 0.0D, 90.0D, 1.0D, 10.0D),
		ENABLE_MULTISHOT_ALL_HIT(Category.BOWS, "enable_multishot_all_hit", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		ENABLE_POTION_ARROW_FULL_DAMAGE(Category.BOWS, "enable_potion_arrow_full_damage", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		BOW_INACCURACY(Category.BOWS, "bow_inaccuracy", Kind.DOUBLE, 0.0D, 20.0D, 0.5D, 1.0D),
		ENABLE_ARROW_VELOCITY(Category.BOWS, "enable_arrow_velocity", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		ARROW_VELOCITY_BONUS_PER_LEVEL(Category.BOWS, "arrow_velocity_bonus_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.10D),
		ENABLE_QUICK_DRAW(Category.BOWS, "enable_quick_draw", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		QUICK_DRAW_SECONDS_PER_LEVEL(Category.BOWS, "quick_draw_seconds_per_level", Kind.DOUBLE, 0.0D, 0.2D, 0.01D, 0.10D),
		ENABLE_INSTINCTIVE_RELEASE(Category.BOWS, "enable_instinctive_release", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		ENABLE_PHANTOM_ARROW(Category.BOWS, "enable_phantom_arrow", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		PHANTOM_ARROW_CHANCE_PER_LEVEL(Category.BOWS, "phantom_arrow_chance_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.10D),
		ENABLE_ENDLESS_QUIVER(Category.BOWS, "enable_endless_quiver", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),

		// ================= 生存与治疗 =================
		ENABLE_LIFE_SHIELD(Category.SURVIVAL, "enable_life_shield", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		LIFE_SHIELD_PERCENT_PER_LEVEL(Category.SURVIVAL, "life_shield_percent_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.20D),
		ENABLE_HEALTH_BOOST(Category.SURVIVAL, "enable_health_boost", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		HEALTH_BOOST_PER_LEVEL(Category.SURVIVAL, "health_boost_per_level", Kind.DOUBLE, 0.0D, 10.0D, 0.5D, 1.0D),
		ENABLE_DEAD_MANS_HEART(Category.SURVIVAL, "enable_dead_mans_heart", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		DEAD_MANS_HEART_HEALTH_BONUS(Category.SURVIVAL, "dead_mans_heart_health_bonus", Kind.DOUBLE, 0.0D, 10.0D, 0.1D, 0.5D),
		DEAD_MANS_HEART_HEALING_PENALTY(Category.SURVIVAL, "dead_mans_heart_healing_penalty", Kind.DOUBLE, 0.0D, 1.0D, 0.05D, 0.5D),
		ENABLE_SOUL_GRACE(Category.SURVIVAL, "enable_soul_grace", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		SOUL_GRACE_COOLDOWN_SECONDS(Category.SURVIVAL, "soul_grace_cooldown_seconds", Kind.INTEGER, 0.0D, 3600.0D, 1.0D, 180.0D),
		ENABLE_CALAMITY(Category.SURVIVAL, "enable_calamity", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		ENABLE_NIMBLE_STEPS(Category.SURVIVAL, "enable_nimble_steps", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		NIMBLE_STEPS_CHANCE_PER_LEVEL(Category.SURVIVAL, "nimble_steps_chance_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.05D),

		// ================= 战斗状态 =================
		ENABLE_DEATHS_BLESSING(Category.COMBAT_STATE, "enable_deaths_blessing", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		DEATHS_BLESSING_DAMAGE_TAKEN_BONUS(Category.COMBAT_STATE, "deaths_blessing_damage_taken_bonus", Kind.DOUBLE, 0.0D, 2.0D, 0.01D, 0.20D),
		DEATHS_BLESSING_DAMAGE_BONUS_PER_PERCENT(Category.COMBAT_STATE, "deaths_blessing_damage_bonus_per_percent", Kind.DOUBLE, 0.0D, 0.1D, 0.001D, 0.01D),
		DEATHS_BLESSING_RESIST_PER_PERCENT(Category.COMBAT_STATE, "deaths_blessing_resist_per_percent", Kind.DOUBLE, 0.0D, 0.1D, 0.001D, 0.005D),
		ENABLE_REVENGE(Category.COMBAT_STATE, "enable_revenge", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		REVENGE_BONUS_BASE(Category.COMBAT_STATE, "revenge_bonus_base", Kind.DOUBLE, 0.0D, 2.0D, 0.01D, 0.05D),
		REVENGE_BONUS_PER_LEVEL(Category.COMBAT_STATE, "revenge_bonus_per_level", Kind.DOUBLE, 0.0D, 2.0D, 0.01D, 0.10D),

		// ================= 防御与耐久 =================
		ENABLE_STURDY(Category.DEFENSE, "enable_sturdy", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		STURDY_COEFFICIENT(Category.DEFENSE, "sturdy_coefficient", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.05D),
		STURDY_COEFFICIENT_STEP(Category.DEFENSE, "sturdy_coefficient_step", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.02D),
		ENABLE_UNDYING_GRACE(Category.DEFENSE, "enable_undying_grace", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		UNDYING_GRACE_DAMAGE_REDUCTION(Category.DEFENSE, "undying_grace_damage_reduction", Kind.DOUBLE, 0.0D, 20.0D, 0.5D, 1.0D),
		ENABLE_LIFE_MENDING(Category.DEFENSE, "enable_life_mending", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		LIFE_MENDING_DURABILITY_PER_LEVEL(Category.DEFENSE, "life_mending_durability_per_level", Kind.DOUBLE, 0.0D, 10.0D, 0.5D, 1.0D),

		// ================= 食物与采集 =================
		ENABLE_QUICK_EAT(Category.FOOD_GATHERING, "enable_quick_eat", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		QUICK_EAT_REDUCTION_PER_LEVEL(Category.FOOD_GATHERING, "quick_eat_reduction_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.05D, 0.25D),
		ENABLE_BIG_STOMACH(Category.FOOD_GATHERING, "enable_big_stomach", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		BIG_STOMACH_BONUS_PER_LEVEL(Category.FOOD_GATHERING, "big_stomach_bonus_per_level", Kind.DOUBLE, 0.0D, 20.0D, 1.0D, 4.0D),
		ENABLE_AUTO_SMELT(Category.FOOD_GATHERING, "enable_auto_smelt", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		ENABLE_SCHOLAR(Category.FOOD_GATHERING, "enable_scholar", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		SCHOLAR_BONUS_PER_LEVEL(Category.FOOD_GATHERING, "scholar_bonus_per_level", Kind.DOUBLE, 0.0D, 1.0D, 0.01D, 0.10D),

		// ================= 粒子与特效 =================
		// 客户端作用域：每名玩家各自的本地设置，服务端不同步
		ARROW_PARTICLE(Category.PARTICLES, "arrow_particle", Kind.ENUM, 0.0D, 12.0D, 1.0D, 0.0D, Scope.CLIENT),
		ARROW_PARTICLE_LONG_DISTANCE(Category.PARTICLES, "arrow_particle_long_distance", Kind.BOOLEAN,
				0.0D, 1.0D, 1.0D, 0.0D, Scope.CLIENT),
		ARROW_PARTICLE_DISTANCE(Category.PARTICLES, "arrow_particle_distance", Kind.INTEGER,
				16.0D, 512.0D, 16.0D, 128.0D, Scope.CLIENT),
		MELEE_PARTICLE(Category.PARTICLES, "melee_particle", Kind.ENUM, 0.0D, 12.0D, 1.0D, 0.0D, Scope.CLIENT),
		MELEE_SWEEP_PARTICLE(Category.PARTICLES, "melee_sweep_particle", Kind.ENUM,
				0.0D, 12.0D, 1.0D, 0.0D, Scope.CLIENT),
		MELEE_PARTICLE_EFFECT(Category.PARTICLES, "melee_particle_effect", Kind.ENUM,
				0.0D, 2.0D, 1.0D, 1.0D, Scope.CLIENT),
		// 客户端作用域：出其不意触发时的粒子（类型独立、强度沿用 melee_particle_effect）
		SURPRISE_PARTICLE(Category.PARTICLES, "surprise_particle", Kind.ENUM,
				0.0D, 12.0D, 1.0D, 2.0D, Scope.CLIENT),
		ENABLE_DEATH_PARTICLE(Category.PARTICLES, "enable_death_particle", Kind.BOOLEAN, 0.0D, 1.0D, 1.0D, 1.0D),
		// 客户端作用域：死亡粒子档位（固定强度，只决定粒子种类）
		DEATH_PARTICLE(Category.PARTICLES, "death_particle", Kind.ENUM,
				0.0D, 12.0D, 1.0D, 12.0D, Scope.CLIENT),
		// 客户端作用域：灵动步伐闪避时的音效档位（每名玩家各自设置，0 = 关闭）
		NIMBLE_STEPS_SOUND(Category.PARTICLES, "nimble_steps_sound", Kind.ENUM,
				0.0D, 8.0D, 1.0D, 3.0D, Scope.CLIENT);

		/** 配置项作用域：服务端项由服务端配置决定并同步；客户端项每名玩家各自保存在本地 */
		public enum Scope {
			SERVER,
			CLIENT
		}

		public enum Kind {
			BOOLEAN,
			DOUBLE,
			INTEGER,
			/** 枚举档位：界面用滑块，取值是 0 ~ max 的档位号 */
			ENUM
		}

		private final String key;
		private final Kind kind;
		private final double min;
		private final double max;
		private final double step;
		private final double defaultValue;
		private final Category category;
		private final Scope scope;

		Option(Category category, String key, Kind kind, double min, double max, double step, double defaultValue) {
			this(category, key, kind, min, max, step, defaultValue, Scope.SERVER);
		}

		Option(Category category, String key, Kind kind, double min, double max, double step, double defaultValue,
				Scope scope) {
			this.category = category;
			this.key = key;
			this.kind = kind;
			this.min = min;
			this.max = max;
			this.step = step;
			this.defaultValue = defaultValue;
			this.scope = scope;
		}

		public Category category() {
			return category;
		}

		public String key() {
			return key;
		}

		public Kind kind() {
			return kind;
		}

		/** 是否为"每名玩家各自的本地设置"（服务端不同步、命令不提供） */
		public boolean clientOnly() {
			return scope == Scope.CLIENT;
		}

		public double min() {
			return min;
		}

		public double max() {
			return max;
		}

		public double step() {
			return step;
		}

		public double defaultValue() {
			return defaultValue;
		}

		/** 语言键，界面与命令反馈共用 */
		public String translationKey() {
			return "text.enchantment_reforged.option." + key;
		}

		/** 详细说明的语言键：鼠标悬停在选项名上时显示 */
		public String tooltipKey() {
			return "text.enchantment_reforged.option." + key + ".tooltip";
		}

		/** 当前值是否等于默认值：界面用它决定这一行的重置按钮是否可点 */
		public boolean isDefault(EnchantmentReforgedConfig config) {
			return Math.abs(get(config) - defaultValue) < 1.0E-6D;
		}

		public double get(EnchantmentReforgedConfig config) {
			return switch (this) {
				case ENABLE_ANVIL_XP_COST -> config.enableAnvilXpCost ? 1.0D : 0.0D;
				case ANVIL_MAX_COST_LEVEL -> config.anvilMaxCostLevel;
				case ENABLE_CUSTOM_SHARPNESS -> config.enableCustomSharpness ? 1.0D : 0.0D;
				case SHARPNESS_DAMAGE_PER_LEVEL -> config.sharpnessDamagePerLevel;
				case ENABLE_STRENGTH_REWORK -> config.enableStrengthRework ? 1.0D : 0.0D;
				case STRENGTH_MULTIPLIER_PER_LEVEL -> config.strengthMultiplierPerLevel;
				case APPLY_STRENGTH_TO_MOBS -> config.applyStrengthToMobs ? 1.0D : 0.0D;
				case ENABLE_PROTECTION_LEVEL_5 -> config.enableProtectionLevel5 ? 1.0D : 0.0D;
				case ENABLE_IMPALING_BEDROCK_RULE -> config.enableImpalingBedrockRule ? 1.0D : 0.0D;
				case ENABLE_INFINITY_MENDING -> config.enableInfinityMending ? 1.0D : 0.0D;
				case ENABLE_CROSSBOW_COMPAT -> config.enableCrossbowCompat ? 1.0D : 0.0D;
				case ENABLE_LOYALTY_REWORK -> config.enableLoyaltyRework ? 1.0D : 0.0D;
				case MULTISHOT_SPREAD_DEGREES -> config.multishotSpreadDegrees;
				case ENABLE_MULTISHOT_ALL_HIT -> config.enableMultishotAllHit ? 1.0D : 0.0D;
				case ENABLE_POTION_ARROW_FULL_DAMAGE -> config.enablePotionArrowFullDamage ? 1.0D : 0.0D;
				case BOW_INACCURACY -> config.bowInaccuracy;
				case ENABLE_SPELLBLADE -> config.enableSpellblade ? 1.0D : 0.0D;
				case SPELLBLADE_MAGIC_PERCENT_PER_LEVEL -> config.spellbladeMagicPercentPerLevel;
				case ENABLE_BEHEADING -> config.enableBeheading ? 1.0D : 0.0D;
				case BEHEADING_HEAD_CHANCE_PER_LEVEL -> config.beheadingHeadChancePerLevel;
				case ENABLE_AUTO_SMELT -> config.enableAutoSmelt ? 1.0D : 0.0D;
				case ENABLE_ARROW_VELOCITY -> config.enableArrowVelocity ? 1.0D : 0.0D;
				case ARROW_VELOCITY_BONUS_PER_LEVEL -> config.arrowVelocityBonusPerLevel;
				case ENABLE_QUICK_DRAW -> config.enableQuickDraw ? 1.0D : 0.0D;
				case QUICK_DRAW_SECONDS_PER_LEVEL -> config.quickDrawSecondsPerLevel;
				case ENABLE_INSTINCTIVE_RELEASE -> config.enableInstinctiveRelease ? 1.0D : 0.0D;
				case ENABLE_LIFE_SHIELD -> config.enableLifeShield ? 1.0D : 0.0D;
				case LIFE_SHIELD_PERCENT_PER_LEVEL -> config.lifeShieldPercentPerLevel;
				case ENABLE_HEALTH_BOOST -> config.enableHealthBoost ? 1.0D : 0.0D;
				case HEALTH_BOOST_PER_LEVEL -> config.healthBoostPerLevel;
				case ENABLE_LIFESTEAL -> config.enableLifesteal ? 1.0D : 0.0D;
				case LIFESTEAL_PERCENT_PER_LEVEL -> config.lifestealPercentPerLevel;
				case ENABLE_SCHOLAR -> config.enableScholar ? 1.0D : 0.0D;
				case SCHOLAR_BONUS_PER_LEVEL -> config.scholarBonusPerLevel;
				case ENABLE_CALAMITY -> config.enableCalamity ? 1.0D : 0.0D;
				case ENABLE_DEATHS_BLESSING -> config.enableDeathsBlessing ? 1.0D : 0.0D;
				case DEATHS_BLESSING_DAMAGE_TAKEN_BONUS -> config.deathsBlessingDamageTakenBonus;
				case DEATHS_BLESSING_DAMAGE_BONUS_PER_PERCENT -> config.deathsBlessingDamageBonusPerPercent;
				case DEATHS_BLESSING_RESIST_PER_PERCENT -> config.deathsBlessingResistPerPercent;
				case ENABLE_DEATH_PARTICLE -> config.enableDeathParticle ? 1.0D : 0.0D;
				case DEATH_PARTICLE -> config.deathParticle;
				case ENABLE_QUICK_STRIKE -> config.enableQuickStrike ? 1.0D : 0.0D;
				case QUICK_STRIKE_BONUS_PER_LEVEL -> config.quickStrikeBonusPerLevel;
				case ENABLE_SURPRISE -> config.enableSurprise ? 1.0D : 0.0D;
				case SURPRISE_CHANCE_PER_LEVEL -> config.surpriseChancePerLevel;
				case ENABLE_EXECUTION -> config.enableExecution ? 1.0D : 0.0D;
				case EXECUTION_THRESHOLD -> config.executionThreshold;
				case ENABLE_QUICK_EAT -> config.enableQuickEat ? 1.0D : 0.0D;
				case QUICK_EAT_REDUCTION_PER_LEVEL -> config.quickEatReductionPerLevel;
				case ENABLE_BIG_STOMACH -> config.enableBigStomach ? 1.0D : 0.0D;
				case BIG_STOMACH_BONUS_PER_LEVEL -> config.bigStomachBonusPerLevel;
				case ENABLE_DEAD_MANS_HEART -> config.enableDeadMansHeart ? 1.0D : 0.0D;
				case DEAD_MANS_HEART_HEALTH_BONUS -> config.deadMansHeartHealthBonus;
				case DEAD_MANS_HEART_HEALING_PENALTY -> config.deadMansHeartHealingPenalty;
				case ENABLE_REVENGE -> config.enableRevenge ? 1.0D : 0.0D;
				case REVENGE_BONUS_BASE -> config.revengeBonusBase;
				case REVENGE_BONUS_PER_LEVEL -> config.revengeBonusPerLevel;
				case ENABLE_SOUL_GRACE -> config.enableSoulGrace ? 1.0D : 0.0D;
				case SOUL_GRACE_COOLDOWN_SECONDS -> config.soulGraceCooldownSeconds;
				case ENABLE_NIMBLE_STEPS -> config.enableNimbleSteps ? 1.0D : 0.0D;
				case NIMBLE_STEPS_CHANCE_PER_LEVEL -> config.nimbleStepsChancePerLevel;
				case NIMBLE_STEPS_SOUND -> config.nimbleStepsSound;
				case ENABLE_PHANTOM_ARROW -> config.enablePhantomArrow ? 1.0D : 0.0D;
				case PHANTOM_ARROW_CHANCE_PER_LEVEL -> config.phantomArrowChancePerLevel;
				case ENABLE_ENDLESS_QUIVER -> config.enableEndlessQuiver ? 1.0D : 0.0D;
				case ENABLE_LIFE_MENDING -> config.enableLifeMending ? 1.0D : 0.0D;
				case LIFE_MENDING_DURABILITY_PER_LEVEL -> config.lifeMendingDurabilityPerLevel;
				case ENABLE_STURDY -> config.enableSturdy ? 1.0D : 0.0D;
				case STURDY_COEFFICIENT -> config.sturdyCoefficient;
				case STURDY_COEFFICIENT_STEP -> config.sturdyCoefficientStep;
				case ENABLE_UNDYING_GRACE -> config.enableUndyingGrace ? 1.0D : 0.0D;
				case UNDYING_GRACE_DAMAGE_REDUCTION -> config.undyingGraceDamageReduction;
				case ARROW_PARTICLE -> config.arrowParticle;
				case ARROW_PARTICLE_LONG_DISTANCE -> config.arrowParticleLongDistance ? 1.0D : 0.0D;
				case ARROW_PARTICLE_DISTANCE -> config.arrowParticleDistance;
				case MELEE_PARTICLE -> config.meleeParticle;
				case MELEE_SWEEP_PARTICLE -> config.meleeSweepParticle;
				case MELEE_PARTICLE_EFFECT -> config.meleeParticleEffect;
				case SURPRISE_PARTICLE -> config.surpriseParticle;
			};
		}

		public boolean getBoolean(EnchantmentReforgedConfig config) {
			return get(config) >= 0.5D;
		}

		/** 写入配置，并自动夹到合法范围内 */
		public void set(EnchantmentReforgedConfig config, double value) {
			double clamped = Math.max(min, Math.min(max, value));
			switch (this) {
				case ENABLE_ANVIL_XP_COST -> config.enableAnvilXpCost = clamped >= 0.5D;
				case ANVIL_MAX_COST_LEVEL -> config.anvilMaxCostLevel = (int) Math.round(clamped);
				case ENABLE_CUSTOM_SHARPNESS -> config.enableCustomSharpness = clamped >= 0.5D;
				case SHARPNESS_DAMAGE_PER_LEVEL -> config.sharpnessDamagePerLevel = (float) clamped;
				case ENABLE_STRENGTH_REWORK -> config.enableStrengthRework = clamped >= 0.5D;
				case STRENGTH_MULTIPLIER_PER_LEVEL -> config.strengthMultiplierPerLevel = (float) clamped;
				case APPLY_STRENGTH_TO_MOBS -> config.applyStrengthToMobs = clamped >= 0.5D;
				case ENABLE_PROTECTION_LEVEL_5 -> config.enableProtectionLevel5 = clamped >= 0.5D;
				case ENABLE_IMPALING_BEDROCK_RULE -> config.enableImpalingBedrockRule = clamped >= 0.5D;
				case ENABLE_INFINITY_MENDING -> config.enableInfinityMending = clamped >= 0.5D;
				case ENABLE_CROSSBOW_COMPAT -> config.enableCrossbowCompat = clamped >= 0.5D;
				case ENABLE_LOYALTY_REWORK -> config.enableLoyaltyRework = clamped >= 0.5D;
				case MULTISHOT_SPREAD_DEGREES -> config.multishotSpreadDegrees = (float) clamped;
				case ENABLE_MULTISHOT_ALL_HIT -> config.enableMultishotAllHit = clamped >= 0.5D;
				case ENABLE_POTION_ARROW_FULL_DAMAGE -> config.enablePotionArrowFullDamage = clamped >= 0.5D;
				case BOW_INACCURACY -> config.bowInaccuracy = (float) clamped;
				case ENABLE_SPELLBLADE -> config.enableSpellblade = clamped >= 0.5D;
				case SPELLBLADE_MAGIC_PERCENT_PER_LEVEL -> config.spellbladeMagicPercentPerLevel = (float) clamped;
				case ENABLE_BEHEADING -> config.enableBeheading = clamped >= 0.5D;
				case BEHEADING_HEAD_CHANCE_PER_LEVEL -> config.beheadingHeadChancePerLevel = (float) clamped;
				case ENABLE_AUTO_SMELT -> config.enableAutoSmelt = clamped >= 0.5D;
				case ENABLE_ARROW_VELOCITY -> config.enableArrowVelocity = clamped >= 0.5D;
				case ARROW_VELOCITY_BONUS_PER_LEVEL -> config.arrowVelocityBonusPerLevel = (float) clamped;
				case ENABLE_QUICK_DRAW -> config.enableQuickDraw = clamped >= 0.5D;
				case QUICK_DRAW_SECONDS_PER_LEVEL -> config.quickDrawSecondsPerLevel = (float) clamped;
				case ENABLE_INSTINCTIVE_RELEASE -> config.enableInstinctiveRelease = clamped >= 0.5D;
				case ENABLE_LIFE_SHIELD -> config.enableLifeShield = clamped >= 0.5D;
				case LIFE_SHIELD_PERCENT_PER_LEVEL -> config.lifeShieldPercentPerLevel = (float) clamped;
				case ENABLE_HEALTH_BOOST -> config.enableHealthBoost = clamped >= 0.5D;
				case HEALTH_BOOST_PER_LEVEL -> config.healthBoostPerLevel = (float) clamped;
				case ENABLE_LIFESTEAL -> config.enableLifesteal = clamped >= 0.5D;
				case LIFESTEAL_PERCENT_PER_LEVEL -> config.lifestealPercentPerLevel = (float) clamped;
				case ENABLE_SCHOLAR -> config.enableScholar = clamped >= 0.5D;
				case SCHOLAR_BONUS_PER_LEVEL -> config.scholarBonusPerLevel = (float) clamped;
				case ENABLE_CALAMITY -> config.enableCalamity = clamped >= 0.5D;
				case ENABLE_DEATHS_BLESSING -> config.enableDeathsBlessing = clamped >= 0.5D;
				case DEATHS_BLESSING_DAMAGE_TAKEN_BONUS -> config.deathsBlessingDamageTakenBonus = (float) clamped;
				case DEATHS_BLESSING_DAMAGE_BONUS_PER_PERCENT -> config.deathsBlessingDamageBonusPerPercent = (float) clamped;
				case DEATHS_BLESSING_RESIST_PER_PERCENT -> config.deathsBlessingResistPerPercent = (float) clamped;
				case ENABLE_DEATH_PARTICLE -> config.enableDeathParticle = clamped >= 0.5D;
				case DEATH_PARTICLE -> config.deathParticle = (int) Math.round(clamped);
				case ENABLE_QUICK_STRIKE -> config.enableQuickStrike = clamped >= 0.5D;
				case QUICK_STRIKE_BONUS_PER_LEVEL -> config.quickStrikeBonusPerLevel = (float) clamped;
				case ENABLE_SURPRISE -> config.enableSurprise = clamped >= 0.5D;
				case SURPRISE_CHANCE_PER_LEVEL -> config.surpriseChancePerLevel = (float) clamped;
				case ENABLE_EXECUTION -> config.enableExecution = clamped >= 0.5D;
				case EXECUTION_THRESHOLD -> config.executionThreshold = (float) clamped;
				case ENABLE_QUICK_EAT -> config.enableQuickEat = clamped >= 0.5D;
				case QUICK_EAT_REDUCTION_PER_LEVEL -> config.quickEatReductionPerLevel = (float) clamped;
				case ENABLE_BIG_STOMACH -> config.enableBigStomach = clamped >= 0.5D;
				case BIG_STOMACH_BONUS_PER_LEVEL -> config.bigStomachBonusPerLevel = (float) clamped;
				case ENABLE_DEAD_MANS_HEART -> config.enableDeadMansHeart = clamped >= 0.5D;
				case DEAD_MANS_HEART_HEALTH_BONUS -> config.deadMansHeartHealthBonus = (float) clamped;
				case DEAD_MANS_HEART_HEALING_PENALTY -> config.deadMansHeartHealingPenalty = (float) clamped;
				case ENABLE_REVENGE -> config.enableRevenge = clamped >= 0.5D;
				case REVENGE_BONUS_BASE -> config.revengeBonusBase = (float) clamped;
				case REVENGE_BONUS_PER_LEVEL -> config.revengeBonusPerLevel = (float) clamped;
				case ENABLE_SOUL_GRACE -> config.enableSoulGrace = clamped >= 0.5D;
				case SOUL_GRACE_COOLDOWN_SECONDS -> config.soulGraceCooldownSeconds = (int) Math.round(clamped);
				case ENABLE_NIMBLE_STEPS -> config.enableNimbleSteps = clamped >= 0.5D;
				case NIMBLE_STEPS_CHANCE_PER_LEVEL -> config.nimbleStepsChancePerLevel = (float) clamped;
				case NIMBLE_STEPS_SOUND -> config.nimbleStepsSound = (int) Math.round(clamped);
				case ENABLE_PHANTOM_ARROW -> config.enablePhantomArrow = clamped >= 0.5D;
				case PHANTOM_ARROW_CHANCE_PER_LEVEL -> config.phantomArrowChancePerLevel = (float) clamped;
				case ENABLE_ENDLESS_QUIVER -> config.enableEndlessQuiver = clamped >= 0.5D;
				case ENABLE_LIFE_MENDING -> config.enableLifeMending = clamped >= 0.5D;
				case LIFE_MENDING_DURABILITY_PER_LEVEL -> config.lifeMendingDurabilityPerLevel = (float) clamped;
				case ENABLE_STURDY -> config.enableSturdy = clamped >= 0.5D;
				case STURDY_COEFFICIENT -> config.sturdyCoefficient = (float) clamped;
				case STURDY_COEFFICIENT_STEP -> config.sturdyCoefficientStep = (float) clamped;
				case ENABLE_UNDYING_GRACE -> config.enableUndyingGrace = clamped >= 0.5D;
				case UNDYING_GRACE_DAMAGE_REDUCTION -> config.undyingGraceDamageReduction = (float) clamped;
				case ARROW_PARTICLE -> config.arrowParticle = (int) Math.round(clamped);
				case ARROW_PARTICLE_LONG_DISTANCE -> config.arrowParticleLongDistance = clamped >= 0.5D;
				case ARROW_PARTICLE_DISTANCE -> config.arrowParticleDistance = (int) Math.round(clamped);
				case MELEE_PARTICLE -> config.meleeParticle = (int) Math.round(clamped);
				case MELEE_SWEEP_PARTICLE -> config.meleeSweepParticle = (int) Math.round(clamped);
				case MELEE_PARTICLE_EFFECT -> config.meleeParticleEffect = (int) Math.round(clamped);
				case SURPRISE_PARTICLE -> config.surpriseParticle = (int) Math.round(clamped);
			}
		}
	}
}
