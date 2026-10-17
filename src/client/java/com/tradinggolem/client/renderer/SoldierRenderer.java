package com.tradinggolem.client.renderer;

import com.tradinggolem.TradingGolemMod;
import com.tradinggolem.client.model.ModModelLayers;
import com.tradinggolem.client.model.SoldierModel;
import com.tradinggolem.entity.Soldier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemUseAnimation;

/** Player skeleton, so armor, held weapons, and the horse seat use the vanilla poses. */
public class SoldierRenderer extends HumanoidMobRenderer<Soldier, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = TradingGolemMod.id("textures/entity/soldier.png");

	public SoldierRenderer(EntityRendererProvider.Context context) {
		super(context, new SoldierModel(context.bakeLayer(ModModelLayers.SOLDIER)), 0.5F);
		ArmorModelSet<HumanoidModel<HumanoidRenderState>> armor = ArmorModelSet.bake(
				ModelLayers.PLAYER_ARMOR,
				context.getModelSet(),
				part -> new HumanoidModel<>(part)
		);
		this.addLayer(new HumanoidArmorLayer<>(this, armor, context.getEquipmentRenderer()));
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(Soldier soldier, HumanoidArm arm) {
		if (soldier.isUsingItem()
				&& soldier.getUsedItemHand().asArm(soldier.getMainArm()) == arm
				&& soldier.getItemHeldByArm(arm).getUseAnimation() == ItemUseAnimation.BLOCK) {
			return HumanoidModel.ArmPose.BLOCK;
		}
		return super.getArmPose(soldier, arm);
	}
}
