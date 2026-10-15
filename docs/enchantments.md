# Enchantment Reforged 全附魔总表

> **适用版本**：1.3.8（Fabric 与 Forge 两树功能一致，只有映射名与少数客户端实现类不同，详见各节"实现位置"）。
> **数值口径**：文中数值都是**默认值**，实际以配置页或 `config/enchantment-reforged.json` 为准；配置键后的括号里写默认值。
> **命名空间**：本模组所有附魔 id 都是 `enchantment_reforged:<id>`；语言键为 `enchantment.enchantment_reforged.<id>`。
> 相关专题（铁砧、力量Plus、伤害管线与限伤、粒子与提示、兼容性）见文末「机制附录」。

## 速览表（32 个）

| id | 中文名（英文名） | 效果 | 目标 / 上限 | 宝藏·诅咒 | 互斥 | 主要配置键 |
| --- | --- | --- | --- | --- | --- | --- |
| `sharpness_plus` | 锋利Plus（SharpnessPlus） | 每级 +1.5 攻击伤害 | 剑斧 / 5 | 否 | 原版锋利 | `enable_custom_sharpness`、`sharpness_damage_per_level` |
| `spellblade` | 魔剑（Spellblade） | 追加本次攻击 8% × 等级 的无视护甲魔法伤害 | 剑斧 + 三叉戟 + 矛 / 5 | 否 | 无 | `enable_spellblade`、`spellblade_magic_percent_per_level` |
| `beheading` | 斩首（Beheading） | 击杀时 10% × 等级 掉落对应头颅 | 剑斧 / 3 | 否 | 无 | `enable_beheading`、`beheading_head_chance_per_level` |
| `execution` | 斩杀（Execution） | 目标生命 ≤15% 时补一记必杀 | 剑斧 + 三叉戟 + 矛 / 1 | 宝藏 | 无 | `enable_execution`、`execution_threshold` |
| `lifesteal` | 嗜血（Lifesteal） | 回复造成伤害 8% × 等级 的生命 | 剑斧 + 三叉戟 + 矛 / 3 | 否 | 无 | `enable_lifesteal`、`lifesteal_percent_per_level` |
| `quick_strike` | 迅捷打击（Quick Strike） | 攻击速度 +0.1 × 等级（加法） | 剑斧 + 三叉戟 + 矛 / 5 | 否 | 无 | `enable_quick_strike`、`quick_strike_bonus_per_level` |
| `surprise` | 出其不意（Surprise） | 每级 10% 几率让本次攻击再结算一次 | 剑斧 + 三叉戟 + 矛 / 3 | 宝藏 | 无 | `enable_surprise`、`surprise_chance_per_level`、`surprise_particle` |
| `arrow_velocity` | 疾矢（Arrow Velocity） | 箭矢初速 ×(1 + 10% × 等级) | 弓（弩需开互通） / 5 | 否 | 无 | `enable_arrow_velocity`、`arrow_velocity_bonus_per_level` |
| `quick_draw` | 速射（Quick Draw） | 满蓄力时间每级 -0.1 秒 | 弓 / 5 | 否 | 无 | `enable_quick_draw`、`quick_draw_seconds_per_level` |
| `instinctive_release` | 本能释放（Instinctive Release） | 弓拉满自动射出并立刻重新蓄力 | 弓 / 1 | 宝藏 | 无 | `enable_instinctive_release` |
| `phantom_arrow` | 幻影箭（Phantom Arrow） | 每级 10% 几率额外射出一支箭 | 弓 + 弩（需开互通） / 3 | 宝藏 | 无 | `enable_phantom_arrow`、`phantom_arrow_chance_per_level` |
| `endless_quiver` | 无尽箭袋（Endless Quiver） | 任何箭矢都不消耗，没箭也能射普通箭 | 弓 + 弩（需开互通） / 1 | 宝藏 | 原版无限 | `enable_endless_quiver` |
| `life_shield` | 生命护盾（Life Shield） | 回血时获得伤害吸收，上限按最大生命占比 | 胸甲 / 5 | 否 | 无 | `enable_life_shield`、`life_shield_percent_per_level` |
| `health_boost` | 生命提升（Health Boost） | 每级 +1 最大生命，四件盔甲等级相加 | 四件盔甲 / 5 | 否 | 无 | `enable_health_boost`、`health_boost_per_level` |
| `dead_mans_heart` | 死者之心（Dead Man's Heart） | 最大生命 ×1.5、受到的治疗 -50% | 胸甲 / 1 | 诅咒 | 无 | `enable_dead_mans_heart`、`dead_mans_heart_health_bonus`、`dead_mans_heart_healing_penalty` |
| `soul_grace` | 灵魂加护（Soul Grace） | 死亡时满血复活（清负面 + 2 秒抗性），有冷却 | 胸甲 / 1 | 宝藏 | 无 | `enable_soul_grace`、`soul_grace_cooldown_seconds` |
| `calamity` | 恶咒（Calamity） | 生命值无法大于 1 | 胸甲 / 1 | 诅咒 | 无 | `enable_calamity` |
| `nimble_steps` | 灵动步伐（Nimble Steps） | 每次受伤按 5% × 等级 几率完全闪避 | 靴子 / 3 | 宝藏 | 无 | `enable_nimble_steps`、`nimble_steps_chance_per_level`、`nimble_steps_sound` |
| `deaths_blessing` | 死神祝福（Death's Blessing） | 越残血：受伤越高但输出越高、受伤越低 | 胸甲 / 1 | 否 | 无 | `enable_deaths_blessing`、`deaths_blessing_damage_taken_bonus`、`deaths_blessing_damage_bonus_per_percent`、`deaths_blessing_resist_per_percent` |
| `revenge` | 复仇（Revenge） | 受击后 3 秒内近战伤害 +5%/15%/25% | 胸甲 / 3 | 否 | 无 | `enable_revenge`、`revenge_bonus_base`、`revenge_bonus_per_level` |
| `sturdy` | 坚甲（Sturdy） | 单次耐久损耗上限 = 最大耐久 × 5%/3%/1% | 四件盔甲 / 3 | 否 | 无 | `enable_sturdy`、`sturdy_coefficient`、`sturdy_coefficient_step` |
| `life_mending` | 生命修补（Life Mending） | 治疗改为修复该件装备耐久 | 有耐久物品 / 3 | 诅咒 | 无 | `enable_life_mending`、`life_mending_durability_per_level` |
| `quick_eat` | 速食（Quick Eat） | 进食时长每级 -25%（下限 8 tick） | 头盔 / 3 | 否 | 无 | `enable_quick_eat`、`quick_eat_reduction_per_level` |
| `big_stomach` | 大胃袋（Big Stomach） | 饥饿值上限每级 +4（20 → 40） | 胸甲 / 5 | 否 | 无 | `enable_big_stomach`、`big_stomach_bonus_per_level` |
| `auto_smelt` | 自动熔炼（Auto Smelt） | 挖掘掉落替换为熔炉产物 | 镐类 / 1 | 否 | 精准采集 | `enable_auto_smelt` |
| `scholar` | 经验学者（Scholar） | 经验球经验 +10% × 等级 | 头盔 / 3 | 否 | 无 | `enable_scholar`、`scholar_bonus_per_level` |
| `lightness` | 轻盈（Lightness） | 箭矢重力每级 -25%（总减重 ≤90%） | 弓 + 弩 / 3 | 否 | 无 | `enable_lightness`、`lightness_gravity_reduction_per_level` |
| `sky_wish` | 破空之愿（Wish of the Sky） | 蓄力每 tick +5% 箭矢伤害，上限 +200% | 弓 / 1 | 宝藏 | 无 | `enable_sky_wish`、`sky_wish_bonus_per_tick`、`sky_wish_max_bonus`、`arrow_trait_hint_particles` |
| `death_eye` | 死亡之眼（Death's Eye） | 命中累计 +25% × 层数（上限 8 层 = ×3.0） | 弓 + 弩 / 1 | 宝藏 | 无 | `enable_death_eye`、`death_eye_damage_per_stack`、`death_eye_max_stacks`、`arrow_trait_hint_particles` |
| `phase_step` | 闪身步（Phase Step） | 完全免疫投射物伤害（末影人本体口径） | 裤子 / 1 | 宝藏 | 无 | `enable_phase_step` |
| `hidden_blade` | 藏剑（Hidden Blade） | 未挥砍期间蓄力，下一刀最高 +400% | 仅剑 / 1 | 宝藏 | 无 | `enable_hidden_blade`、`hidden_blade_bonus_per_tick`、`hidden_blade_max_bonus`、`hidden_blade_cooldown_seconds` |
| `electric_blade` | 电刀（Electric Blade） | 近战主命中后链式弹射 1~4 段，每段为前一段的 80% | 仅剑 / 4 | 否 | 无 | `enable_electric_blade`、`electric_blade_radius`、`electric_blade_damage_ratio`、`electric_blade_delay_ticks` |

