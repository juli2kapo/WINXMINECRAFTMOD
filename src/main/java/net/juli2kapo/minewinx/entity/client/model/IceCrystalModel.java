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

import java.util.ArrayList;
import java.util.List;

/**
 * Racimo de cristales de hielo procedural. Reglas para que se vea limpio con
 * transparencia: los picos NO se intersectan entre sí (bases separadas,
 * inclinados hacia afuera) y las puntas van rotadas 45° respecto de su base
 * para que ninguna cara quede coplanar (eso causaba el z-fighting del modelo
 * anterior).
 */
public class IceCrystalModel<T extends net.minecraft.world.entity.Entity> extends EntityModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(MineWinx.MOD_ID, "ice_crystal"), "main");

    private static final int SPIKE_COUNT = 7;

    private final List<ModelPart> spikes = new ArrayList<>();

    public IceCrystalModel(ModelPart root) {
        for (int i = 0; i < SPIKE_COUNT; i++) {
            spikes.add(root.getChild("spike" + i));
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // {ángulo (rad) alrededor del centro, radio (px), ancho (px), alto (px), inclinación hacia afuera (rad)}
        float[][] spikes = {
                {0.0F,   0.0F, 5.0F, 22.0F, 0.05F},  // pico central, casi vertical
                {0.0F,   6.5F, 4.0F, 15.0F, 0.30F},
                {1.05F,  6.0F, 3.0F, 11.0F, 0.34F},
                {2.09F,  6.5F, 4.0F, 14.0F, 0.28F},
                {3.14F,  6.0F, 3.0F, 10.0F, 0.36F},
                {4.19F,  6.5F, 4.0F, 16.0F, 0.30F},
                {5.24F,  6.0F, 3.0F, 12.0F, 0.33F},
        };

        for (int k = 0; k < spikes.length; k++) {
            float angle = spikes[k][0];
            float radius = spikes[k][1];
            float w = spikes[k][2];
            float h = spikes[k][3];
            float tilt = spikes[k][4];

            float px = (float) (Math.sin(angle) * radius);
            float pz = (float) (Math.cos(angle) * radius);
            // Inclinación hacia afuera: rotar alejándose del centro del racimo
            float xRot = tilt * (float) Math.cos(angle);
            float zRot = -tilt * (float) Math.sin(angle);

            PartDefinition spike = root.addOrReplaceChild("spike" + k,
                    CubeListBuilder.create().texOffs(0, 0)
                            .addBox(-w / 2.0F, -h, -w / 2.0F, w, h, w, new CubeDeformation(0.0F)),
                    PartPose.offsetAndRotation(px, 24.0F, pz, xRot, angle * 0.5F, zRot));

            // Punta: más angosta, rotada 45° (ninguna cara coplanar con la base) y
            // apenas incrustada 1px para que no haya costura visible.
            float wt = w * 0.55F;
            float ht = h * 0.35F;
            spike.addOrReplaceChild("tip" + k,
                    CubeListBuilder.create().texOffs(32, 0)
                            .addBox(-wt / 2.0F, -ht, -wt / 2.0F, wt, ht, wt, new CubeDeformation(0.0F)),
                    PartPose.offsetAndRotation(0.0F, -h + 1.0F, 0.0F, 0.0F, 0.7854F, 0.0F));
        }

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        for (ModelPart spike : spikes) {
            spike.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    @Override
    public void setupAnim(T pEntity, float pLimbSwing, float pLimbSwingAmount, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
    }
}
