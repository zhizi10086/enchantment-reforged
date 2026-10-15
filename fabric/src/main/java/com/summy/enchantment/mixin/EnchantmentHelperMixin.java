package com.summy.enchantment.mixin;

import com.summy.enchantment.enchantment.SimpleEnchantment;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * 附魔台候选过滤（1.3.8）：把"严格只剑"的附魔从非剑物品的候选里剔除。
 *
 * <p>原版附魔台只按 {@code EnchantmentTarget} 判定（{@code EnchantmentTarget.isAcceptableItem(Item)}），
 * 而 {@code Enchantment#isAcceptableItem(ItemStack)}（我们覆写"仅剑"的地方）在铁砧、命令、创造书、
 * 随机战利品里才走得到。所以要让"斧头在附魔台上也附不到藏剑/电刀"，必须在这一处再过滤一次。
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@ModifyReturnValue(method = "getPossibleEntries(ILnet/minecraft/item/ItemStack;Z)Ljava/util/List;",
			at = @At("RETURN"))
	private static List<EnchantmentLevelEntry> summyEnchantment$filterSwordOnly(
			List<EnchantmentLevelEntry> original, int level, ItemStack stack, boolean treasure) {
		if (original == null || original.isEmpty() || stack.getItem() instanceof SwordItem) {
			return original;
		}
		List<EnchantmentLevelEntry> filtered = new ArrayList<>(original.size());
		for (EnchantmentLevelEntry entry : original) {
			Enchantment enchantment = entry.enchantment;
			if (enchantment instanceof SimpleEnchantment simple && simple.isSwordOnlyEnchantment()) {
				continue;
			}
			filtered.add(entry);
		}
		return filtered.size() == original.size() ? original : filtered;
	}
}
