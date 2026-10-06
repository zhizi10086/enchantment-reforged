package com.enchantmentreforged.client;

import com.enchantmentreforged.EnchantmentReforged;
import com.enchantmentreforged.config.ArrowParticleStyle;
import com.enchantmentreforged.config.DodgeSoundStyle;
import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.config.EnchantmentReforgedConfig.Category;
import com.enchantmentreforged.config.EnchantmentReforgedConfig.Option;
import com.enchantmentreforged.particle.MeleeParticleLevels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 配置界面（Mod Menu → 配置）。
 *
 * <p>按功能域分成 7 个分类：顶部分类标签切换（一行放不下时自动分两行），
 * 下面一屏显示该分类的全部条目（不分页）。
 * 屏幕特别矮时（18px 行高都放不下）才启用滚轮滚动，滚动时只创建可见行的控件，
 * 避免"看不到的控件被点到"。行内控件全部直接注册到 Screen，点击派发可靠。
 */
public class EnchantmentReforgedConfigScreen extends Screen {
	/** 标题下方标签栏的起始高度与行距（标签栏 1 行 48、2 行 70） */
	private static final int TAB_TOP = 24;
	private static final int TAB_HEIGHT = 20;
	private static final int TAB_ROW_GAP = 2;
	private static final int FOOTER_HEIGHT = 34;
	private static final int MAX_ROW_WIDTH = 330;
	private static final int FIELD_WIDTH = 84;
	private static final int RESET_WIDTH = 50;
	private static final int GAP = 4;
	private static final int TOOLTIP_WIDTH = 200;
	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();

