package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.LightRayEntity;
import net.juli2kapo.minewinx.entity.client.model.LightRayModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class LightRayRenderer extends EntityRenderer<LightRayEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/lightray.png");
    private final LightRayModel<LightRayEntity> model;

    public LightRayRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new LightRayModel<>(context.bakeLayer(LightRayModel.LAYER_LOCATION));
    }

    @Override
    public ResourceLocation getTextureLocation(LightRayEntity pEntity) {
        return TEXTURE_LOCATION;
    }

    @Override
    public void render(LightRayEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pMatrixStack,
                       MultiBufferSource pBuffer, int pPackedLight) {
        pMatrixStack.pushPose();

        // Orientar el rayo a lo largo de su dirección de vuelo (el modelo apunta en Z)
        Vec3 motion = pEntity.getDeltaMovement();
        if (motion.lengthSqr() > 1.0E-4D) {
            float yaw = (float) (Mth.atan2(motion.x, motion.z) * (180.0 / Math.PI));
            float pitch = (float) (Mth.atan2(motion.y, motion.horizontalDistance()) * (180.0 / Math.PI));
            pMatrixStack.mulPose(Axis.YP.rotationDegrees(yaw));
            pMatrixStack.mulPose(Axis.XP.rotationDegrees(-pitch));
        }

        // Pulso sutil para que "vibre" como luz viva
        float pulse = 1.0F + 0.08F * Mth.sin((pEntity.tickCount + pPartialTicks) * 0.6F);
        pMatrixStack.scale(pulse, pulse, pulse);

        // BRILLO: emisivo a plena luz — los rayos de luz no dependen de la luz del mundo
        int fullBright = LightTexture.FULL_BRIGHT;
        VertexConsumer emissive = pBuffer.getBuffer(RenderType.entityTranslucentEmissive(getTextureLocation(pEntity)));

        // Núcleo
        this.model.renderToBuffer(pMatrixStack, emissive, fullBright, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        // Halo: el mismo modelo agrandado, tenue y cálido
        pMatrixStack.scale(1.6F, 1.6F, 1.6F);
        this.model.renderToBuffer(pMatrixStack, emissive, fullBright, OverlayTexture.NO_OVERLAY, 1.0F, 0.95F, 0.6F, 0.35F);

        pMatrixStack.popPose();
        super.render(pEntity, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight);
    }
}