## 附魔详表

### 锋利Plus（`sharpness_plus`）

- **效果与公式**：每级 **+1.5 攻击伤害**（`sharpness_damage_per_level`，默认 1.5）；暴击时这份附魔加成同样被暴击系数放大。
- **目标 / 上限 / 稀有度**：剑斧（沿用原版锋利的目标物品），上限 **5**，稀有度 COMMON，非宝藏、非诅咒。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。开启时**原版锋利会退出附魔台与随机战利品池**。
- **互斥**：与原版**锋利**双向互斥（由同一个开关 `enable_custom_sharpness` 决定）。
- **相关配置键**：`enable_custom_sharpness`（开，服务端）、`sharpness_damage_per_level`（1.5，服务端）、隐藏项 `sharpness_max_level`（5，可在 1~10 之间改，仅配置文件）。
- **边界与实现位置**：不能附在三叉戟/矛上（保持原版限制）。实现：`SharpnessPlusEnchantment`（自带类）+ `EnchantmentMixin`（互斥与"移出随机可选范围"）。

### 魔剑（`spellblade`）

- **效果与公式**：追加伤害 = 本次攻击伤害 × **8% × 等级**（`spellblade_magic_percent_per_level`，默认 0.08），走原版间接魔法伤害（**无视护甲**）。
- **目标 / 上限 / 稀有度**：剑斧 + 三叉戟 + 矛模组的矛，上限 **5**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_spellblade`（开）、`spellblade_magic_percent_per_level`（0.08）（均服务端作用域）。
- **边界与实现位置**：追加伤害前会**清掉目标的受击冷却**，保证同一击的附加伤害真的落地；投掷三叉戟与矛命中同样结算；SR「强力斩击」不吃增伤乘区，但它的魔剑照常触发。实现：`EnchantmentEffects.applySpellbladeReport`，入口分别是 `PlayerEntityMixin`→`SummyReliquaryCompat.settleHit`（近战/SR）、`TridentEntityMixin`（投掷三叉戟）、`SpearItemCompatMixin`（矛）。

### 斩首（`beheading`）

- **效果与公式**：击杀时按 **10% × 等级**（`beheading_head_chance_per_level`，默认 0.10）额外掉落对应头颅。
- **目标 / 上限 / 稀有度**：剑斧，上限 **3**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_beheading`（开）、`beheading_head_chance_per_level`（0.10）（均服务端作用域）。
- **边界与实现位置**：**几率独立于抢夺**（全库没有 `Looting` 引用，头颅也不走原版掉落表，而是直接丢出）；含变种映射，击杀玩家掉落带皮肤的头颅，末影龙无效。实现：`EnchantmentReforgedEvents`（AFTER_DEATH）→ `EnchantmentEffects.onDeathForBeheading`。

### 斩杀（`execution`）

