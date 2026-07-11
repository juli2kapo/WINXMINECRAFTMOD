package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.client.geo.GeoMesh;
import net.juli2kapo.minewinx.entity.plants.CobProjectileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class CobRenderer extends EntityRenderer<CobProjectileEntity> {

    private static final ResourceLocation GEO = new ResourceLocation(MineWinx.MOD_ID, "geo/plants/cob.geo.json");
    // El choclo comparte la hoja de textura del cañón
    private static final ResourceLocation TEXTURE = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/plants/cobcannon.png");

    public CobRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(CobProjectileEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(CobProjectileEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // Gira mientras vuela
        poseStack.mulPose(Axis.XP.rotationDegrees((entity.tickCount + partialTicks) * 15.0F));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        GeoMesh.get(GEO).render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                entity.tickCount + partialTicks, null, 1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
