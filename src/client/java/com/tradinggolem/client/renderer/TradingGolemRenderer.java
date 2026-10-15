package com.tradinggolem.client.renderer;

import com.tradinggolem.TradingGolemMod;
import com.tradinggolem.client.model.ModModelLayers;
import com.tradinggolem.client.model.TradingGolemModel;
import com.tradinggolem.entity.TradingGolem;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class TradingGolemRenderer extends MobRenderer<TradingGolem, LivingEntityRenderState, TradingGolemModel> {
	private static final Identifier TEXTURE = TradingGolemMod.id("textures/entity/trading_golem.png");

	public TradingGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new TradingGolemModel(context.bakeLayer(ModModelLayers.TRADING_GOLEM)), 0.35F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}
}
