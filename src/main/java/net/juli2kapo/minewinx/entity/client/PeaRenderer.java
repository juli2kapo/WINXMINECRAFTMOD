package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.client.geo.GeoMesh;
import net.juli2kapo.minewinx.entity.plants.PeaProjectileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class PeaRenderer extends EntityRenderer<PeaProjectileEntity> {

    private static final ResourceLocation GEO = new ResourceLocation(MineWinx.MOD_ID, "geo/plants/pea.geo.json");
    private static final ResourceLocation TEX_NORMAL = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/plants/pea.png");
    private static final ResourceLocation TEX_FROZEN = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/plants/icepea.png");
    private static final ResourceLocation TEX_FIRE = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/plants/fire_pea.png");

    public PeaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PeaProjectileEntity entity) {
        return switch (entity.getVariant()) {
            case FROZEN -> TEX_FROZEN;
            case FIRE -> TEX_FIRE;
            default -> TEX_NORMAL;
        };
    }

    @Override
    public void render(PeaProjectileEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        GeoMesh.get(GEO).render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                entity.tickCount + partialTicks, null, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
