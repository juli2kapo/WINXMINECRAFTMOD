package net.juli2kapo.minewinx.entity.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class WaterBlobModel<T extends Entity> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(MineWinx.MOD_ID, "water_blob"), "main");
    private final ModelPart bb_main;

    public WaterBlobModel(ModelPart root) {
        this.bb_main = root.getChild("bb_main");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bb_main = partdefinition.addOrReplaceChild("bb_main", CubeListBuilder.create().texOffs(48, 0).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(0, 0).addBox(-6.0F, -12.0F, -6.0F, 12.0F, 10.0F, 12.0F, new CubeDeformation(0.0F))
                .texOffs(0, 22).addBox(-5.0F, -2.0F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(48, 9).addBox(-4.0F, -14.0F, -4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(0, 33).addBox(-5.0F, -13.0F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        bb_main.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -1.0F, -4.0F, 6.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -7.0F, 7.0F, -1.5708F, 0.0F, -1.5708F));
        bb_main.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(36, 44).addBox(-4.0F, -1.0F, -5.0F, 8.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -7.0F, 6.0F, -1.5708F, 0.0F, -1.5708F));
        bb_main.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(56, 55).addBox(-3.0F, -1.0F, -4.0F, 6.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -7.0F, -8.0F, -1.5708F, 0.0F, -1.5708F));
        bb_main.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(0, 44).addBox(-4.0F, -1.0F, -5.0F, 8.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -7.0F, -7.0F, -1.5708F, 0.0F, -1.5708F));
        bb_main.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(28, 55).addBox(-3.0F, -1.0F, -4.0F, 6.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -7.0F, 0.0F, 0.0F, 0.0F, -1.5708F));
        bb_main.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(40, 33).addBox(-4.0F, -1.0F, -5.0F, 8.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, -7.0F, 0.0F, 0.0F, 0.0F, -1.5708F));
        bb_main.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(0, 55).addBox(-3.0F, -1.0F, -4.0F, 6.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.0F, -7.0F, 0.0F, 0.0F, 0.0F, -1.5708F));
        bb_main.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(40, 22).addBox(-4.0F, -1.0F, -5.0F, 8.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, -7.0F, 0.0F, 0.0F, 0.0F, -1.5708F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        bb_main.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}