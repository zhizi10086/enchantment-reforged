package com.enchantmentreforged.client;

import com.enchantmentreforged.EnchantmentReforged;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.fml.ModList;

/**
 * 大胃袋的 HUD：在饥饿条正上方单起一行显示"多出来的饥饿值"。
 *
 * <p>这里刻意不再用 Mixin 注入 {@code Gui#renderPlayerHealth}：那个方法末尾会打开
 * depthMask / enableDepthTest 并做全屏受伤叠加，而 Forge 又把饥饿、气泡拆成了
 * 独立的 overlay 层（苹果皮也在其上绘制），画在方法内部层级不可控。
 * 改成 Forge 官方 GUI overlay、并注册在 {@code VanillaGuiOverlay.AIR_LEVEL} 之上，
 * 就能稳定地画在原版 HUD 之上、不会被覆盖，也不受原版渲染状态影响。
 *
 * <p>规则与苹果皮（AppleSkin）一致：
 * <ul>
 *     <li>额外饥饿值用原版同款饥饿图标（满 / 半 / 空，附带"饥饿效果"的绿色变体）；</li>
 *     <li>额外饱和度在同一行叠加金色描边，覆盖率 4 档（0 / 9 / 18 / 27 四张图，与苹果皮同 UV）；</li>
 *     <li>描边贴图内置（{@code textures/gui/appleskin_icons.png}），所以不装苹果皮也能看到金框。</li>
 * </ul>
 *
 * <p>位置取"饥饿条上一行"（右半边，不会和左半边的盔甲行冲突）；如果原版此时会画水泡行，
 * 就再让开一行，避免互相压住。骑乘生物（原版不画饥饿条）或额外饥饿值为 0 时不画。
 *
 * <p>创造 / 旁观模式不画：这两种模式下饥饿值不会消耗，"多出来的饥饿"没有意义。
 * 判定用客户端权威的 {@code MultiPlayerGameMode#getPlayerMode()}，不能用
 * {@code player.isCreative()}——客户端玩家的那个方法并不可靠。
 *
 * <p>与"经典状态条"（Classic Bar）的兼容：那个模组会把原版的饥饿/空气等 overlay 取消隐藏，
 * 改成自己画的条形，并借用 Forge 的堆叠偏移（每条绘制在 {@code y = screenHeight - ForgeGui.rightHeight}）。
 * 所以这里在检测到 classicbar 时改用同一份偏移，把我们的行排在它所有条的正上方；
 * 注册锚点也放在 {@code ITEM_NAME} 之上，保证读取偏移时经典状态条已经完成累加。
 */
public final class ExtraHungerOverlay implements IGuiOverlay {
	public static final ExtraHungerOverlay INSTANCE = new ExtraHungerOverlay();

	/** overlay 注册用的路径名（完整 id 为 enchantment_reforged:extra_hunger_row） */
	public static final String ID = "extra_hunger_row";

	/** 原版 GUI 图集（饥饿图标） */
	private static final ResourceLocation VANILLA_ICONS = new ResourceLocation("textures/gui/icons.png");
	/** 内置的苹果皮图集（饱和度金框） */
	private static final ResourceLocation APPLESKIN_ICONS =
			EnchantmentReforged.id("textures/gui/appleskin_icons.png");

	/** 自检日志限流：只在饥饿值/饱和度变化时打印一次 */
	private static int lastLoggedFoodLevel = Integer.MIN_VALUE;
	private static int lastLoggedSaturationKey = Integer.MIN_VALUE;
	/** 经典状态条是否已加载（客户端渲染期间 mod 列表是稳定的，懒加载缓存一次） */
	private static Boolean classicBarLoaded;

	private ExtraHungerOverlay() {
	}

