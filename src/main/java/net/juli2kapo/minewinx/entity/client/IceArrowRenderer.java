// src/main/java/net/juli2kapo/minewinx/entity/client/IceArrowRenderer.java
package net.juli2kapo.minewinx.entity.client;

import net.juli2kapo.minewinx.entity.IceArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class IceArrowRenderer extends ArrowRenderer<IceArrowEntity> {
    public IceArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(IceArrowEntity entity) {
        // Usa tu textura personalizada
        return new ResourceLocation("minewinx", "textures/entity/ice_arrow.png");
    }
}