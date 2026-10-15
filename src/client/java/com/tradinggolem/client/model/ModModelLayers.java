package com.tradinggolem.client.model;

import com.tradinggolem.TradingGolemMod;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

public final class ModModelLayers {
	public static final ModelLayerLocation TRADING_GOLEM = new ModelLayerLocation(TradingGolemMod.id("trading_golem"), "main");

	private ModModelLayers() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(TRADING_GOLEM, TradingGolemModel::createBodyLayer);
	}
}