	private Category category = Category.CORE;
	private Option[] currentOptions = new Option[0];
	private int firstRow;
	private int visibleRows = 1;
	private int rowHeight = 22;
	private int headerHeight = 48;
	/** 滑块被拖动过（还没写盘）：关闭界面时兜底保存一次 */
	private boolean sliderDirty;
	public EnchantmentReforgedConfigScreen(Screen parent) {
		super(Component.translatable("text.enchantment_reforged.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.rows.clear();
		this.currentOptions = Arrays.stream(Option.values())
				.filter(option -> option.category() == this.category)
				.toArray(Option[]::new);

		// 标签栏先算：一行放不下就分两行，并据此决定页头高度（行区从页头下方开始）
		Category[] categories = Category.values();
		int tabGap = 4;
		int[] tabWidths = new int[categories.length];
		int oneRowWidth = 0;
		for (int i = 0; i < categories.length; i++) {
			tabWidths[i] = Math.max(40, this.font.width(
					Component.translatable(categories[i].translationKey())) + 8);
			oneRowWidth += tabWidths[i] + (i == 0 ? 0 : tabGap);
		}
		int tabRows = oneRowWidth > this.width - 8 ? 2 : 1;
		this.headerHeight = TAB_TOP + tabRows * TAB_HEIGHT + (tabRows - 1) * TAB_ROW_GAP + 4;

		// 行高按屏幕高度自适应，保证最常见的 240 逻辑高度下 9 项也能一屏放下
		this.rowHeight = this.height >= 260 ? 22 : (this.height >= 230 ? 20 : 18);
		int available = Math.max(this.rowHeight, this.height - this.headerHeight - FOOTER_HEIGHT);
		this.visibleRows = Math.max(1, available / this.rowHeight);
		int maxFirst = Math.max(0, this.currentOptions.length - this.visibleRows);
		this.firstRow = Math.max(0, Math.min(this.firstRow, maxFirst));

		int controlHeight = Math.min(20, this.rowHeight);
		int rowWidth = Math.min(MAX_ROW_WIDTH, Math.max(200, this.width - 40));
		int left = this.width / 2 - rowWidth / 2;
		int resetX = left + rowWidth - RESET_WIDTH;
		int controlX = resetX - GAP - FIELD_WIDTH;

		for (int i = 0; i < this.visibleRows && this.firstRow + i < this.currentOptions.length; i++) {
			this.rows.add(new Row(this.currentOptions[this.firstRow + i], left, controlX, resetX,
					this.headerHeight + i * this.rowHeight, this.rowHeight, controlHeight));
		}

		// 顶部分类标签（当前分类置灰表示选中；两行时按 4+3 平分）
		int perRow = tabRows == 1 ? categories.length : (categories.length + 1) / 2;
		int index = 0;
		for (int row = 0; row < tabRows; row++) {
			int count = Math.min(perRow, categories.length - index);
			int tabsWidth = 0;
			for (int i = 0; i < count; i++) {
				tabsWidth += tabWidths[index + i] + (i == 0 ? 0 : tabGap);
			}
			int tabX = this.width / 2 - tabsWidth / 2;
			int tabY = TAB_TOP + row * (TAB_HEIGHT + TAB_ROW_GAP);
			for (int i = 0; i < count; i++) {
				Category target = categories[index + i];
				Button tab = Button.builder(Component.translatable(target.translationKey()), button -> {
					this.category = target;
					this.firstRow = 0;
					this.rebuildWidgets();
				}).bounds(tabX, tabY, tabWidths[index + i], TAB_HEIGHT).build();
				tab.active = target != this.category;
				this.addRenderableWidget(tab);
				tabX += tabWidths[index + i] + tabGap;
			}
			index += count;
		}

		// 底部：重置全部 / 完成
		int buttonWidth = Math.max(70, (this.width - 20) / 2);
		int buttonX = this.width / 2 - buttonWidth - 3;
		int buttonY = this.height - 28;
		this.addRenderableWidget(Button.builder(Component.translatable("text.enchantment_reforged.config.reset_all"),
				button -> {
					EnchantmentReforgedConfig.get().resetToDefaults();
					publishIfNeeded(Option.ARROW_PARTICLE);
					this.rebuildWidgets();
				}).bounds(buttonX, buttonY, buttonWidth, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds(buttonX + buttonWidth + 6, buttonY, buttonWidth, 20).build());
	}

	/** 只有"一屏放不下"时才滚动（正常情况不需要） */
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int maxFirst = Math.max(0, this.currentOptions.length - this.visibleRows);
		if (maxFirst > 0 && amount != 0.0D) {
			int next = Math.max(0, Math.min(maxFirst, this.firstRow - (int) Math.signum(amount)));
			if (next != this.firstRow) {
				this.firstRow = next;
				this.rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);

		for (Row row : this.rows) {
			int textY = row.y + (row.height - 8) / 2;
			Component label = Component.translatable(row.option.translationKey());
			context.drawString(this.font, label, row.left, textY, 0xFFFFFF);
			// 枚举型选项（当前只有"箭矢粒子"）在选项名后面用灰字补一句当前档位名
			String valueName = optionValueName(row.option);
			if (valueName != null) {
				int nameX = row.left + this.font.width(label) + 6;
				int nameWidth = row.controlX - GAP - nameX;
				if (nameWidth > 12) {
					context.drawString(this.font,
							Component.literal(this.font.plainSubstrByWidth(valueName, nameWidth)),
							nameX, textY, 0xAAAAAA);
				}
			}
			row.syncState();
		}

		super.render(context, mouseX, mouseY, delta);

		for (Row row : this.rows) {
			if (row.isLabelHovered(mouseX, mouseY)) {
				List<FormattedCharSequence> tooltip = this.font.split(
						buildTooltip(row.option), TOOLTIP_WIDTH);
				context.renderTooltip(this.font, tooltip, mouseX, mouseY);
				break;
			}
		}

		context.drawCenteredString(this.font,
				Component.translatable("text.enchantment_reforged.config.title_with_category",
						Component.translatable(this.category.translationKey())),
				this.width / 2, 10, 0xFFFFFF);

		if (this.currentOptions.length > this.visibleRows) {
			context.drawCenteredString(this.font,
					Component.translatable("text.enchantment_reforged.config.scroll_hint",
							this.firstRow + 1, Math.max(1, this.currentOptions.length - this.visibleRows + 1)),
					this.width / 2, this.height - 38, 0x808080);
		}
	}

	@Override
	public void onClose() {
		// 兜底：滑块是"松手才写盘"，若玩家拖到一半直接关界面（或中途切了分类），这里补一次保存
		if (this.sliderDirty) {
			EnchantmentReforgedConfig.get().save();
		}
		if (this.minecraft != null) {
			this.minecraft.setScreen(this.parent);
		}
	}


	/** 开关按钮上的文字 */
	private static Component booleanText(Option option) {
		boolean enabled = option.getBoolean(EnchantmentReforgedConfig.get());
		return Component.translatable(enabled
				? "text.enchantment_reforged.value.on"
				: "text.enchantment_reforged.value.off");
	}

	/** 数值显示：整数直接显示，小数去掉多余的 0 */
	private static String formatValue(Option option, double value) {
		if (option.kind() == Option.Kind.INTEGER || option.kind() == Option.Kind.ENUM) {
			return Integer.toString((int) value);
		}
		String text = String.format(Locale.ROOT, "%.3f", value);
		while (text.endsWith("0")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.endsWith(".")) {
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}

	/** 是否为"档位项"（界面用滑块，取值 0 ~ max） */
	private static boolean isEnumOption(Option option) {
		return option.kind() == Option.Kind.ENUM;
	}

	/** 档位项的最大档位号 */
	private static int enumMax(Option option) {
		return (int) Math.round(option.max());
	}

	/** 档位对应的名字语言键：粒子、效果强度、闪避音效各有一张表 */
	private static String enumNameKey(Option option, int index) {
		if (option == Option.MELEE_PARTICLE_EFFECT) {
			return MeleeParticleLevels.nameKey(index);
		}
		if (option == Option.NIMBLE_STEPS_SOUND) {
			return DodgeSoundStyle.nameKey(index);
		}
		return ArrowParticleStyle.nameKey(index);
	}

	/** 档位文本（形如 {@code 5 电火花}；强度与音效只显示名字） */
	private static Component enumLabel(Option option, int index) {
		Component name = Component.translatable(enumNameKey(option, index));
		if (option == Option.MELEE_PARTICLE_EFFECT || option == Option.NIMBLE_STEPS_SOUND) {
			return name;
		}
		return Component.literal(index + " ").append(name);
	}

	/** 档位项的当前档位文本；非档位项返回 null */
	private static String optionValueName(Option option) {
		if (!isEnumOption(option)) {
			return null;
		}
		return enumLabel(option, (int) option.get(EnchantmentReforgedConfig.get())).getString();
	}

	/** 悬停说明：选项说明本身；档位项再逐行列出全部档位名，当前档位带"（当前）" */
	private static Component buildTooltip(Option option) {
		MutableComponent tooltip = Component.translatable(option.tooltipKey());
		if (!isEnumOption(option)) {
			return tooltip;
		}
		int current = (int) option.get(EnchantmentReforgedConfig.get());
		int max = enumMax(option);
		for (int index = 0; index <= max; index++) {
			MutableComponent line = Component.literal(index + " ")
					.append(Component.translatable(enumNameKey(option, index)));
			if (index == current) {
				line.append(Component.translatable("text.enchantment_reforged.config.current"));
			}
			tooltip.append("\n").append(line);
		}
		return tooltip;
	}

	/** 客户端作用域项改动后立刻上报，服务端据此给"我们射出的箭/打出的近战"用上档位 */
	private static void publishIfNeeded(Option option) {
		if (!option.clientOnly()) {
			return;
		}
		if (option == Option.ARROW_PARTICLE
				|| option == Option.ARROW_PARTICLE_LONG_DISTANCE
				|| option == Option.ARROW_PARTICLE_DISTANCE
				|| option == Option.MELEE_PARTICLE
				|| option == Option.MELEE_SWEEP_PARTICLE
				|| option == Option.MELEE_PARTICLE_EFFECT
				|| option == Option.SURPRISE_PARTICLE
				|| option == Option.DEATH_PARTICLE
				|| option == Option.NIMBLE_STEPS_SOUND) {
			EnchantmentReforgedClient.publishParticlePreference();
		}
	}

	/** 输入框允许的字符：数字、负号、小数点（允许中间态，便于连续输入） */
	private static boolean isNumericInput(String text) {
		if (text.isEmpty() || text.equals("-") || text.equals(".") || text.equals("-.")) {
			return true;
		}
		return text.matches("-?\\d*\\.?\\d*");
	}

	/** 一行配置：名称标签 +（输入框或开关）+ 单项重置按钮 */
	private class Row {
		private final Option option;
		private final int left;
		private final int controlX;
		private final int y;
		private final int height;
		private final Button toggleButton;
		private final EditBox textField;
		private final EnumSlider slider;
		private final Button resetButton;
		/** 点了重置后即使输入框还在编辑，也要立刻把默认值显示出来 */
		private boolean forceSync;

		Row(Option option, int left, int controlX, int resetX, int y, int height, int controlHeight) {
			this.option = option;
			this.left = left;
			this.controlX = controlX;
			this.y = y;
			this.height = height;

			if (option.kind() == Option.Kind.BOOLEAN) {
				this.textField = null;
				this.slider = null;
				this.toggleButton = Button.builder(booleanText(option), button -> {
					EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
					option.set(config, option.getBoolean(config) ? 0.0D : 1.0D);
					config.save();
					publishIfNeeded(option);
					button.setMessage(booleanText(option));
				}).bounds(controlX, y, FIELD_WIDTH, controlHeight).build();
				addRenderableWidget(this.toggleButton);
			} else if (option.kind() == Option.Kind.ENUM) {
				// 档位项：滑块。拖动时只改内存配置，松手才写盘并上报，避免拖动过程反复 IO
				this.toggleButton = null;
				this.textField = null;
				this.slider = new EnumSlider(option, controlX, y, FIELD_WIDTH, controlHeight);
				addRenderableWidget(this.slider);
			} else {
				this.toggleButton = null;
				this.slider = null;
				this.textField = new EditBox(EnchantmentReforgedConfigScreen.this.font,
						controlX, y, FIELD_WIDTH, controlHeight, Component.translatable(option.translationKey()));
				this.textField.setFilter(EnchantmentReforgedConfigScreen::isNumericInput);
				this.textField.setMaxLength(10);
				this.textField.setValue(formatValue(option, option.get(EnchantmentReforgedConfig.get())));
				this.textField.setResponder(text -> {
					if (!isNumericInput(text) || text.isEmpty() || text.equals("-") || text.endsWith(".")) {
						return;
					}
					try {
						EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
						option.set(config, Double.parseDouble(text));
						config.save();
						publishIfNeeded(option);
					} catch (NumberFormatException ignored) {
						// 解析失败时保持上一个合法值
					}
				});
				addRenderableWidget(this.textField);
			}

			this.resetButton = Button.builder(Component.translatable("text.enchantment_reforged.config.reset_one"),
					button -> {
						EnchantmentReforgedConfig.resetOption(this.option);
						publishIfNeeded(this.option);
						EnchantmentReforged.LOGGER.info("[Enchantment Reforged] 单项重置：{} -> {}",
								this.option.key(), this.option.get(EnchantmentReforgedConfig.get()));
						this.forceSync = true;
						this.syncState();
					}).bounds(resetX, y, RESET_WIDTH, controlHeight).build();
			addRenderableWidget(this.resetButton);
		}

		/** 每帧同步：值与默认值相同时禁用本行重置按钮；输入框未编辑时跟随配置值 */
		private void syncState() {
			this.resetButton.active = !this.option.isDefault(EnchantmentReforgedConfig.get());

			if (this.textField != null && (this.forceSync || !this.textField.isFocused())) {
				this.forceSync = false;
				this.textField.setValue(formatValue(this.option, this.option.get(EnchantmentReforgedConfig.get())));
			}
			// 滑块：配置值被"单项重置/重置全部"改动后，把滑块拖回对应档位
			if (this.slider != null) {
				int configIndex = (int) this.option.get(EnchantmentReforgedConfig.get());
				if (this.forceSync || this.slider.index() != configIndex) {
					this.forceSync = false;
					this.slider.setIndex(configIndex);
				}
			}
		}

		/** 鼠标是否停在选项名上（用来显示详细说明） */
		private boolean isLabelHovered(int mouseX, int mouseY) {
			return mouseY >= this.y && mouseY < this.y + this.height
					&& mouseX >= this.left && mouseX < this.controlX - GAP;
		}
	}

	/**
	 * 档位项用的滑块。
	 *
	 * <p>{@code AbstractSliderButton} 自身的 {@code setValue} 是私有的，所以这里补一个
	 * {@link #setIndex(int)} 供"单项重置 / 重置全部"之后把滑块拖回正确档位。
	 * 拖动过程中只改内存配置，松手才写盘与上报（避免一路拖动反复写文件、发包）。
	 */
	private class EnumSlider extends AbstractSliderButton {
		private final Option option;
		private final int max;

		EnumSlider(Option option, int x, int y, int width, int height) {
			super(x, y, width, height, enumLabel(option, (int) option.get(EnchantmentReforgedConfig.get())),
					indexToValue(option, (int) option.get(EnchantmentReforgedConfig.get())));
			this.option = option;
			this.max = enumMax(option);
		}

		/** 当前档位号 */
		int index() {
			return (int) Math.round(this.value * this.max);
		}

		/** 把滑块拖到指定档位（不会写盘，由调用方的保存/上报逻辑负责） */
		void setIndex(int index) {
			this.value = indexToValue(this.option, index);
			this.applyValue();
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(enumLabel(this.option, this.index()));
		}

		@Override
		protected void applyValue() {
			// 只写内存：拖动时不需要每帧写盘
			this.option.set(EnchantmentReforgedConfig.get(), this.index());
			EnchantmentReforgedConfigScreen.this.sliderDirty = true;
		}

		@Override
		public void onRelease(double mouseX, double mouseY) {
			super.onRelease(mouseX, mouseY);
			EnchantmentReforgedConfig.get().save();
			publishIfNeeded(this.option);
		}

		/** 档位号 → 滑块位置（0~1） */
		private static double indexToValue(Option option, int index) {
			int max = enumMax(option);
			if (max <= 0) {
				return 0.0D;
			}
			return Math.max(0, Math.min(max, index)) / (double) max;
		}
	}
}
