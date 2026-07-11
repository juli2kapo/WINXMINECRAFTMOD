package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.IceCrystalEntity;
import net.juli2kapo.minewinx.entity.client.model.IceCrystalModel;
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
    public void render(IceCrystalEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pPoseStack,
                       MultiBufferSource pBuffer, int pPackedLight) {
        pPoseStack.pushPose();
        // Convención estándar de modelos de entidad (espacio Y-abajo, suelo en 24px)
        pPoseStack.scale(-1.0F, -1.0F, 1.0F);
        pPoseStack.translate(0.0D, -1.501D, 0.0D);
        // Escala sincronizada según el tamaño de la víctima
        float s = pEntity.getVisualScale();
        pPoseStack.scale(s, s, s);

        this.model.setupAnim(pEntity, 0.0F, 0.0F, pEntity.tickCount + pPartialTicks, 0.0F, 0.0F);
        // Cull de caras traseras: sin él, cada cara interna se ve a través de la
        // transparencia y el cristal se ve "roto"
        VertexConsumer vertexconsumer = pBuffer.getBuffer(RenderType.entityTranslucentCull(getTextureLocation(pEntity)));
        this.model.renderToBuffer(pPoseStack, vertexconsumer, pPackedLight, OverlayTexture.NO_OVERLAY, 0.65F, 0.85F, 1.0F, 0.65F);
        pPoseStack.popPose();
        super.render(pEntity, pEntityYaw, pPartialTicks, pPoseStack, pBuffer, pPackedLight);
    }
}
