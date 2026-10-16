package com.tradinggolem.client;

import com.tradinggolem.block.ModBlocks;
import com.tradinggolem.client.model.ModModelLayers;
import com.tradinggolem.client.renderer.CropChestRenderer;
import com.tradinggolem.client.renderer.FarmingGolemRenderer;
import com.tradinggolem.client.renderer.TradingChestRenderer;
import com.tradinggolem.client.renderer.TradingGolemRenderer;
import com.tradinggolem.entity.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class TradingGolemClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModModelLayers.register();
		EntityRenderers.register(ModEntityTypes.TRADING_GOLEM, TradingGolemRenderer::new);
		EntityRenderers.register(ModEntityTypes.FARMING_GOLEM, FarmingGolemRenderer::new);
		BlockEntityRendererRegistry.register(ModBlocks.TRADING_CHEST_ENTITY, TradingChestRenderer::new);
		BlockEntityRendererRegistry.register(ModBlocks.CROP_CHEST_ENTITY, CropChestRenderer::new);
	}
}