- **效果与公式**：目标当前生命 ≤ **15% 最大生命**（`execution_threshold`，默认 0.15）时补一刀必杀，伤害写死为 `(当前生命 + 吸收) × 5 + 10`。
- **目标 / 上限 / 稀有度**：剑斧 + 三叉戟 + 矛，上限 **1**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品（宝藏不进附魔台）/ 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_execution`（开）、`execution_threshold`（0.15）（均服务端作用域）。
- **边界与实现位置**：补刀使用**自定义伤害类型** `enchantment_reforged:execution`（注册在 `data/enchantment_reforged/damage_type/execution.json`，同时记录攻击者），因此有专属死亡信息；取不到该伤害类型时退回原版 `playerAttack`。SR「强力斩击」吃斩杀。实现：`EnchantmentEffects.tryExecuteReport` + 各近战入口。

### 嗜血（`lifesteal`）

- **效果与公式**：回复 **造成伤害 × 8% × 等级**（`lifesteal_percent_per_level`，默认 0.08）的生命。
- **目标 / 上限 / 稀有度**：剑斧 + 三叉戟 + 矛，上限 **3**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_lifesteal`（开）、`lifesteal_percent_per_level`（0.08）（均服务端作用域）。
- **边界与实现位置**：击杀也回血（按本次伤害而非目标剩余生命结算）；SR 的斩击与投掷命中同样结算。实现：`EnchantmentEffects.applyLifestealReport`，入口与魔剑相同。

### 迅捷打击（`quick_strike`）

- **效果与公式**：攻击速度 **+0.1 × 等级**（`quick_strike_bonus_per_level`，默认 0.10，**加法**修饰符）。
- **目标 / 上限 / 稀有度**：剑斧 + 三叉戟 + 矛，上限 **5**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_quick_strike`（开）、`quick_strike_bonus_per_level`（0.10）（均服务端作用域）。
- **边界与实现位置**：以属性修饰符 `enchantment_reforged:quick_strike` 实现，每 tick 仅在数值变化时写入。实现：`LivingEntityMixin#tick` → `EnchantmentEffects.updateEquipmentAttributes`。

### 出其不意（`surprise`）

- **效果与公式**：每级 **10%**（`surprise_chance_per_level`，默认 0.10）几率让本次攻击**再结算一次相同伤害**。
- **目标 / 上限 / 稀有度**：剑斧 + 三叉戟 + 矛，上限 **3**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_surprise`（开）、`surprise_chance_per_level`（0.10）（服务端）；粒子 `surprise_particle`（默认 2，**客户端作用域**，每名玩家各自设置）。
- **边界与实现位置**：二次结算会**再次触发魔剑与嗜血**；目标已死时不掷；粒子只在**二次伤害确实落地**后才播；SR「强力斩击」按设计**不掷**出其不意。实现：`SummyReliquaryCompat.settleHit`（左键近战与 SR 共用同一口径）+ `TridentEntityMixin`（投掷三叉戟）。

### 疾矢（`arrow_velocity`）

- **效果与公式**：箭矢初速度 ×(1 + **10% × 等级**)（`arrow_velocity_bonus_per_level`，默认 0.10）。
- **目标 / 上限 / 稀有度**：弓（弩需要开启 `enable_crossbow_compat` 互通），上限 **5**，COMMON。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令（弩需要互通开关）。
- **互斥**：无。
- **相关配置键**：`enable_arrow_velocity`（开）、`arrow_velocity_bonus_per_level`（0.10）（均服务端作用域）。
- **边界与实现位置**：弩射出的箭（含多重射击的三支）同样吃；额外箭是主箭副本，速度自动继承。实现：`BowItemMixin`（弓）/ `CrossbowItemMixin`（弩）。

### 速射（`quick_draw`）

- **效果与公式**：满蓄力时间每级 **-0.1 秒**（`quick_draw_seconds_per_level`，默认 0.10）。
- **目标 / 上限 / 稀有度**：弓，上限 **5**，UNCOMMON。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_quick_draw`（开）、`quick_draw_seconds_per_level`（0.10）（均服务端作用域）。
- **边界与实现位置**：只改原版威力曲线的"已过 tick"换算（等价于缩短满蓄力时间），客户端拉弓动画同步；与破空之愿无关（后者按**真实**蓄力 tick 算）。实现：`BowItemMixin`。

### 本能释放（`instinctive_release`）

- **效果与公式**：弓拉满即**自动射出**并立刻开始下一次蓄力（非开关型数值）。
- **目标 / 上限 / 稀有度**：弓，上限 **1**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_instinctive_release`（开，服务端）。
- **边界与实现位置**：服务端每 tick 检查"是否拉满"，客户端有对应部分保证动画同步。实现：`EnchantmentReforgedEvents.tickInstinctiveRelease`（服务端）+ `EnchantmentReforgedClient`（客户端）。

### 幻影箭（`phantom_arrow`）

- **效果与公式**：每级 **10%**（`phantom_arrow_chance_per_level`，默认 0.10）几率额外射出一支箭。
- **目标 / 上限 / 稀有度**：弓 + 弩（弩需要互通开关），上限 **3**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_phantom_arrow`（开）、`phantom_arrow_chance_per_level`（0.10）（均服务端作用域）。
- **边界与实现位置**：每支箭**独立判定**；额外箭复制主箭属性（伤害、暴击、击退、穿透、着火、药水效果、粒子档位、轻盈等级、死亡之眼归属），**不可拾取**、不额外消耗弹药，并显式复制归属者（击杀计入射手）；与多重射击**不互斥**（同时存在时最多 4 支）。实现：`BowItemMixin` / `CrossbowItemMixin` → `EnchantmentEffects.spawnExtraArrow`。

### 无尽箭袋（`endless_quiver`）

- **效果与公式**：**任何箭矢**（含药水箭、光灵箭）都不被消耗，且没有箭时也能射出普通箭。
- **目标 / 上限 / 稀有度**：弓 + 弩（弩需要互通开关），上限 **1**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：与原版**无限**双向互斥（它是无限的上位替代）。
- **相关配置键**：`enable_endless_quiver`（开，服务端）。
- **边界与实现位置**：免耗实现是把原版 `split(1)` 换成"返回副本"；互斥判定在 `EnchantmentMixin`。实现：`BowItemMixin` / `CrossbowItemMixin` + `EnchantmentEffects.sparesAmmo`。

