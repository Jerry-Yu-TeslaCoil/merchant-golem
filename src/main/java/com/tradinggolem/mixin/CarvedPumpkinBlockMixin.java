package com.tradinggolem.mixin;

import com.tradinggolem.summon.FarmingGolemSummon;
import com.tradinggolem.summon.TradingGolemSummon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarvedPumpkinBlock.class)
public class CarvedPumpkinBlockMixin {
	@Inject(method = "onPlace", at = @At("TAIL"))
	private void tradingGolem$onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, CallbackInfo ci) {
		TradingGolemSummon.trySummon(level, pos, state, oldState, movedByPiston);
		FarmingGolemSummon.trySummon(level, pos, state, oldState, movedByPiston);
	}
}
