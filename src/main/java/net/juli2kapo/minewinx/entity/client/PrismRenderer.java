package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.PrismEntity;
import net.juli2kapo.minewinx.entity.client.model.PrismModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class PrismRenderer extends EntityRenderer<PrismEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/prism.png");
    private final PrismModel<PrismEntity> model;

    public PrismRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new PrismModel<>(context.bakeLayer(PrismModel.LAYER_LOCATION));
    }

    @Override
    public ResourceLocation getTextureLocation(PrismEntity entity) {
        return TEXTURE_LOCATION;
    }

    @Override
    public void render(PrismEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        float age = entity.tickCount + partialTicks;
        // Flotar: el modelo está centrado en el origen; subirlo al centro del
        // hitbox más un balanceo suave (sin flip vanilla — el modelo es Y-arriba)
        float bob = 0.08F * Mth.sin(age * 0.09F);
        poseStack.translate(0.0F, entity.getBbHeight() * 0.5F + bob, 0.0F);

        this.model.setupAnim(entity, 0.0F, 0.0F, age, 0.0F, 0.0F);
        // Emisivo a plena luz, igual que los rayos que refracta
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE_LOCATION));
        this.model.renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 0.9F);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