### 生命护盾（`life_shield`）

- **效果与公式**：回复生命时获得 **回复量 × 20% × 等级**（`life_shield_percent_per_level`，默认 0.20）的伤害吸收，护盾上限为 **最大生命 × 20% × 等级**。
- **目标 / 上限 / 稀有度**：胸甲，上限 **5**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_life_shield`（开）、`life_shield_percent_per_level`（0.20）（均服务端作用域）。
- **边界与实现位置**：护盾值存在**自定义玩家属性** `enchantment_reforged:life_shield`（与附魔 id 同名但属于不同注册表），**不再占用效果栏、没有图标**，黄心由原版吸收显示，与其它来源的吸收**相加**（不抢其它模组的护盾）；满血时不累积；死亡重生不残留。实现：`LivingEntityMixin`（heal / tick）+ `EnchantmentEffects.lifeShield*`。

### 生命提升（`health_boost`）

- **效果与公式**：每级 **+1 最大生命**（`health_boost_per_level`，默认 1.0）；**四件盔甲的等级相加**，5 级 ×4 件 = 最多 +20。
- **目标 / 上限 / 稀有度**：四件盔甲，上限 **5**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_health_boost`（开）、`health_boost_per_level`（1.0）（均服务端作用域）。
- **边界与实现位置**：以属性修饰符 `enchantment_reforged:health_boost`（加法）实现，与死者之心的乘法修饰符叠加；每 tick 仅在数值变化时写入。实现：`LivingEntityMixin#tick` → `EnchantmentEffects.updateEquipmentAttributes`。

### 死者之心（`dead_mans_heart`）

- **效果与公式**：最大生命 **×1.5**（`dead_mans_heart_health_bonus`，默认 0.5）、受到的治疗 **-50%**（`dead_mans_heart_healing_penalty`，默认 0.5）。
- **目标 / 上限 / 稀有度**：胸甲，上限 **1**，VERY_RARE，**诅咒**。
- **获取方式**：**可以出现在附魔台**（附魔台不排除诅咒）/ 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_dead_mans_heart`（开）、`dead_mans_heart_health_bonus`（0.5）、`dead_mans_heart_healing_penalty`（0.5）（均服务端作用域）。
- **边界与实现位置**：治疗减半会连带影响生命护盾的累积（护盾按治疗量换算）；以乘法属性修饰符实现。实现：`LivingEntityMixin`（heal 缩放）+ `updateEquipmentAttributes`。

### 灵魂加护（`soul_grace`）

- **效果与公式**：死亡时取消这次死亡，改为 **满血 + 清除全部负面效果 + 2 秒抗性 IV（80% 减免）**，冷却 **180 秒**（`soul_grace_cooldown_seconds`）。
- **目标 / 上限 / 稀有度**：胸甲，上限 **1**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_soul_grace`（开）、`soul_grace_cooldown_seconds`（180）（均服务端作用域）。
- **边界与实现位置**：免死瞬间固定播放**原版图腾音效 + 图腾粒子爆发**（不加开关）；冷却以**服务端计时**为权威，效果栏里的"灵魂加护 冷却中"图标（状态效果 id 与附魔同名 `enchantment_reforged:soul_grace`）只作显示，喝牛奶或 `/effect clear` 最多 1 秒后自动回来且**冷却不被重置**；与恶咒同时穿戴时复活后血量为 1。实现：`EnchantmentReforgedEvents`（ALLOW_DEATH）+ `EnchantmentEffects.trySoulGrace` / `syncSoulGraceCooldown` + `ModStatusEffects`。

### 恶咒（`calamity`）

- **效果与公式**：生命值**无法大于 1**（不影响最大生命值；回血逻辑照常执行）。
- **目标 / 上限 / 稀有度**：胸甲，上限 **1**，RARE，**诅咒**。
- **获取方式**：**可以出现在附魔台**（附魔台不排除诅咒）/ 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_calamity`（开，服务端）。
- **边界与实现位置**：锁血放在每 tick 的末尾而非伤害流程，因此"回血类联动"（例如生命护盾累积）依然成立；与灵魂加护同穿时复活后为 1 血。实现：`LivingEntityMixin`（`setHealth` 裁剪 + tick 兜底）+ `EnchantmentEffects.isCalamityActive`。

### 灵动步伐（`nimble_steps`）

- **效果与公式**：每次受伤按 **5% × 等级**（`nimble_steps_chance_per_level`，默认 0.05）几率**完全无效化**该次伤害（虚空、指令伤害同样可闪避）。
- **目标 / 上限 / 稀有度**：靴子，上限 **3**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_nimble_steps`（开）、`nimble_steps_chance_per_level`（0.05）（服务端）；音效档位 `nimble_steps_sound`（默认 3 = 幻术师镜影，**客户端作用域**，0 = 关闭）。
- **边界与实现位置**：标签 `#enchantment_reforged:no_dodge` 里的伤害类型**无法被闪避**（例如 SRA 的凋亡元素伤害）；闪避成功时在**受击者位置**按**该玩家自己上报的档位**播音效（0 只静音，不影响闪避本身）。实现：`LivingEntityMixin#damage` → `EnchantmentEffects.rollNimbleSteps` + `ConfigSync`（音效档位随客户端偏好上报）。

### 死神祝福（`deaths_blessing`）

