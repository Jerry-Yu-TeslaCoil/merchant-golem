package com.tradinggolem.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Player skeleton plus the villager nose. */
public class SoldierModel extends HumanoidModel<HumanoidRenderState> {
	public SoldierModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		mesh.getRoot().getChild("head").addOrReplaceChild(
				"nose",
				CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -4.0F, -6.0F, 2.0F, 4.0F, 2.0F),
				PartPose.ZERO
		);
		return LayerDefinition.create(mesh, 64, 64);
	}
}
