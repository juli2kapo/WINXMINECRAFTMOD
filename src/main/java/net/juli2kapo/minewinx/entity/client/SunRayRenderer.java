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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class SunRayRenderer extends EntityRenderer<SunRay> {
    public SunRayRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    public void render(SunRay pEntity, float pEntityYaw, float pPartialTicks, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
        VertexConsumer vertexconsumer = pBuffer.getBuffer(RenderType.lightning());

        // Get sun/moon angle based on time of day
        float celestialAngle = pEntity.level().getSunAngle(pPartialTicks);

        // Calculate sun/moon position
        // The sun rises at angle 0, is overhead at 0.25, sets at 0.5, and the moon follows from 0.5 to 1.0
        float angle = celestialAngle * Mth.TWO_PI;

        // Sun/moon travels in an arc from east to west
        // At angle 0: sunrise (east)
        // At angle PI/2: noon (overhead)
        // At angle PI: sunset (west)
        // At angle 3PI/2: midnight (below)

        // Calculate direction vector from celestial body to ground
        float sunX = -Mth.sin(angle);  // East-West component
        float sunY = Mth.cos(angle);    // Vertical component
        float sunZ = 0.0F;               // North-South (keeping it simple, no seasonal tilt)

        // Normalize the direction vector
        float length = Mth.sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ);
        sunX /= length;
        sunY /= length;
        sunZ /= length;

        // Only render if celestial body is above horizon (sunY > 0)
        // Or allow moon rays at night
        boolean isDay = celestialAngle < 0.25F || celestialAngle > 0.75F;
        boolean isNight = celestialAngle >= 0.25F && celestialAngle <= 0.75F;

        pPoseStack.pushPose();

        // Calculate rotation to align cylinder with sun/moon direction
        if (sunY < 0.999F && sunY > -0.999F) {  // Avoid gimbal lock
            // Calculate rotation axis (cross product of up vector and sun direction)
            Vector3f up = new Vector3f(0, 1, 0);
            Vector3f sunDir = new Vector3f(sunX, sunY, sunZ);
            Vector3f rotAxis = new Vector3f(
                    up.y * sunDir.z - up.z * sunDir.y,
                    up.z * sunDir.x - up.x * sunDir.z,
                    up.x * sunDir.y - up.y * sunDir.x
            );

            // Calculate rotation angle
            float rotAngle = (float) Math.acos(Mth.clamp(sunY, -1.0F, 1.0F));

            // Apply rotation if needed
            if (rotAxis.lengthSquared() > 0.001F) {
                rotAxis.normalize();
                pPoseStack.mulPose(com.mojang.math.Axis.of(rotAxis).rotationDegrees(Mth.RAD_TO_DEG * rotAngle));
            }
        }

        Matrix4f matrix4f = pPoseStack.last().pose();

        float lifeFactor = (float) pEntity.tickCount + pPartialTicks;
        float alpha = Mth.clamp(1.0F - lifeFactor / 20.0F, 0.0F, 1.0F) * 0.5F;
        float radius = 1.5F;
        int segments = 12;
        float height = 256.0F; // Length of the ray

        // Adjust color based on whether it's sun or moon
        float red = isDay ? 1.0F : 0.9F;
        float green = isDay ? 1.0F : 0.9F;
        float blue = isDay ? 0.75F : 1.0F;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) i / segments * Mth.TWO_PI;
            float angle2 = (float) (i + 1) / segments * Mth.TWO_PI;

            float x1 = Mth.cos(angle1) * radius;
            float z1 = Mth.sin(angle1) * radius;
            float x2 = Mth.cos(angle2) * radius;
            float z2 = Mth.sin(angle2) * radius;

            // Render cylinder face
            vertex(matrix4f, vertexconsumer, x1, 0, z1, red, green, blue, alpha);
            vertex(matrix4f, vertexconsumer, x2, 0, z2, red, green, blue, alpha);
            vertex(matrix4f, vertexconsumer, x2, height, z2, red, green, blue, alpha);
            vertex(matrix4f, vertexconsumer, x1, height, z1, red, green, blue, alpha);
        }

        pPoseStack.popPose();
    }

    private void vertex(Matrix4f pMatrix, VertexConsumer pConsumer, float pX, float pY, float pZ, float pRed, float pGreen, float pBlue, float pAlpha) {
        pConsumer.vertex(pMatrix, pX, pY, pZ).color(pRed, pGreen, pBlue, pAlpha).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SunRay pEntity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}