- **效果与公式**：受到的伤害 ×(1 + **20%** − **0.5%** × 已损失生命百分比)；造成的伤害 ×(1 + **1%** × 已损失生命百分比)。
- **目标 / 上限 / 稀有度**：胸甲，上限 **1**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_deaths_blessing`（开）、`deaths_blessing_damage_taken_bonus`（0.20）、`deaths_blessing_damage_bonus_per_percent`（0.01）、`deaths_blessing_resist_per_percent`（0.005）（均服务端作用域）。
- **边界与实现位置**：**受伤侧对全类型生效**（没有伤害类型过滤）；**造成侧只算直接来源**（攻击者本人或他射出的投射物；TNT、火焰、爆炸等间接伤害不吃，自伤也不吃造成侧），并且**跳过**定值真伤——标签 `#enchantment_reforged:no_deaths_blessing_outgoing` 默认含 SR 的 11 个与 SRA 的 3 个伤害类型；SR「强力斩击」整条链路（含它的魔剑/斩杀）同样不吃造成侧。实现：`LivingEntityMixin`（`hurt` 的 `@ModifyVariable(argsOnly = true)`）+ `EnchantmentEffects.deathsBlessingIncoming/Outgoing`。

### 复仇（`revenge`）

- **效果与公式**：受击后 **3 秒**内（每次受击刷新）近战伤害 **+5% / +15% / +25%**（`revenge_bonus_base` 默认 0.05，每级步进 `revenge_bonus_per_level` 默认 0.10）。
- **目标 / 上限 / 稀有度**：胸甲，上限 **3**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_revenge`（开）、`revenge_bonus_base`（0.05）、`revenge_bonus_per_level`（0.10）（均服务端作用域）。
- **边界与实现位置**：只作用于**剑 / 斧 / 三叉戟的近战**（不含弓与空手；矛模组的刺击走自己的乘子——只有左键刺击吃力量Plus，复仇不在其列）。窗口在"伤害真正落地"时刷新；SR 的基础斩击与投掷命中吃，强力斩击不吃。实现：`LivingEntityMixin#damage` → `EnchantmentEffects.onDamaged`（记录窗口）+ `PlayerEntityMixin` / `SummyReliquaryCompat`（读取乘子）。

### 坚甲（`sturdy`）

- **效果与公式**：单次耐久损耗上限 = `max(1, floor(最大耐久 × 系数))`，系数按等级递减 **5% / 3% / 1%**（`sturdy_coefficient` 默认 0.05，每级 -`sturdy_coefficient_step` 默认 0.02）。
- **目标 / 上限 / 稀有度**：四件盔甲，上限 **3**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_sturdy`（开）、`sturdy_coefficient`（0.05）、`sturdy_coefficient_step`（0.02）（均服务端作用域）。
- **边界与实现位置**：所有"扣耐久"最终都经过同一个入口，所以对各类损耗都生效。实现：`ItemStackMixin` + `EnchantmentEffects`。

### 生命修补（`life_mending`）

- **效果与公式**：治疗不再回血，改为按 **每 1 点治疗量 → `life_mending_durability_per_level`（默认 1）× 等级 点耐久**修复**带该附魔的那件**装备，并**封住这次治疗**。
- **目标 / 上限 / 稀有度**：任何有耐久且可附魔的物品（四件盔甲 + 主/副手），上限 **3**，RARE，**诅咒**。
- **获取方式**：**可以出现在附魔台**（附魔台不排除诅咒）/ 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_life_mending`（开）、`life_mending_durability_per_level`（1.0）（均服务端作用域）。
- **边界与实现位置**：**满血时同样生效**（用本次治疗量换算耐久）；装备满耐久/未损坏时跳过，换算后不足 1 点也不修，这两种情况会正常回血；与恶咒联动不变。实现：`LivingEntityMixin#heal` + `ItemStackMixin`（耐久入口）。

### 速食（`quick_eat`）

- **效果与公式**：进食时长每级 **-25%**（`quick_eat_reduction_per_level`，默认 0.25）：32 → 24 → 16 → 8 tick，**下限 8 tick**。
- **目标 / 上限 / 稀有度**：头盔，上限 **3**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_quick_eat`（开）、`quick_eat_reduction_per_level`（0.25）（均服务端作用域）。
- **边界与实现位置**：只影响"吃食物"的时长（不影响药水、牛奶等其它使用）。实现：`LivingEntityMixin`（使用时长）+ `EnchantmentEffects.quickEatUseTicks`。

### 大胃袋（`big_stomach`）

- **效果与公式**：饥饿值上限 **+4 × 等级**（`big_stomach_bonus_per_level`，默认 4）：20 → 40；饱和度上限同步抬高（数值口径不变）。
- **目标 / 上限 / 稀有度**：胸甲，上限 **5**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_big_stomach`（开）、`big_stomach_bonus_per_level`（4）（均服务端作用域）。
- **边界与实现位置**：HUD 在饥饿条**上方单起一行**显示"多出来的饥饿值"，溢出饱和度在该行叠加**金色描边**（贴图内置 `textures/gui/appleskin_icons.png`，**不依赖苹果皮**）；创造/旁观模式、骑乘生物、额外饥饿值为 0 时都不画；与"经典状态条"（Classic Bar）兼容——检测到它时按同一份堆叠偏移排在其所有条的上方。实现：数据侧 `HungerManagerMixin`；HUD 侧 Fabric 为 `InGameHudMixin`、Forge 为 `ExtraHungerOverlay`（注册在 `VanillaGuiOverlay.AIR_LEVEL` 之上）。

### 自动熔炼（`auto_smelt`）

- **效果与公式**：挖掘掉落替换为**熔炉产物**（只有该掉落物存在熔炉配方时才替换）。
- **目标 / 上限 / 稀有度**：镐类（原版 DIGGER 目标），上限 **1**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：与**精准采集**双向互斥。
- **相关配置键**：`enable_auto_smelt`（开，服务端）。
- **边界与实现位置**：**潜行时自动关闭**。实现：`BlockMixin` + `EnchantmentMixin`（互斥）。

### 经验学者（`scholar`）

