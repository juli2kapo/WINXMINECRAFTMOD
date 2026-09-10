package net.juli2kapo.minewinx.entity.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.DragonHeadEntity;
import net.juli2kapo.minewinx.powers.DragonHeadPoints;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

/**
 * La cabeza del dragón como nube de VOXELS: cada punto extraído del modelo de
 * MineBench (DragonHeadPoints) se hornea como un mini-cubo. Espacio Y-arriba
 * centrado en el origen, con +Z hacia adelante (el renderer no aplica flip).
 */
public class DragonHeadModel extends EntityModel<DragonHeadEntity> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(MineWinx.MOD_ID, "dragon_head"), "main");

    // Escala: la cabeza normalizada (dimensión mayor = 1.0) ocupa 24 px ≈ 1.5 bloques
    private static final float SCALE_PX = 24.0F;
    private static final float CUBE = 1.7F;

    private final ModelPart flame;
    private final ModelPart glow;
    private final ModelPart white;
    private final ModelPart smoke;

    public DragonHeadModel(ModelPart root) {
        this.flame = root.getChild("flame");
        this.glow = root.getChild("glow");
        this.white = root.getChild("white");
        this.smoke = root.getChild("smoke");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Textura 16x16 en cuadrantes: (0,0) fuego, (8,0) brillo, (0,8) blanco, (8,8) humo
        root.addOrReplaceChild("flame", cubesFor(DragonHeadPoints.FLAME, 0, 0), PartPose.ZERO);
        root.addOrReplaceChild("glow", cubesFor(DragonHeadPoints.GLOW, 8, 0), PartPose.ZERO);
        root.addOrReplaceChild("white", cubesFor(DragonHeadPoints.WHITE, 0, 8), PartPose.ZERO);
        root.addOrReplaceChild("smoke", cubesFor(DragonHeadPoints.SMOKE, 8, 8), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    private static CubeListBuilder cubesFor(float[] points, int u, int v) {
        CubeListBuilder builder = CubeListBuilder.create();
        for (int i = 0; i < points.length; i += 3) {
            float px = points[i] * SCALE_PX;
            float py = points[i + 1] * SCALE_PX;
            float pz = points[i + 2] * SCALE_PX;
            builder.texOffs(u, v).addBox(px - CUBE / 2, py - CUBE / 2, pz - CUBE / 2, CUBE, CUBE, CUBE, new CubeDeformation(0.0F));
        }
        return builder;
    }

    @Override
    public void setupAnim(DragonHeadEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        flame.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        glow.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        white.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        smoke.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
