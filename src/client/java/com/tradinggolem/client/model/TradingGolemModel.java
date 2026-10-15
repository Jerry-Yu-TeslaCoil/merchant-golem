package com.tradinggolem.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

public class TradingGolemModel extends EntityModel<LivingEntityRenderState> {
	private final ModelPart head;
	private final ModelPart leftArm;
	private final ModelPart rightArm;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;

	public TradingGolemModel(ModelPart root) {
		super(root);
		this.head = root.getChild(PartNames.HEAD);
		this.leftArm = root.getChild(PartNames.LEFT_ARM);
		this.rightArm = root.getChild(PartNames.RIGHT_ARM);
		this.leftLeg = root.getChild(PartNames.LEFT_LEG);
		this.rightLeg = root.getChild(PartNames.RIGHT_LEG);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild(
				PartNames.HEAD,
				CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offset(0.0F, 14.0F, 0.0F)
		);
		root.addOrReplaceChild(
				PartNames.BODY,
				CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 6.0F, 4.0F),
				PartPose.offset(0.0F, 14.0F, 0.0F)
		);
		root.addOrReplaceChild(
				PartNames.RIGHT_ARM,
				CubeListBuilder.create().texOffs(32, 16).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offset(-4.0F, 15.0F, 0.0F)
		);
		root.addOrReplaceChild(
				PartNames.LEFT_ARM,
				CubeListBuilder.create().texOffs(32, 16).mirror().addBox(0.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offset(4.0F, 15.0F, 0.0F)
		);
		root.addOrReplaceChild(
				PartNames.RIGHT_LEG,
				CubeListBuilder.create().texOffs(32, 28).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
				PartPose.offset(-2.0F, 20.0F, 0.0F)
		);
		root.addOrReplaceChild(
				PartNames.LEFT_LEG,
				CubeListBuilder.create().texOffs(32, 28).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
				PartPose.offset(2.0F, 20.0F, 0.0F)
		);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.4F * state.walkAnimationSpeed;
		this.rightLeg.xRot = swing;
		this.leftLeg.xRot = -swing;
		this.rightArm.xRot = -swing;
		this.leftArm.xRot = swing;
	}
}
