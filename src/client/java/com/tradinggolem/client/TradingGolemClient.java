package com.tradinggolem.client;

import com.tradinggolem.block.ModBlocks;
import com.tradinggolem.client.model.ModModelLayers;
import com.tradinggolem.client.renderer.CropChestRenderer;
import com.tradinggolem.client.renderer.FarmingGolemRenderer;
import com.tradinggolem.client.renderer.SoldierChestRenderer;
import com.tradinggolem.client.renderer.TradingChestRenderer;
import com.tradinggolem.client.renderer.TradingGolemRenderer;
import com.tradinggolem.entity.ModEntityTypes;
import com.tradinggolem.entity.Soldier;
import com.tradinggolem.menu.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.VillagerRenderer;

public final class TradingGolemClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModModelLayers.register();
		MenuScreens.register(ModMenus.SOLDIER, SoldierScreen::new);
		EntityRenderers.register(ModEntityTypes.TRADING_GOLEM, TradingGolemRenderer::new);
		EntityRenderers.register(ModEntityTypes.FARMING_GOLEM, FarmingGolemRenderer::new);
		EntityRenderers.register(ModEntityTypes.SOLDIER, soldierRenderer());
		BlockEntityRendererRegistry.register(ModBlocks.TRADING_CHEST_ENTITY, TradingChestRenderer::new);
		BlockEntityRendererRegistry.register(ModBlocks.CROP_CHEST_ENTITY, CropChestRenderer::new);
		BlockEntityRendererRegistry.register(ModBlocks.SOLDIER_CHEST_ENTITY, SoldierChestRenderer::new);
	}

	@SuppressWarnings("unchecked")
	private static EntityRendererProvider<Soldier> soldierRenderer() {
		return context -> (EntityRenderer<Soldier, ?>) (Object) new VillagerRenderer(context);
	}
}