	@Override
	public void render(ForgeGui gui, GuiGraphics context, float partialTick, int screenWidth, int screenHeight) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || enchantmentReforged$hiddenGameMode(client)
				|| enchantmentReforged$ridesLivingMount(player)) {
			return;
		}
		FoodData hunger = player.getFoodData();
		int foodLevel = hunger.getFoodLevel();
		int extraFood = foodLevel - 20;
		float saturation = hunger.getSaturationLevel();
		int saturationKey = Math.round(saturation * 10.0F);

		// 经典状态条：它把原版饥饿条藏起来、自己从屏幕底部往上排条形（y = 高度 - rightHeight），
		// 所以这里读同一份偏移，把我们的行排在它所有条的正上方。
		// 纯原版：饥饿条在 高度-39，额外行画在它上面一行；若原版会画水泡行，再让开一行。
		int rowY;
		if (enchantmentReforged$classicBarLoaded()) {
			rowY = screenHeight - gui.rightHeight - 10;
		} else {
			rowY = screenHeight - 39 - 10;
			if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply()) {
				rowY -= 10;
			}
		}

		if (foodLevel != lastLoggedFoodLevel || saturationKey != lastLoggedSaturationKey) {
			lastLoggedFoodLevel = foodLevel;
			lastLoggedSaturationKey = saturationKey;
			EnchantmentReforged.LOGGER.info(
					"[ER-HUD] foodLevel={}, saturation={}, extraFood={}, rowY={}, screen={}x{}, classicBar={}",
					foodLevel, saturation, extraFood, rowY, screenWidth, screenHeight,
					enchantmentReforged$classicBarLoaded());
		}
		if (extraFood <= 0) {
			return;
		}
		float extraSaturation = Math.max(0.0F, saturation - 20.0F);

		int right = screenWidth / 2 + 91;

		// 排除其它 HUD 留下的颜色状态影响（结束前再还原一次）
		context.setColor(1.0F, 1.0F, 1.0F, 1.0F);

		boolean hungerEffect = player.hasEffect(MobEffects.HUNGER);
		int baseU = hungerEffect ? 52 : 16;
		int emptyU = hungerEffect ? 133 : 16;
		int fullU = baseU + 36;
		int halfU = baseU + 45;

		for (int i = 0; i < 10; i++) {
			int x = right - i * 8 - 9;
			int remaining = extraFood - i * 2;
			context.blit(VANILLA_ICONS, x, rowY, emptyU, 27, 9, 9);
			if (remaining >= 2) {
				context.blit(VANILLA_ICONS, x, rowY, fullU, 27, 9, 9);
			} else if (remaining == 1) {
				context.blit(VANILLA_ICONS, x, rowY, halfU, 27, 9, 9);
			}
			// 额外饱和度的金框：4 档覆盖率，判定与苹果皮完全相同
			float effective = extraSaturation / 2.0F - i;
			if (effective > 0.0F) {
				int u = effective >= 1.0F ? 27 : (effective > 0.5F ? 18 : (effective > 0.25F ? 9 : 0));
				context.blit(APPLESKIN_ICONS, x, rowY, u, 0, 9, 9);
			}
		}
		context.setColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	/** 创造 / 旁观模式不显示这一行（用客户端权威的游戏模式判定） */
	private static boolean enchantmentReforged$hiddenGameMode(Minecraft client) {
		if (client.gameMode == null) {
			return false;
		}
		GameType mode = client.gameMode.getPlayerMode();
		return mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
	}

	/** 经典状态条（Classic Bar）是否已加载 */
	private static boolean enchantmentReforged$classicBarLoaded() {
		if (classicBarLoaded == null) {
			classicBarLoaded = ModList.get().isLoaded("classicbar");
		}
		return classicBarLoaded;
	}

	/** 原版"骑乘生物时不画饥饿条"的同款判定（骑船、矿车等不影响） */
	private static boolean enchantmentReforged$ridesLivingMount(Player player) {
		if (!(player.getVehicle() instanceof LivingEntity mount)) {
			return false;
		}
		if (mount.isRemoved() || mount.isDeadOrDying()) {
			return false;
		}
		return Math.min(30, (int) (mount.getMaxHealth() + 0.5F) / 2) > 0;
	}
}
