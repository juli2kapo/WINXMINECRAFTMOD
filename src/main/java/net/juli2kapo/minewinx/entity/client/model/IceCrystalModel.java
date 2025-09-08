package net.juli2kapo.minewinx.entity.client.model;// Made with Blockbench 4.12.5
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

public class IceCrystalModel<T extends net.minecraft.world.entity.Entity> extends EntityModel<T> {
    // This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(MineWinx.MOD_ID, "ice_crystal"), "main");
    private final ModelPart Coso1;
    private final ModelPart Coso2;
    private final ModelPart Coso3;
    private final ModelPart Coso4;

    public IceCrystalModel(ModelPart root) {
        this.Coso1 = root.getChild("Coso1");
        this.Coso2 = root.getChild("Coso2");
        this.Coso3 = root.getChild("Coso3");
        this.Coso4 = root.getChild("Coso4");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Coso1 = partdefinition.addOrReplaceChild("Coso1", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition cube_r1 = Coso1.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(76, 37).addBox(-5.0F, -12.0F, 0.0F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -13.0F, 2.0F, -0.5624F, 0.0395F, -0.2436F));

        PartDefinition cube_r2 = Coso1.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(74, 17).addBox(-5.0F, -12.0F, -1.0F, 2.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -12.0F, 2.0F, -0.5624F, 0.0395F, -0.2436F));

        PartDefinition cube_r3 = Coso1.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(74, 9).addBox(-5.0F, -12.0F, -2.0F, 3.0F, 1.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -11.0F, 2.0F, -0.5624F, 0.0395F, -0.2436F));

        PartDefinition cube_r4 = Coso1.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(62, 73).addBox(-5.0F, -12.0F, -3.0F, 4.0F, 1.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -10.0F, 2.0F, -0.5624F, 0.0395F, -0.2436F));

        PartDefinition cube_r5 = Coso1.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(34, 73).addBox(-5.0F, -12.0F, -4.0F, 5.0F, 1.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -9.0F, 2.0F, -0.5624F, 0.0395F, -0.2436F));

        PartDefinition cube_r6 = Coso1.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(38, 62).addBox(-5.0F, -12.0F, -5.0F, 6.0F, 1.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -8.0F, 2.0F, -0.5624F, 0.0395F, -0.2436F));

        PartDefinition cube_r7 = Coso1.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(0, 37).addBox(-6.0F, -12.0F, -5.0F, 8.0F, 18.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, -8.0F, 1.0F, -0.5624F, 0.0395F, -0.2436F));

        PartDefinition Coso2 = partdefinition.addOrReplaceChild("Coso2", CubeListBuilder.create().texOffs(38, 37).addBox(-5.0F, -16.0F, -8.0F, 8.0F, 14.0F, 11.0F, new CubeDeformation(0.0F))
                .texOffs(70, 62).addBox(-4.0F, -17.0F, -7.0F, 6.0F, 2.0F, 9.0F, new CubeDeformation(0.0F))
                .texOffs(68, 29).addBox(-3.0F, -18.0F, -6.0F, 4.0F, 1.0F, 7.0F, new CubeDeformation(0.0F))
                .texOffs(16, 75).addBox(-2.0F, -19.0F, -5.0F, 2.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 22.0F, 2.0F, 0.903F, 0.2849F, 0.1756F));

        PartDefinition Coso3 = partdefinition.addOrReplaceChild("Coso3", CubeListBuilder.create().texOffs(42, 0).addBox(0.0F, -19.0F, -3.0F, 6.0F, 19.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(74, 0).addBox(1.0F, -20.0F, -2.0F, 4.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(0, 75).addBox(2.0F, -21.0F, -1.0F, 2.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, 18.0F, -5.0F, -0.48F, 0.0F, 0.5672F));

        PartDefinition Coso4 = partdefinition.addOrReplaceChild("Coso4", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -27.0F, -6.0F, 11.0F, 27.0F, 10.0F, new CubeDeformation(0.0F))
                .texOffs(0, 66).addBox(-5.0F, -28.0F, -5.0F, 9.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(42, 29).addBox(-4.0F, -29.0F, -4.0F, 7.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
                .texOffs(74, 24).addBox(-3.0F, -30.0F, -3.0F, 5.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                .texOffs(76, 43).addBox(-2.0F, -31.0F, -2.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }


    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        Coso1.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        Coso2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        Coso3.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        Coso4.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void setupAnim(T pEntity, float pLimbSwing, float pLimbSwingAmount, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {

    }

}