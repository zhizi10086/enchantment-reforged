package com.enchantmentreforged.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.config.EnchantmentReforgedConfig.Option;
import com.enchantmentreforged.config.EnchantmentReforgedConfig.Category;
import com.enchantmentreforged.network.ConfigSync;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * 原版命令接口：不装 Mod Menu 也能开关改动、调整数值。
 *
 * <pre>
 * /enchantmentreforged status                  查看全部配置
 * /enchantmentreforged set &lt;选项&gt; &lt;值&gt;        修改某项并立即保存
 * /enchantmentreforged reset                   恢复默认值
 * </pre>
 *
 * <p>选项名与配置文件里的字段名一致（例如 enable_strength_rework、strength_multiplier_per_level），
 * 需要 OP 权限等级 2（单人世界需开启作弊）。
 *
 * <p>客户端作用域项（箭矢粒子）不在这里提供：它是每名玩家各自的本地设置，
 * 服务端命令改不了别人的客户端，因此只在图形配置页里修改。
 */
public final class EnchantmentReforgedCommand {
	private static final String ROOT = "enchantmentreforged";
	private static final String SHORT_ROOT = "er";

	private static final SuggestionProvider<ServerCommandSource> BOOLEAN_SUGGESTIONS =
			(context, builder) -> CommandSource.suggestMatching(new String[]{"true", "false"}, builder);

	private EnchantmentReforgedCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(build(ROOT));
			// 短别名：如果已被其它模组占用就安静跳过，避免注册冲突导致启动失败
			if (dispatcher.getRoot().getChild(SHORT_ROOT) == null) {
				dispatcher.register(build(SHORT_ROOT));
			}
		});
	}

	private static LiteralArgumentBuilder<ServerCommandSource> build(String name) {
		LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal(name)
				.requires(source -> source.hasPermissionLevel(2))
				.executes(context -> status(context.getSource()));

		root.then(CommandManager.literal("status").executes(context -> status(context.getSource())));
		root.then(CommandManager.literal("reset").executes(context -> reset(context.getSource())));

		LiteralArgumentBuilder<ServerCommandSource> set = CommandManager.literal("set");
		for (Option option : Option.values()) {
			// 客户端作用域项（每名玩家自己的本地设置）不提供服务端命令
			if (option.clientOnly()) {
				continue;
			}
			switch (option.kind()) {
				case BOOLEAN -> set.then(CommandManager.literal(option.key())
						.then(CommandManager.argument("value", BoolArgumentType.bool())
								.suggests(BOOLEAN_SUGGESTIONS)
								.executes(context -> apply(context.getSource(), option,
										BoolArgumentType.getBool(context, "value") ? 1.0D : 0.0D))));
				case DOUBLE -> set.then(CommandManager.literal(option.key())
						.then(CommandManager.argument("value",
										DoubleArgumentType.doubleArg(option.min(), option.max()))
								.executes(context -> apply(context.getSource(), option,
										DoubleArgumentType.getDouble(context, "value")))));
				case INTEGER -> set.then(CommandManager.literal(option.key())
						.then(CommandManager.argument("value",
										IntegerArgumentType.integer((int) option.min(), (int) option.max()))
								.executes(context -> apply(context.getSource(), option,
										IntegerArgumentType.getInteger(context, "value")))));
			}
		}
		root.then(set);

		return root;
	}

	/** 列出全部配置 */
	private static int status(ServerCommandSource source) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		source.sendFeedback(() -> Text.translatable("commands.enchantment_reforged.status.header"), false);
		for (Category category : Category.values()) {
			source.sendFeedback(() -> Text.translatable(category.translationKey()), false);
			for (Option option : Option.values()) {
				if (option.clientOnly() || option.category() != category) {
					continue;
				}
				source.sendFeedback(() -> Text.translatable("commands.enchantment_reforged.status.line",
						Text.translatable(option.translationKey()), describe(option, config)), false);
			}
		}
		return 1;
	}

	/** 修改一项并保存 */
	private static int apply(ServerCommandSource source, Option option, double value) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		option.set(config, value);
		config.save();
		source.sendFeedback(() -> Text.translatable("commands.enchantment_reforged.set",
				Text.translatable(option.translationKey()), describe(option, config)), true);
		// 让在线客户端立刻同步到新配置
		ConfigSync.broadcast(source.getServer());
		return 1;
	}

	/** 恢复默认值 */
	private static int reset(ServerCommandSource source) {
		EnchantmentReforgedConfig.get().resetToDefaults();
		source.sendFeedback(() -> Text.translatable("commands.enchantment_reforged.reset"), true);
		ConfigSync.broadcast(source.getServer());
		return 1;
	}

	private static MutableText describe(Option option, EnchantmentReforgedConfig config) {
		double value = option.get(config);
		return switch (option.kind()) {
			case BOOLEAN -> Text.translatable(value >= 0.5D
					? "text.enchantment_reforged.value.on"
					: "text.enchantment_reforged.value.off");
			case DOUBLE -> Text.literal(trimNumber(value));
			case INTEGER, ENUM -> Text.literal(Integer.toString((int) value));
		};
	}

	/** 数值显示去掉多余的 0，例如 1.500 -> 1.5 */
	private static String trimNumber(double value) {
		String text = String.format(Locale.ROOT, "%.3f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}
}
