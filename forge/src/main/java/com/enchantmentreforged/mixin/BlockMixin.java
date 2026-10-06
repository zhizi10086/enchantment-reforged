package com.enchantmentreforged.mixin;

import com.enchantmentreforged.config.EnchantmentReforgedConfig;
import com.enchantmentreforged.registry.ModEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 自动熔炼：把挖掘掉落换成熔炉产物。
 *
 * <p>注入 {@code Block#dropStacks}（掉落生成的统一入口）：命中条件时改用
 * {@code getDroppedStacks} 取掉落 → 逐个查冶炼配方替换 → 由我们生成物品实体。
 * 只要掉落物有熔炉配方就替换（没有黑名单），潜行时完全不介入（方便正常采集原矿）。
 */
@Mixin(Block.class)
public abstract class BlockMixin {
	@Inject(
			method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private static void enchantmentReforged$autoSmelt(BlockState state, Level world, BlockPos pos,
			BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
		EnchantmentReforgedConfig config = EnchantmentReforgedConfig.get();
		if (!config.enableAutoSmelt || ModEnchantments.AUTO_SMELT == null) {
			return;
		}
		if (!(world instanceof ServerLevel serverWorld) || !(entity instanceof Player player)) {
			return;
		}
		// 潜行时自动关闭，方便正常采集
		if (player.isShiftKeyDown()) {
			return;
		}
		if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.AUTO_SMELT, tool) <= 0) {
			return;
		}
		List<ItemStack> drops = Block.getDrops(state, serverWorld, pos, blockEntity, entity, tool);
		boolean smelted = false;
		for (ItemStack drop : drops) {
			ItemStack result = smeltResult(serverWorld, drop);
			if (!result.isEmpty()) {
				smelted = true;
				ItemStack output = result.copy();
				output.setCount(result.getCount() * drop.getCount());
				Block.popResource(serverWorld, pos, output);
			} else {
				Block.popResource(serverWorld, pos, drop);
			}
		}
		// 即便是空掉落也取消原版流程（我们已经完整接管）
		ci.cancel();
	}

	/** 查询熔炉配方；没有对应配方时返回空 */
	private static ItemStack smeltResult(ServerLevel world, ItemStack input) {
		if (input.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return world.getRecipeManager()
				.getRecipeFor(RecipeType.SMELTING, new SimpleContainer(input), world)
				.filter(recipe -> recipe instanceof AbstractCookingRecipe)
				.map(recipe -> ((AbstractCookingRecipe) recipe).getResultItem(world.registryAccess()))
				.filter(output -> !output.isEmpty())
				.orElse(ItemStack.EMPTY);
	}
}
