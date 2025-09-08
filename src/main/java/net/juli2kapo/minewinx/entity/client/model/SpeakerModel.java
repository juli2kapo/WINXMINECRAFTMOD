package net.juli2kapo.minewinx.entity.client.model;

// Made with Blockbench 4.12.6
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;


public class SpeakerModel<T extends net.minecraft.world.entity.Entity> extends EntityModel<T> {
    // This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "speakermodel"), "main");
    private final ModelPart Abajo;
    private final ModelPart Abajo2;
    private final ModelPart bb_main;

    public SpeakerModel(ModelPart root) {
        this.Abajo = root.getChild("Abajo");
        this.Abajo2 = root.getChild("Abajo2");
        this.bb_main = root.getChild("bb_main");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Abajo = partdefinition.addOrReplaceChild("Abajo", CubeListBuilder.create().texOffs(30, 42).addBox(1.0F, -14.0F, -7.0F, 1.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(44, 0).addBox(1.0F, -3.0F, -7.0F, 1.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(0, 60).addBox(2.0F, -2.0F, -7.0F, 1.0F, 2.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(60, 50).addBox(1.0F, -11.0F, 4.0F, 1.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(60, 72).addBox(2.0F, -12.0F, 5.0F, 1.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(60, 61).addBox(1.0F, -11.0F, -7.0F, 1.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(66, 72).addBox(2.0F, -12.0F, -7.0F, 1.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(44, 34).addBox(1.0F, -8.0F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition Abajo2 = partdefinition.addOrReplaceChild("Abajo2", CubeListBuilder.create().texOffs(44, 17).addBox(1.0F, -14.0F, -7.0F, 1.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(30, 59).addBox(1.0F, -3.0F, -7.0F, 1.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(60, 34).addBox(2.0F, -14.0F, -7.0F, 1.0F, 2.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(68, 50).addBox(1.0F, -11.0F, 4.0F, 1.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(72, 72).addBox(2.0F, -12.0F, 5.0F, 1.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(68, 61).addBox(1.0F, -11.0F, -7.0F, 1.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(74, 0).addBox(2.0F, -12.0F, -7.0F, 1.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(44, 38).addBox(1.0F, -8.0F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 10.0F, 0.0F));

        PartDefinition bb_main = partdefinition.addOrReplaceChild("bb_main", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -28.0F, -7.0F, 8.0F, 28.0F, 14.0F, new CubeDeformation(0.0F))
                .texOffs(0, 42).addBox(2.0F, -16.0F, -7.0F, 1.0F, 4.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        Abajo.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        Abajo2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        bb_main.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void setupAnim(T pEntity, float pLimbSwing, float pLimbSwingAmount, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {

    }
}