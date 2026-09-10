package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.DragonHeadEntity;
import net.juli2kapo.minewinx.entity.client.model.DragonHeadModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class DragonHeadRenderer extends EntityRenderer<DragonHeadEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/dragon_head_cloud.png");
    private final DragonHeadModel model;

    public DragonHeadRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new DragonHeadModel(context.bakeLayer(DragonHeadModel.LAYER_LOCATION));
    }

    @Override
    public ResourceLocation getTextureLocation(DragonHeadEntity entity) {
        return TEXTURE_LOCATION;
    }

    @Override
    public void render(DragonHeadEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // Orientar el modelo (+Z adelante) según el rumbo fijado al nacer
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(-entity.getXRot()));

        float age = entity.tickCount + partialTicks;
        float scale = 0.8F + 0.4F * entity.getStage();
        poseStack.scale(scale, scale, scale);

        // Fundido de salida hacia el final de la vida
        float alpha = 1.0F - Math.max(0, (age - 6.0F) / (DragonHeadEntity.LIFETIME - 6.0F)) * 0.8F;

        this.model.setupAnim(entity, 0, 0, age, 0, 0);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE_LOCATION));
        this.model.renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, alpha);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
