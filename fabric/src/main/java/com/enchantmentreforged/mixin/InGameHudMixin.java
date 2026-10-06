package com.enchantmentreforged.mixin;

import com.enchantmentreforged.EnchantmentReforged;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 大胃袋的 HUD：在饥饿条正上方单起一行显示"多出来的饥饿值"。
 *
 * <p>规则与苹果皮（AppleSkin）一致：
 * <ul>
 *     <li>额外饥饿值用原版同款饥饿图标（满 / 半 / 空，附带"饥饿效果"的绿色变体）；</li>
 *     <li>额外饱和度在同一行叠加金色描边，覆盖率 4 档（0 / 9 / 18 / 27 四张图，与苹果皮同 UV）；</li>
 *     <li>描边贴图内置（{@code textures/gui/appleskin_icons.png}，与原版苹果皮逐字节一致），
 *         所以不装苹果皮也能看到金框。</li>
 * </ul>
 *
 * <p>位置取"饥饿条上一行"（右半边，不会和左半边的盔甲行冲突）；如果原版此时会画水泡行，
 * 就再让开一行，避免互相压住。骑乘生物（原版不画饥饿条）或额外饥饿值为 0 时不画。
 */
@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	/** 原版 GUI 图集（饥饿图标） */
	private static final Identifier VANILLA_ICONS = new Identifier("textures/gui/icons.png");
	/** 内置的苹果皮图集（饱和度金框） */
	private static final Identifier APPLESKIN_ICONS =
			EnchantmentReforged.id("textures/gui/appleskin_icons.png");

	@Inject(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("TAIL"))
	private void enchantmentReforged$renderExtraHungerRow(DrawContext context, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;
		if (player == null || enchantmentReforged$ridesLivingMount(player)) {
			return;
		}
		HungerManager hunger = player.getHungerManager();
		int extraFood = hunger.getFoodLevel() - 20;
		if (extraFood <= 0) {
			return;
		}
		float extraSaturation = Math.max(0.0F, hunger.getSaturationLevel() - 20.0F);

		int right = client.getWindow().getScaledWidth() / 2 + 91;
		// 原版饥饿条所在行 = scaledHeight - 39；额外行画在它上面一行
		int rowY = client.getWindow().getScaledHeight() - 39 - 10;
		// 原版的水泡行也在这条带上，此时再让开一行
		if (player.isSubmergedIn(FluidTags.WATER) || player.getAir() < player.getMaxAir()) {
			rowY -= 10;
		}

		boolean hungerEffect = player.hasStatusEffect(StatusEffects.HUNGER);
		int baseU = hungerEffect ? 52 : 16;
		int emptyU = hungerEffect ? 133 : 16;
		int fullU = baseU + 36;
		int halfU = baseU + 45;

		for (int i = 0; i < 10; i++) {
			int x = right - i * 8 - 9;
			int remaining = extraFood - i * 2;
			context.drawTexture(VANILLA_ICONS, x, rowY, emptyU, 27, 9, 9);
			if (remaining >= 2) {
				context.drawTexture(VANILLA_ICONS, x, rowY, fullU, 27, 9, 9);
			} else if (remaining == 1) {
				context.drawTexture(VANILLA_ICONS, x, rowY, halfU, 27, 9, 9);
			}
			// 额外饱和度的金框：4 档覆盖率，判定与苹果皮完全相同
			float effective = extraSaturation / 2.0F - i;
			if (effective > 0.0F) {
				int u = effective >= 1.0F ? 27 : (effective > 0.5F ? 18 : (effective > 0.25F ? 9 : 0));
				context.drawTexture(APPLESKIN_ICONS, x, rowY, u, 0, 9, 9);
			}
		}
	}

	/** 原版"骑乘生物时不画饥饿条"的同款判定（骑船、矿车等不影响） */
	private static boolean enchantmentReforged$ridesLivingMount(PlayerEntity player) {
		if (!(player.getVehicle() instanceof LivingEntity mount)) {
			return false;
		}
		if (mount.isRemoved() || mount.isDead()) {
			return false;
		}
		return Math.min(30, (int) (mount.getMaxHealth() + 0.5F) / 2) > 0;
	}
}
