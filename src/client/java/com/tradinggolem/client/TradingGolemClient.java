package com.tradinggolem.client;

import com.tradinggolem.client.model.ModModelLayers;
import com.tradinggolem.client.renderer.TradingGolemRenderer;
import com.tradinggolem.entity.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class TradingGolemClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModModelLayers.register();
		EntityRenderers.register(ModEntityTypes.TRADING_GOLEM, TradingGolemRenderer::new);
	}
}
