package com.tradinggolem.client.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

public class TradingChestRenderState extends BlockEntityRenderState {
	public Direction facing = Direction.NORTH;
	public float open;
}
