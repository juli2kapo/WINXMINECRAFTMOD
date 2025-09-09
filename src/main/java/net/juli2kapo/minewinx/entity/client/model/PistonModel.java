package net.juli2kapo.minewinx.entity.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.PistonEntity;
import net.juli2kapo.minewinx.entity.client.animations.PistonAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class PistonModel<T extends PistonEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(MineWinx.MOD_ID, "piston"), "main");
    private final ModelPart root;
    private final ModelPart Base;
    private final ModelPart Top;
    private final ModelPart Extender;
    private final ModelPart Extender2;
    private final ModelPart Extender3;
    private final ModelPart Extender4;

    public PistonModel(ModelPart root) {
        this.root = root;
        this.Base = root.getChild("Base");
        this.Top = root.getChild("Top");
        this.Extender = root.getChild("Extender");
        this.Extender2 = root.getChild("Extender2");
        this.Extender3 = root.getChild("Extender3");
        this.Extender4 = root.getChild("Extender4");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Base = partdefinition.addOrReplaceChild("Base", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -12.0F, -8.0F, 16.0F, 12.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition Top = partdefinition.addOrReplaceChild("Top", CubeListBuilder.create().texOffs(0, 28).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 4.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition Extender = partdefinition.addOrReplaceChild("Extender", CubeListBuilder.create().texOffs(10, 53).addBox(-2.0F, -12.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition Extender2 = partdefinition.addOrReplaceChild("Extender2", CubeListBuilder.create().texOffs(10, 53).addBox(-2.0F, -12.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition Extender3 = partdefinition.addOrReplaceChild("Extender3", CubeListBuilder.create().texOffs(10, 53).addBox(-2.0F, -12.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition Extender4 = partdefinition.addOrReplaceChild("Extender4", CubeListBuilder.create().texOffs(10, 53).addBox(-2.0F, -12.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        
        // Apply animation based on the entity's animation progress
        float animationProgress = entity.getAnimationProgress();
        this.animate(entity.getAnimationState(), PistonAnimations.APLASTAR, ageInTicks, 1.0f);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}