- **效果与公式**：**仅经验球拾取**时经验 +10% × 等级（`scholar_bonus_per_level`，默认 0.10）。
- **目标 / 上限 / 稀有度**：头盔，上限 **3**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_scholar`（开）、`scholar_bonus_per_level`（0.10）（均服务端作用域）。
- **边界与实现位置**：只影响经验球拾取，不影响交易、钓鱼、熔炉等其它经验来源。实现：`ExperienceOrbEntityMixin` + `EnchantmentEffects.scholarBonus`。

### 轻盈（`lightness`）

- **效果与公式**：箭矢重力 ×(1 − **25% × 等级**)（`lightness_gravity_reduction_per_level`，默认 0.25），运行时总减重夹到 ≤90%（防反向重力）。
- **目标 / 上限 / 稀有度**：**弓 + 弩两用**（不受"弓弩附魔互通"开关影响），上限 **3**，RARE。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_lightness`（开）、`lightness_gravity_reduction_per_level`（0.25）（均服务端作用域）。
- **边界与实现位置**：只作用于**弓/弩射出的箭**（含额外箭），不作用于矛或 SR 投掷物；重力是两端各自计算的常量，因此等级通过 **S2C 通道**（实体 id + 等级，协议版本 9）同步给 64 格内同维度玩家（对方需装本模组），保证射手与旁观者看到的弹道一致。实现：`PersistentProjectileEntityMixin`（`tick` 的重力常量）+ `ArrowTraits` + `ConfigSync`。

### 破空之愿（`sky_wish`）

- **效果与公式**：箭矢伤害 ×(1 + min(**5% × 真实蓄力 tick**, **+200%**))，**0 tick 起算**：默认 40 tick 封顶（`sky_wish_bonus_per_tick` 0.05、`sky_wish_max_bonus` 2.0）。
- **目标 / 上限 / 稀有度**：**仅弓**，上限 **1**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_sky_wish`（开）、`sky_wish_bonus_per_tick`（0.05）、`sky_wish_max_bonus`（2.0）（服务端）；提示粒子 `arrow_trait_hint_particles`（开，服务端，与死亡之眼共用）。
- **边界与实现位置**：乘在**基础伤害**（已含原版力量加值）上，并在生成额外箭**之前**应用，所以额外箭自动继承；与速射的换算无关（取真实蓄力 tick）；弩不生效；**满蓄力时**在射手身上每 tick 播 2 个附魔符文（`ENCHANT`）作提示，松手即停。实现：`BowItemMixin`（伤害）+ `ItemMixin`（每 tick 提示）。

### 死亡之眼（`death_eye`）

- **效果与公式**：命中累计层数 `n`（0~8），箭矢伤害 ×(**4 + n**) / 4，即每层 +25%（`death_eye_damage_per_stack` 0.25、`death_eye_max_stacks` 8）；**先按当前 n 算伤害，命中后再 n+1**，第 9 击起稳定 ×3.0。
- **目标 / 上限 / 稀有度**：**弓 + 弩两用**，上限 **1**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_death_eye`（开）、`death_eye_damage_per_stack`（0.25）、`death_eye_max_stacks`（8）（服务端）；提示粒子 `arrow_trait_hint_particles`（开，服务端，与破空之愿共用）。
- **边界与实现位置**：层数按**射手**累计、跨武器共享，**死亡 / 重生 / 断线即清空**；穿透一箭多目标时每个目标各结算一次（后面目标吃更高层）。**清空规则（硬规则）**：只有这支箭**整段飞行未命中任何生物**且已插地/被移除时才清空（固定 100%）；**穿透命中过生物后落地不清空**；命中非生物实体（船、矿车等）算落空；清空时打一条按玩家 5 秒节流的日志。**层数达到上限时**在射手肩边每 tick 播 2 个附魔命中粒子（`ENCHANTED_HIT` 十字闪），与破空之愿的符文一眼可分，层数清零即停。实现：`PersistentProjectileEntityMixin`（命中/落空结算）+ `ArrowTraits` + `EnchantmentReforgedEvents`（提示粒子）。

### 闪身步（`phase_step`）

- **效果与公式**：穿在裤子上时**完全免疫投射物伤害**（不扣血、不进无敌帧、不会被击退）。
- **目标 / 上限 / 稀有度**：裤子（`ARMOR_LEGS`），上限 **1**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_phase_step`（开，服务端）。
- **边界与实现位置**：判定完全照搬末影人本体——只看伤害源是否在**原版标签** `#minecraft:is_projectile` 里（箭、雪球、鸡蛋、投掷三叉戟、火球等都算，整合包可用数据包改这个标签）；**喷溅药水的直击伤害不在该标签内，因此不免疫**；免疫时**不做传送**；不受 `#enchantment_reforged:no_dodge` 影响；与灵动步伐共存时**先判闪身步**，命中时不消耗灵动步伐的随机数。表现：命中瞬间在受击者身上播 8 个末影粒子（`PORTAL`），无音效。实现：`LivingEntityMixin` 的 `damage`/`hurt` HEAD 注入 + `EnchantmentEffects.isPhaseStepImmune`。

### 藏剑（`hidden_blade`）

- **效果与公式**：未挥砍期间每 tick 累积 **+2%**（`hidden_blade_bonus_per_tick` 0.02）下一刀的伤害加成，上限 **+400%**（`hidden_blade_max_bonus` 4.0，即默认 10 秒封顶）；加成作为**攻击侧乘子**与力量、复仇同乘区：`原版伤害 × 力量 × 复仇 × (1 + 藏剑加成)`。
- **目标 / 上限 / 稀有度**：**严格只剑**，上限 **1**，VERY_RARE，**宝藏**。
- **获取方式**：铁砧 / 随机战利品 / 创造书 / 命令；附魔台也会给剑产出（斧头不会，见下）。
- **互斥**：无。
- **相关配置键**：`enable_hidden_blade`（开）、`hidden_blade_bonus_per_tick`（0.02）、`hidden_blade_max_bonus`（4.0）、`hidden_blade_cooldown_seconds`（2）（均服务端作用域）；满额提示沿用共用开关 `arrow_trait_hint_particles`。
- **边界与实现位置**：**只有主手持着带该附魔的剑时才累积**（离开主手暂停累积但**保留**已攒值）；**任何近战挥砍都清空并进入 2 秒冷却**（含打空、砍空气），冷却期间停止累积；只作用于**主命中**（横扫不加成），SR 斩击与投掷不参与；死亡 / 重生 / 断线清空。满额时在持有人身上每 tick 播 2 个暴击星（`CRIT`）提示，挥出后消失。实现：`PlayerEntityMixin`（主命中乘子 + `attack` TAIL 清空）+ `EnchantmentReforgedEvents`（每 tick 累积与提示）。

