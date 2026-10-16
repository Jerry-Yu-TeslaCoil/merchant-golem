package com.tradinggolem.entity;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class FarmCrops {
	private FarmCrops() {
	}

	public static boolean isSeed(Item item) {
		return cropFor(item) != null;
	}

	public static Block cropFor(Item item) {
		if (item == Items.WHEAT_SEEDS) {
			return Blocks.WHEAT;
		}
		if (item == Items.BEETROOT_SEEDS) {
			return Blocks.BEETROOTS;
		}
		if (item == Items.CARROT) {
			return Blocks.CARROTS;
		}
		if (item == Items.POTATO) {
			return Blocks.POTATOES;
		}
		if (item == Items.MELON_SEEDS) {
			return Blocks.MELON_STEM;
		}
		if (item == Items.PUMPKIN_SEEDS) {
			return Blocks.PUMPKIN_STEM;
		}
		if (item == Items.TORCHFLOWER_SEEDS) {
			return Blocks.TORCHFLOWER_CROP;
		}
		if (item == Items.PITCHER_POD) {
			return Blocks.PITCHER_CROP;
		}
		return null;
	}

	public static boolean isMature(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop && crop.isMaxAge(state)) {
			return state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES) || state.is(Blocks.BEETROOTS);
		}
		if (state.is(Blocks.PITCHER_PLANT) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
			return false;
		}
		return state.is(Blocks.MELON)
				|| state.is(Blocks.PUMPKIN)
				|| state.is(Blocks.TORCHFLOWER)
				|| state.is(Blocks.PITCHER_PLANT);
	}

	public static boolean isTillable(BlockState state) {
		return state.is(Blocks.DIRT)
				|| state.is(Blocks.GRASS_BLOCK)
				|| state.is(Blocks.DIRT_PATH)
				|| state.is(Blocks.COARSE_DIRT)
				|| state.is(Blocks.ROOTED_DIRT);
	}
}
