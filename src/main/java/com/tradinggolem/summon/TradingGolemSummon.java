package com.tradinggolem.summon;

import com.tradinggolem.entity.ModEntityTypes;
import com.tradinggolem.entity.TradingGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class TradingGolemSummon {
	private TradingGolemSummon() {
	}

	public static void trySummon(Level level, BlockPos pumpkinPos, BlockState state, BlockState oldState, boolean movedByPiston) {
		if (movedByPiston || oldState.is(state.getBlock()) || level.isClientSide()) {
			return;
		}
		if (!(state.getBlock() instanceof CarvedPumpkinBlock)) {
			return;
		}
		BlockPos basePos = pumpkinPos.below();
		if (!level.getBlockState(basePos).is(Blocks.EMERALD_BLOCK)) {
			return;
		}
		if (!(level instanceof ServerLevel server)) {
			return;
		}

		TradingGolem golem = ModEntityTypes.TRADING_GOLEM.create(server, EntitySpawnReason.MOB_SUMMONED);
		if (golem == null) {
			return;
		}

		Direction facing = state.getValue(CarvedPumpkinBlock.FACING);
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
		server.setBlock(basePos, chest, Block.UPDATE_ALL);
		server.removeBlock(pumpkinPos, false);

		BlockPos stand = findStand(server, pumpkinPos, basePos);
		golem.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, facing.toYRot(), 0.0F);
		golem.setYHeadRot(facing.toYRot());
		golem.setPersistenceRequired();
		golem.bindChest(basePos);
		server.addFreshEntity(golem);

		server.playSound(null, stand, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 1.0F, 1.15F);
		server.sendParticles(ParticleTypes.HAPPY_VILLAGER, stand.getX() + 0.5, stand.getY() + 0.6, stand.getZ() + 0.5, 16, 0.35, 0.4, 0.35, 0.02);
	}

	private static BlockPos findStand(ServerLevel level, BlockPos preferred, BlockPos chestPos) {
		if (canStand(level, preferred)) {
			return preferred;
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos beside = chestPos.relative(direction);
			if (canStand(level, beside)) {
				return beside;
			}
		}
		return preferred;
	}

	private static boolean canStand(Level level, BlockPos feet) {
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
	}
}
