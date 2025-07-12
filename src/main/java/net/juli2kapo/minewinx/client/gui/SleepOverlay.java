package net.juli2kapo.minewinx.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.juli2kapo.minewinx.effect.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class SleepOverlay {
    public static final IGuiOverlay HUD_SLEEP = (gui, guiGraphics, partialTicks, screenWidth, screenHeight) -> {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.hasEffect(ModEffects.SLEEP.get()) && !client.player.isCreative()) {
            // Dibuja un rectángulo negro semitransparente que cubre toda la pantalla.
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            guiGraphics.fill(0, 0, screenWidth, screenHeight, 0xD0000000); // Color negro con transparencia
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    };
}