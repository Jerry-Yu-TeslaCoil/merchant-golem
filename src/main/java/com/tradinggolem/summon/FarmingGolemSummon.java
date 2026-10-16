package com.tradinggolem.summon;

import com.tradinggolem.block.CropChestBlock;
import com.tradinggolem.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class FarmingGolemSummon {
	private FarmingGolemSummon() {
	}

	public static void trySummon(Level level, BlockPos pumpkinPos, BlockState state, BlockState oldState, boolean movedByPiston) {
		if (movedByPiston || oldState.is(state.getBlock()) || level.isClientSide()) {
			return;
		}
		if (!(state.getBlock() instanceof CarvedPumpkinBlock)) {
			return;
		}
		BlockPos basePos = pumpkinPos.below();
		if (!level.getBlockState(basePos).is(Blocks.HAY_BLOCK)) {
			return;
		}
		if (!(level instanceof ServerLevel server)) {
			return;
		}

		Direction facing = state.getValue(CarvedPumpkinBlock.FACING);
		server.removeBlock(pumpkinPos, false);
		BlockState chest = ModBlocks.CROP_CHEST.defaultBlockState().setValue(CropChestBlock.FACING, facing);
		server.setBlock(basePos, chest, Block.UPDATE_ALL);
		server.playSound(null, basePos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 0.8F);
		server.sendParticles(ParticleTypes.HAPPY_VILLAGER, basePos.getX() + 0.5, basePos.getY() + 0.8, basePos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
	}
}
