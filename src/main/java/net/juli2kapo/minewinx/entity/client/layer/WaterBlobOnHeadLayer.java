package net.juli2kapo.minewinx.entity.client.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.entity.client.model.WaterBlobModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class WaterBlobOnHeadLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation WATER_BLOB_TEXTURE = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/water_blob.png");
    private final WaterBlobModel<T> model;

    public WaterBlobOnHeadLayer(RenderLayerParent<T, M> pRenderer, WaterBlobModel<T> model) {
        super(pRenderer);
        this.model = model;
    }

    @Override
    public void render(PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight, T pLivingEntity, float pLimbSwing, float pLimbSwingAmount, float pPartialTick, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
        if (pLivingEntity.hasEffect(ModEffects.DROWNING_TARGET.get())) {
            pPoseStack.pushPose();
            pPoseStack.translate(0, pLivingEntity.getBbHeight() + 0.3F, 0);
            pPoseStack.scale(0.5F, 0.5F, 0.5F);

            VertexConsumer vertexconsumer = pBuffer.getBuffer(RenderType.entityTranslucent(WATER_BLOB_TEXTURE));
            model.renderToBuffer(pPoseStack, vertexconsumer, pPackedLight, OverlayTexture.NO_OVERLAY, 0.6F, 0.8F, 1.0F, 0.7F);
            pPoseStack.popPose();
        }
    }
}