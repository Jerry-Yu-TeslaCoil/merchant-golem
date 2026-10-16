package com.tradinggolem.client.renderer;

import com.tradinggolem.TradingGolemMod;
import com.tradinggolem.block.TradingChestBlock;
import com.tradinggolem.block.TradingChestBlockEntity;
import com.tradinggolem.client.model.ModModelLayers;
import com.tradinggolem.client.model.TradingChestModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;

public class TradingChestRenderer implements BlockEntityRenderer<TradingChestBlockEntity, TradingChestRenderState> {
	private static final Identifier TEXTURE = TradingGolemMod.id("textures/entity/trading_chest.png");
	private final TradingChestModel model;

	public TradingChestRenderer(BlockEntityRendererProvider.Context context) {
		this.model = new TradingChestModel(context.bakeLayer(ModModelLayers.TRADING_CHEST));
	}

	@Override
	public TradingChestRenderState createRenderState() {
		return new TradingChestRenderState();
	}

	@Override
	public void extractRenderState(TradingChestBlockEntity chest, TradingChestRenderState state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(chest, state, breakProgress);
		state.facing = chest.getBlockState().getValue(TradingChestBlock.FACING);
		state.open = chest.getOpenNess(partialTick);
	}

	@Override
	public void submit(TradingChestRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(ChestRenderer.modelTransformation(state.facing));
		float openness = 1.0F - state.open;
		openness = 1.0F - openness * openness * openness;
		collector.submitModel(
				this.model,
				openness,
				poseStack,
				this.model.renderType(TEXTURE),
				state.lightCoords,
				OverlayTexture.NO_OVERLAY,
				-1,
				null,
				0
		);
		if (state.breakProgress != null) {
			collector.order(1).submitCrumblingOverlay(
					this.model,
					openness,
					poseStack,
					this.model.renderType(TEXTURE),
					state.lightCoords,
					OverlayTexture.NO_OVERLAY,
					-1,
					state.breakProgress
			);
		}
		poseStack.popPose();
	}
}
