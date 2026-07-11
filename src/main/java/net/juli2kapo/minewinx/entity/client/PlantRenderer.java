package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.client.geo.GeoMesh;
import net.juli2kapo.minewinx.entity.plants.PlantEntity;
import net.juli2kapo.minewinx.entity.plants.PlantType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

public class PlantRenderer extends EntityRenderer<PlantEntity> {

    private static final Map<PlantType, ResourceLocation> TEXTURES = new EnumMap<>(PlantType.class);
    private static final Map<PlantType, ResourceLocation> GEOS = new EnumMap<>(PlantType.class);
    private static final ResourceLocation COB_RELOADING_TEXTURE =
            new ResourceLocation(MineWinx.MOD_ID, "textures/entity/plants/cobcannon_nocob.png");

    static {
        for (PlantType type : PlantType.values()) {
            TEXTURES.put(type, new ResourceLocation(MineWinx.MOD_ID, "textures/entity/plants/" + type.textureName + ".png"));
            GEOS.put(type, new ResourceLocation(MineWinx.MOD_ID, "geo/plants/" + type.geoName + ".geo.json"));
        }
    }

    public PlantRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PlantEntity entity) {
        if (entity.getPlantType() == PlantType.COB_CANNON && entity.isReloading()) {
            return COB_RELOADING_TEXTURE;
        }
        return TEXTURES.get(entity.getPlantType());
    }

    @Override
    public void render(PlantEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));

        // Balanceo idle sutil de toda la planta
        float age = entity.tickCount + partialTicks;
        float sway = Mth.sin(age * 0.08F + entity.getId() * 1.7F) * 2.0F;
        poseStack.mulPose(Axis.ZP.rotationDegrees(sway));

        GeoMesh mesh = GeoMesh.get(GEOS.get(entity.getPlantType()));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        mesh.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, age, null, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