### 电刀（`electric_blade`）

- **效果与公式**：近战**主命中落地**后向附近目标**链式弹射**，段数 = 附魔等级（1~4 段，最多打 5 个目标）；每段伤害 = **攻击侧金额 × 0.8ⁿ**（`electric_blade_damage_ratio` 0.8），四段依次约 ×0.8 / ×0.64 / ×0.512 / ×0.41。
- **目标 / 上限 / 稀有度**：**严格只剑**，上限 **4**，VERY_RARE，**非宝藏**（附魔台可产出，但只会给剑）。
- **获取方式**：附魔台 / 铁砧 / 随机战利品 / 创造书 / 命令。
- **互斥**：无。
- **相关配置键**：`enable_electric_blade`（开）、`electric_blade_radius`（5.0）、`electric_blade_damage_ratio`（0.8）、`electric_blade_delay_ticks`（2）（均服务端作用域）。
- **边界与实现位置**：**只对玩家的左键近战主命中生效**（生物带该附魔不弹射；SR 斩击与投掷不触发）；选目标是"以上一段命中实体为圆心、半径 5 格内**最近且本次链未命中过**的活体"（排除攻击者本人），找不到就结束；**基数取攻击侧金额**（含力量/复仇/藏剑加成，**不含**护甲减免、目标受伤倍率与魔剑追加），每段照常吃目标侧倍率与一层造成侧；**每段先清目标无敌帧**再打出，伤害源用原版 `playerAttack`；**每段各结算一次魔剑与嗜血**（定稿），但**不**触发斩杀、不出其不意、也不会再触发电刀；段间延迟 0.1 秒（默认 2 tick）由服务端队列推进，攻击者离线即终止；表现是沿线电火花（`ELECTRIC_SPARK`）+ 命中点小爆发，服务端广播。实现：`PlayerEntityMixin`（启动）+ `combat/ElectricArc`（队列）+ `EnchantmentReforgedEvents`（每 tick 推进）。
- **叠加口径提醒**：4 级电刀 + 魔剑 5 级时，四段合计的魔剑追加 ≈ 主命中金额 × (0.8+0.64+0.512+0.41) × 0.08 × 5 ≈ **主命中金额的 94%**（嗜血同理按 ×0.19×等级 回血）；这是"每段各结算一次"的既定口径。

## 机制附录

以下内容不是附魔，但会影响玩法与读数口径，配置页里都能找到对应条目（键名后括号是默认值，作用域默认"服务端"）。

### 铁砧改造（核心机制组）

- `enable_anvil_xp_cost`（开）：铁砧改为消耗**经验点数**计价，并移除"过于昂贵"的限制；界面上显示"等级 X ／ Y 点"。
- `anvil_max_cost_level`（30）：铁砧单次可承受的最高等级上限。
- 实现：`AnvilScreenHandlerMixin` / `AnvilScreenMixin`（两树同名）。

### 锋利Plus 与力量Plus（核心机制组）

- **锋利Plus**：见上表同名附魔小节（`enable_custom_sharpness`、`sharpness_damage_per_level`，隐藏项 `sharpness_max_level`）。
- **力量Plus**：`enable_strength_rework`（开）、`strength_multiplier_per_level`（0.5）——把原版力量药水重做成**乘算增伤**（每级 +50%），原版力量效果会被自动替换成新实现（旧存档/旧药水在实体加载时也会被替换）；`apply_strength_to_mobs`（开）决定生物是否同样享受。

### 原版附魔调整（原版改动组）

- `enable_protection_level_5`（开）：允许原版保护到 5 级（铁砧两个保护 IV → V）；隐藏项 `protection_max_level`（5，仅配置文件可改）。
- `enable_impaling_bedrock_rule`（开）：穿刺按**基岩版规则**判定（攻击时把目标生物类型视为水生，接触水或淋雨即可）。
- `enable_infinity_mending`（开）：允许**无限 + 经验修补**共存。
- `enable_crossbow_compat`（开）：**弓弩附魔互通**——弩能附力量/无限/火矢/冲击与本模组弓类附魔（疾矢、幻影箭、无尽箭袋），弓能附原版弩的**多重射击 / 穿透**；关闭时两侧同时失效。
- `enable_loyalty_rework`（开）：**忠诚改版**——投掷时本体留在物品栏，飞出的只是"假三叉戟"，返回前无法再次投掷（快捷栏显示冷却）；关闭后完全恢复原版。
- 弓弩与箭矢组的机制项：`multishot_spread_degrees`（10）、`enable_multishot_all_hit`（开）、`enable_potion_arrow_full_damage`（开）、`bow_inaccuracy`（1.0）。

### 不死者加护（防御与耐久组，非附魔）

- `enable_undying_grace`（开）、`undying_grace_damage_reduction`（1.0）：主手或副手持有**不死图腾**时，在护甲/保护减伤之后、伤害吸收之前，**直接减免固定点数**（默认 1 点）。

### 伤害管线与限伤核验

