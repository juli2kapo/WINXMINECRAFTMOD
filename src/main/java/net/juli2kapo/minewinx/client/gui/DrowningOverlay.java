package net.juli2kapo.minewinx.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.juli2kapo.minewinx.effect.ModEffects; // Asegúrate de que esta sea la clase donde registras tus efectos
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class DrowningOverlay {
    private static final ResourceLocation WATER_OVERLAY = new ResourceLocation("textures/misc/water_overlay.png");

    public static final IGuiOverlay HUD_DROWNING = (gui, guiGraphics, partialTicks, screenWidth, screenHeight) -> {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.hasEffect(ModEffects.DROWNING_TARGET.get()) && !client.player.isCreative()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(0.2F, 0.5F, 1.0F, 0.4F); // Tinte azul con transparencia
            RenderSystem.setShaderTexture(0, WATER_OVERLAY);

            //guiGraphics.blit(WATER_OVERLAY, 0, 0, -90, 0.0F, 0.0F, screenWidth, screenHeight, screenWidth, screenHeight);
            guiGraphics.blit(WATER_OVERLAY, 0, 0, -90, 0.0F, 0.0F, screenWidth, screenHeight, 16, 16);

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    };
}