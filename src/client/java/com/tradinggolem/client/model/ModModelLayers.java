package com.tradinggolem.client.model;

import com.tradinggolem.TradingGolemMod;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

public final class ModModelLayers {
	public static final ModelLayerLocation TRADING_GOLEM = new ModelLayerLocation(TradingGolemMod.id("trading_golem"), "main");
	public static final ModelLayerLocation TRADING_CHEST = new ModelLayerLocation(TradingGolemMod.id("trading_chest"), "main");

	private ModModelLayers() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(TRADING_GOLEM, TradingGolemModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(TRADING_CHEST, TradingChestModel::createBodyLayer);
	}
}
