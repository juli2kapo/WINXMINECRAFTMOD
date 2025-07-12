package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.juli2kapo.minewinx.entity.TsunamiEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public class TsunamiEntityRenderer extends EntityRenderer<TsunamiEntity> {

    public TsunamiEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TsunamiEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // No renderizar nada, ya que la entidad es invisible y solo maneja lógica y partículas.
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Nullable
    @Override
    public ResourceLocation getTextureLocation(TsunamiEntity entity) {
        return null; // No hay textura
    }
}