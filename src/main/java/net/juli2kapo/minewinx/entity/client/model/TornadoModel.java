package net.juli2kapo.minewinx.entity.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.TornadoEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class TornadoModel<T extends TornadoEntity> extends HierarchicalModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(MineWinx.MOD_ID, "tornado"), "main");

    private static final int SEGMENTS = 6;
    // Ancho (px) de cada anillo, de la base a la cima: embudo que se abre hacia arriba
    private static final int[] WIDTHS = {4, 8, 14, 20, 26, 32};
    private static final int SEGMENT_HEIGHT = 16;

    private final ModelPart root;
    private final ModelPart[] segments = new ModelPart[SEGMENTS];

    public TornadoModel(ModelPart root) {
        this.root = root;
        for (int i = 0; i < SEGMENTS; i++) {
            this.segments[i] = root.getChild("segment" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        for (int i = 0; i < SEGMENTS; i++) {
            int w = WIDTHS[i];
            // Pivote en el centro del segmento para que yRot gire sobre el eje del embudo
            float pivotY = 24.0F - (i * SEGMENT_HEIGHT) - (SEGMENT_HEIGHT / 2.0F);
            partdefinition.addOrReplaceChild("segment" + i,
                    CubeListBuilder.create().texOffs(0, 0)
                            .addBox(-w / 2.0F, -SEGMENT_HEIGHT / 2.0F, -w / 2.0F, w, SEGMENT_HEIGHT, w, new CubeDeformation(0.0F)),
                    PartPose.offset(0.0F, pivotY, 0.0F));
        }

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        for (int i = 0; i < SEGMENTS; i++) {
            // Cada anillo gira a distinta velocidad y fase: eso vende el remolino
            segments[i].yRot = ageInTicks * (0.25F + 0.08F * i) + i * 0.9F;
            // Balanceo leve, mayor hacia la cima
            float sway = 0.6F * i;
            segments[i].x = Mth.sin(ageInTicks * 0.09F + i * 0.7F) * sway;
            segments[i].z = Mth.cos(ageInTicks * 0.07F + i * 0.7F) * sway;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        for (ModelPart segment : segments) {
            segment.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