- 三条核验结论：① **死神祝福·受伤侧本来就是全类型**（`hurt` 的 `argsOnly` 修改，无伤害类型过滤）；② **造成侧此前只覆盖近战类**，1.3.7 起统一到受伤侧按攻击者结算（只算**直接来源**，跳过自伤与标签排除的定值真伤）；③ ER 的"同 tick 多段"是"清无敌帧 + 再 `hurt` 一次"，**打不过**灾变 / 诡厄巫法 / Mowzie's 那类**实体级或事件层限伤**（`bypasses_armor` / `bypasses_enchantments` 标签只作用于原版减伤链）。
- **箭矢完整公式**：`基础伤害（含原版力量加值）→ × 死亡之眼 (4+n)/4 → × 箭速倍率（疾矢）→ 命中时 × 死神祝福·造成侧 → 目标侧：死神祝福·受伤侧 → 护盾/原版吸收 → 第三方拦截或限伤`。
- **伤害类型标签**（整合包可直接在自己的数据包里增删，`/reload` 生效）：
  - `#enchantment_reforged:no_dodge`：这些伤害**无法被灵动步伐闪避**（默认含 SRA 的凋亡元素）。
  - `#enchantment_reforged:no_deaths_blessing_outgoing`：这些伤害**不吃死神祝福·造成侧**（默认含 SR 的 11 个与 SRA 的 3 个定值真伤）。
- **额外伤害只吃一层**：魔剑、出其不意这类"基于本次攻击金额"的追加伤害，其基数取自**攻击侧金额**（不含造成侧），因此受伤侧只会为它们补一层，不存在平方叠加。

### 粒子与提示（粒子与特效组）

共 12 项：**9 项客户端作用域**（每名玩家各自设置；服务端不同步、命令里也不提供）+ 3 项服务端作用域。

- 客户端作用域：`arrow_particle`（0）、`arrow_particle_long_distance`（关）、`arrow_particle_distance`（128）、`melee_particle`（0）、`melee_sweep_particle`（0）、`melee_particle_effect`（1，档位 0~2）、`surprise_particle`（2）、`death_particle`（12）、`nimble_steps_sound`（3，0 = 关闭）。
- 服务端作用域：`arrow_particle_other_projectiles`（开，是否把其它模组的箭也算作"箭"）、`enable_death_particle`（开）、`arrow_trait_hint_particles`（开，破空之愿满蓄力与死亡之眼满层的提示粒子，两处共用）。
- 口径：**0 = 原版默认（不介入）**；箭矢与近战/横扫/出其不意的档位由攻击者随客户端偏好上报，服务端再广播给附近玩家；**只有玩家发起的行为才有自定义粒子**（生物、发射器、指令生成的箭保持原版）。

### 兼容性

- **Summy Reliquary（SR）**：把「遁入暗影」的两段斩击与投掷长矛命中接回 ER 的近战管线——基础斩击与投掷命中吃全部增伤与命中后效果；**强力斩击**按 SR 原设计不吃增伤乘区（含死神祝福·造成侧，1.3.7 起由"造成侧抑制窗口"显式保证），但仍吃魔剑/嗜血/粒子与斩杀、不掷出其不意；`debug_sr_compat`（开）可打印逐次结算日志。未安装 SR 时整份兼容被跳过。
- **Backported Spears（矛）**：修复其玩家分支"基础伤害算两遍"导致的伤害翻倍；左键刺击额外吃力量Plus。未安装矛模组时兼容代码不介入。
- **苹果皮（AppleSkin）**：大胃袋 HUD 使用**内置**的同款贴图，不装苹果皮也能看到金色描边；同时与"经典状态条"（Classic Bar）的 HUD 重绘兼容。

### 赋能组新增机制（1.3.8）

- **"严格只剑"有两处落地**：`SimpleEnchantment` 的"仅剑"清单负责铁砧、`/enchant`、创造书、随机战利品与村民附魔书；附魔台的候选另由 `EnchantmentHelperMixin` 过滤（原版附魔台只按 `EnchantmentTarget` / `EnchantmentCategory` 判定，光覆写 `isAcceptableItem` / `canEnchant` 挡不住斧头）。因此斧、三叉戟、矛都附不到藏剑与电刀。
- **电刀的口径定稿**：基数 = 主命中的**攻击侧金额**（与 1.3.7 里魔剑"额外伤害只吃一层"的口径一致），因此不会出现"基数已含魔剑再乘一次"的平方；弹射段与主命中作用在**不同目标**上，同一个目标不会被电刀算两次；每段各结算一次魔剑/嗜血是刻意保留的。
- **藏剑与电刀的叠加**：藏剑加成在攻击侧生效，所以电刀的弹射基数会**连同藏剑的蓄力加成一起**递减传导（不要把两者理解成互斥）。
- **闪身步与灵动步伐**：闪身步是**确定性免疫**（不吃 `no_dodge` 豁免、不消耗随机数），灵动步伐仍是**按几率**闪避并受 `no_dodge` 标签约束；两者写在同一个受伤侧 HEAD 注入里，闪身步先判。

## 计数与口径声明

- 附魔 **32** 个；配置 **99** 项，按功能域分成 **11 组**：核心机制 8 / 原版改动 5 / 武器附魔 12 / 弓弩与箭矢 12 / 生存与治疗 12 / 战斗状态 7 / 防御与耐久 7 / 食物与采集 7 / 粒子与特效 12 / 箭矢特性 8 / 赋能 9。
- 网络协议版本 **9**；中英语言键各 **356** 条且一一对应。
- 两树（Fabric `1.3.8` / Forge `1.3.8-forge`）**功能一致**，只是 Yarn / Mojang 映射名不同，Mixin 类名两树相同（Fabric 30 个、Forge 29 个）。客户端实现差异只有两处：**大胃袋 HUD**（Fabric `InGameHudMixin` 注入原版 HUD；Forge 改用 GUI overlay `ExtraHungerOverlay`）与**配置页入口**（Fabric 走 Mod Menu 的 `EnchantmentReforgedModMenu`，Forge 走模组列表里的"配置"按钮，即 `ConfigScreenHandler.ConfigScreenFactory`）。
- 本文档以 **1.3.8 的代码**为准；改动数值后请按"注册代码 / 配置枚举 / 客户端 `EnchantmentDescriptions` / 两树 README 专题"逐字段回来核对。
