package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.WaterBlobProjectileEntity;
import net.juli2kapo.minewinx.entity.client.model.WaterBlobModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class WaterBlobProjectileRenderer extends EntityRenderer<WaterBlobProjectileEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/water_blob.png");
    private final WaterBlobModel<WaterBlobProjectileEntity> model;

    public WaterBlobProjectileRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
        this.model = new WaterBlobModel<>(pContext.bakeLayer(WaterBlobModel.LAYER_LOCATION));
    }

    @Override
    public void render(WaterBlobProjectileEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
        pPoseStack.pushPose();
        pPoseStack.scale(0.5f, 0.5f, 0.5f);
        VertexConsumer vertexconsumer = pBuffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(pEntity)));
        model.renderToBuffer(pPoseStack, vertexconsumer, pPackedLight, OverlayTexture.NO_OVERLAY, 0.6F, 0.8F, 1.0F, 0.7F);
        pPoseStack.popPose();
        super.render(pEntity, pEntityYaw, pPartialTicks, pPoseStack, pBuffer, pPackedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(WaterBlobProjectileEntity pEntity) {
        return TEXTURE_LOCATION;
    }
}