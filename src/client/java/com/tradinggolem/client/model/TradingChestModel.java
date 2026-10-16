package com.tradinggolem.client.model;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public class TradingChestModel extends Model<Float> {
	private final ModelPart lid;

	public TradingChestModel(ModelPart root) {
		super(root, RenderTypes::entityCutoutCull);
		this.lid = root.getChild("lid");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild(
				"bottom",
				CubeListBuilder.create().texOffs(0, 19).addBox(1.0F, 0.0F, 1.0F, 14.0F, 10.0F, 14.0F),
				PartPose.ZERO
		);
		PartDefinition lid = root.addOrReplaceChild(
				"lid",
				CubeListBuilder.create().texOffs(0, 0).addBox(1.0F, 0.0F, 0.0F, 14.0F, 4.0F, 14.0F),
				PartPose.offset(0.0F, 10.0F, 1.0F)
		);
		lid.addOrReplaceChild(
				"latch",
				CubeListBuilder.create().texOffs(0, 48).addBox(7.0F, -3.0F, 14.0F, 2.0F, 4.0F, 1.0F),
				PartPose.ZERO
		);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(Float openness) {
		super.setupAnim(openness);
		this.lid.xRot = -openness * ((float) Math.PI / 2.0F);
	}
}
