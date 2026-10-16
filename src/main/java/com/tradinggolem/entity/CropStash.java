package com.tradinggolem.entity;

import com.tradinggolem.block.CropChestBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

/** Reads and writes the farming golem's crop chest. */
public final class CropStash {
	private CropStash() {
	}

	public static Container get(Level level, BlockPos pos) {
		if (!isChest(level, pos)) {
			return null;
		}
		if (level.getBlockEntity(pos) instanceof Container container) {
			return container;
		}
		return null;
	}

	public static boolean isChest(Level level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() instanceof CropChestBlock;
	}
}
