package net.juli2kapo.minewinx.client.gui;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.client.ClientHudState;
import net.juli2kapo.minewinx.powers.EnumPowers;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HUD de poderes (arriba-izquierda): barra tematizada por elemento que
 * evoluciona con el stage + iconos de los 3 poderes + teclas + cooldowns.
 */
public class PowerHudOverlay {

    private static final int BAR_X = 4;
    private static final int BAR_Y = 4;
    private static final int BAR_W = 134;
    private static final int BAR_H = 44;
    // Coordenadas de los interiores de sockets (reportadas por el generador del arte)
    private static final int[][] SOCKETS = {{44, 15}, {70, 15}, {96, 15}};

    private static final Map<String, ResourceLocation> BAR_CACHE = new HashMap<>();
    private static final Map<String, ResourceLocation> ICON_CACHE = new HashMap<>();

    public static final IGuiOverlay HUD_POWERS = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) return;

        String element = ClientHudState.getElement();
        int stage = ClientHudState.getStage();
        if (element.isEmpty() || stage <= 0) return;
        EnumPowers.Element el = EnumPowers.Element.fromName(element);
        if (el == EnumPowers.Element.UNKNOWN) return;

        int clampedStage = Math.min(3, Math.max(1, stage));
        ResourceLocation bar = BAR_CACHE.computeIfAbsent(el.getName() + "_" + clampedStage,
                key -> new ResourceLocation(MineWinx.MOD_ID, "textures/gui/hud/" + key + ".png"));
        graphics.blit(bar, BAR_X, BAR_Y, 0, 0, BAR_W, BAR_H, BAR_W, BAR_H);

        // Orden visual = orden de teclas (Z, X, C de izquierda a derecha).
        // Flora dispara slots 2/3/1 con esas teclas, así que se muestran así.
        boolean floraOrder = el == EnumPowers.Element.NATURE;
        int[] slotForSocket = floraOrder ? new int[]{2, 3, 1} : new int[]{1, 2, 3};

        for (int socket = 0; socket < 3; socket++) {
            int slot = slotForSocket[socket];
            int sx = BAR_X + SOCKETS[socket][0] + 1;
            int sy = BAR_Y + SOCKETS[socket][1] + 1;

            EnumPowers power = EnumPowers.getPower(el, slot);
            if (power == EnumPowers.UNKNOWN) continue; // socket vacío (p. ej. Storm slot 3)

            // Flora: el socket 3 muestra la PLANTA seleccionada (lo que vas a plantar)
            ResourceLocation icon;
            if (power == EnumPowers.PLANT_SEED && !ClientHudState.getSelectedPlant().isEmpty()) {
                icon = ICON_CACHE.computeIfAbsent("plant:" + ClientHudState.getSelectedPlant().toLowerCase(Locale.ROOT),
                        key -> new ResourceLocation(MineWinx.MOD_ID, "textures/gui/plants/" + key.substring(6) + ".png"));
            } else {
                icon = ICON_CACHE.computeIfAbsent(power.name().toLowerCase(Locale.ROOT),
                        key -> new ResourceLocation(MineWinx.MOD_ID, "textures/gui/powers/" + key + ".png"));
            }
            graphics.blit(icon, sx, sy, 0, 0, 16, 16, 16, 16);

            // Cooldown: barrido oscuro de arriba hacia abajo + segundos
            float fraction = ClientHudState.cooldownFraction(slot);
            if (fraction > 0) {
                int sweep = Math.round(16 * fraction);
                graphics.fill(sx, sy, sx + 16, sy + sweep, 0xB0100408);
                String seconds = String.valueOf(ClientHudState.cooldownSecondsLeft(slot));
                int tw = minecraft.font.width(seconds);
                graphics.drawString(minecraft.font, seconds, sx + 8 - tw / 2, sy + 4, 0xFFF3C4, true);
            }

            // Con el orden visual por teclas, el socket i siempre se dispara con
            // la tecla física i (Z, X, C de izquierda a derecha). Respeta rebinds.
            net.minecraft.client.KeyMapping mapping = switch (socket) {
                case 0 -> net.juli2kapo.minewinx.client.KeyBindings.USE_POWER_KEY1;
                case 1 -> net.juli2kapo.minewinx.client.KeyBindings.USE_POWER_KEY2;
                default -> net.juli2kapo.minewinx.client.KeyBindings.USE_POWER_KEY3;
            };
            String key = mapping.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
            if (key.length() > 1) key = key.substring(0, 1);
            graphics.drawString(minecraft.font, key, sx + 12, sy + 11, 0xFFF3C4, true);
        }
    };
}
