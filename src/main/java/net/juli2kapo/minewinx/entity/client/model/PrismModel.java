package net.juli2kapo.minewinx.entity.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.PrismEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

/**
 * Cristal flotante del Prisma de Luz: núcleo tipo diamante (cajas apiladas
 * rotadas 45° entre sí para que ninguna cara quede coplanar) más tres esquirlas
 * que orbitan. Construido centrado en el origen, con Y hacia arriba (el
 * renderer NO aplica el flip vanilla).
 */
public class PrismModel<T extends PrismEntity> extends EntityModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(MineWinx.MOD_ID, "prism"), "main");

    private final ModelPart core;
    private final ModelPart tipTop;
    private final ModelPart tipBottom;
    private final ModelPart shard0;
    private final ModelPart shard1;
    private final ModelPart shard2;

    public PrismModel(ModelPart root) {
        this.core = root.getChild("core");
        this.tipTop = root.getChild("tip_top");
        this.tipBottom = root.getChild("tip_bottom");
        this.shard0 = root.getChild("shard0");
        this.shard1 = root.getChild("shard1");
        this.shard2 = root.getChild("shard2");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("core",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -4.5F, -3.0F, 6.0F, 9.0F, 6.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

        root.addOrReplaceChild("tip_top",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        root.addOrReplaceChild("tip_bottom",
                CubeListBuilder.create().texOffs(16, 16).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.ZERO);

        // Esquirlas orbitando: pivote en el centro, caja desplazada del eje;
        // girar el yRot del hueso las hace orbitar
        for (int i = 0; i < 3; i++) {
            root.addOrReplaceChild("shard" + i,
                    CubeListBuilder.create().texOffs(16, 0).addBox(6.5F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
                    PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.15F * (i - 1)));
        }

        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        core.yRot = 0.7854F + ageInTicks * 0.04F;      // giro lento del núcleo
        tipTop.yRot = -ageInTicks * 0.06F;             // las puntas giran al revés
        tipBottom.yRot = -ageInTicks * 0.06F;
        shard0.yRot = ageInTicks * 0.10F;
        shard1.yRot = ageInTicks * 0.10F + 2.094F;     // 120°
        shard2.yRot = ageInTicks * 0.10F + 4.189F;     // 240°
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        core.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        tipTop.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        tipBottom.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        shard0.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        shard1.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        shard2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
