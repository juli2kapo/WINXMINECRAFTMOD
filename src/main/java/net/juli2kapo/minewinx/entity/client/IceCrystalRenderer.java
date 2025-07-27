package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.IceArrowEntity;
import net.juli2kapo.minewinx.entity.IceCrystalEntity;
import net.juli2kapo.minewinx.entity.WaterBlobProjectileEntity;
import net.juli2kapo.minewinx.entity.client.model.IceCrystalModel;
import net.juli2kapo.minewinx.entity.client.model.WaterBlobModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class IceCrystalRenderer extends EntityRenderer<IceCrystalEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/ice_crystal.png");
    private final IceCrystalModel<IceCrystalEntity> model;
    public IceCrystalRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new IceCrystalModel<>(context.bakeLayer(IceCrystalModel.LAYER_LOCATION));
    }

    @Override
    public ResourceLocation getTextureLocation(IceCrystalEntity pEntity) {
        return TEXTURE_LOCATION;
    }
    @Override
    public void render(IceCrystalEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
        pPoseStack.pushPose();
        pPoseStack.scale(2f, 2f, 2f);
        pPoseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180.0F)); // Rotar 180° en X
        VertexConsumer vertexconsumer = pBuffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(pEntity)));
        model.renderToBuffer(pPoseStack, vertexconsumer, pPackedLight, OverlayTexture.NO_OVERLAY, 0.6F, 0.8F, 1.0F, 0.7F);
        pPoseStack.popPose();
        super.render(pEntity, pEntityYaw, pPartialTicks, pPoseStack, pBuffer, pPackedLight);
    }

}
