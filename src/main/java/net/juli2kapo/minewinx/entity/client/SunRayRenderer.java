package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.entity.SunRay;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class SunRayRenderer extends EntityRenderer<SunRay> {
    public SunRayRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    public void render(SunRay pEntity, float pEntityYaw, float pPartialTicks, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
        VertexConsumer vertexconsumer = pBuffer.getBuffer(RenderType.lightning());
        Matrix4f matrix4f = pPoseStack.last().pose();

        float lifeFactor = (float) pEntity.tickCount + pPartialTicks;
        float alpha = Mth.clamp(1.0F - lifeFactor / 20.0F, 0.0F, 1.0F) * 0.5F; // Desvanecimiento
        float radius = 1.5F;
        int segments = 12;
        float height = 256.0F; // Altura del rayo

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) i / segments * Mth.TWO_PI;
            float angle2 = (float) (i + 1) / segments * Mth.TWO_PI;

            float x1 = Mth.cos(angle1) * radius;
            float z1 = Mth.sin(angle1) * radius;
            float x2 = Mth.cos(angle2) * radius;
            float z2 = Mth.sin(angle2) * radius;

            // Cara del cilindro
            vertex(matrix4f, vertexconsumer, x1, 0, z1, 1.0F, 1.0F, 0.75F, alpha);
            vertex(matrix4f, vertexconsumer, x2, 0, z2, 1.0F, 1.0F, 0.75F, alpha);
            vertex(matrix4f, vertexconsumer, x2, height, z2, 1.0F, 1.0F, 0.75F, alpha);
            vertex(matrix4f, vertexconsumer, x1, height, z1, 1.0F, 1.0F, 0.75F, alpha);
        }
    }

    private void vertex(Matrix4f pMatrix, VertexConsumer pConsumer, float pX, float pY, float pZ, float pRed, float pGreen, float pBlue, float pAlpha) {
        pConsumer.vertex(pMatrix, pX, pY, pZ).color(pRed, pGreen, pBlue, pAlpha).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SunRay pEntity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}