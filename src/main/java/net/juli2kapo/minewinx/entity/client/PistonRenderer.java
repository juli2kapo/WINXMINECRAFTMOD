package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.IceArrowEntity;
import net.juli2kapo.minewinx.entity.IceCrystalEntity;
import net.juli2kapo.minewinx.entity.PistonEntity;
import net.juli2kapo.minewinx.entity.WaterBlobProjectileEntity;
import net.juli2kapo.minewinx.entity.client.model.IceCrystalModel;
import net.juli2kapo.minewinx.entity.client.model.PistonModel;
import net.juli2kapo.minewinx.entity.client.model.WaterBlobModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class PistonRenderer extends EntityRenderer<PistonEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/piston.png");
    private final PistonModel<PistonEntity> model;
    public PistonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new PistonModel<>(context.bakeLayer(PistonModel.LAYER_LOCATION));
    }

    @Override
    public ResourceLocation getTextureLocation(PistonEntity pEntity) {
        return TEXTURE_LOCATION;
    }

    @Override
    public void render(PistonEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pMatrixStack,
                       MultiBufferSource pBuffer, int pPackedLight) {
        pMatrixStack.pushPose();
        float scale = pEntity.getScale();
        pMatrixStack.scale(scale, scale, scale);
        this.model.setupAnim(pEntity, 0.0F, 0.0F, pEntity.tickCount + pPartialTicks, 0.0F, 0.0F);
        VertexConsumer vertexconsumer = pBuffer.getBuffer(this.model.renderType(getTextureLocation(pEntity)));
        this.model.renderToBuffer(pMatrixStack, vertexconsumer, pPackedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pMatrixStack.popPose();
        super.render(pEntity, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight);
    }

}
