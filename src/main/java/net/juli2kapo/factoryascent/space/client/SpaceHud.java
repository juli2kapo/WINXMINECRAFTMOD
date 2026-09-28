package net.juli2kapo.factoryascent.space.client;

import net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity;
import net.juli2kapo.factoryascent.space.Jetpack;
import net.juli2kapo.factoryascent.space.RocketSeatEntity;
import net.juli2kapo.factoryascent.space.SpaceConfig;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.juli2kapo.factoryascent.space.SuitItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Space HUD, bottom left: the suit's air gauge (in airless places, or while wearing the suit) and
 * the jetpack's charge with its hover mode. Without air: a pulsing red vignette and a warning in
 * the middle of the screen. In a rocket: countdown, altitude, and the sky going black as it climbs.
 * Re-entry: an orange glow at the edges while the screen shakes.
 */
final class SpaceHud {
    private SpaceHud() {}

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        long time = mc.level.getGameTime();

        climb(g, font, player, w, h, partial);
        if (SpaceClient.reentryTicks > 0) {
            float a = Math.min(1f, SpaceClient.reentryTicks / (float) SpaceClient.reentryLength + 0.2f);
            vignette(g, w, h, 0xFF6A10, a * (0.55f + 0.15f * Mth.sin(time * 0.9f)));
        }

        boolean airless = SpaceRules.isAirless(mc.level);
        SpaceRules.Breath breath = breath(player, airless, time);
        int y = h - 14;
        ItemStack tank = SpaceRules.suitTank(player);
        if (!tank.isEmpty() || airless) {
            int oxygen = SuitItems.oxygen(tank);
            float f = oxygen / (float) Math.max(1, SpaceConfig.suitOxygen());
            int color = tank.isEmpty() || f < 0.2f ? 0xFFE04040 : 0xFF40C8FF;
            g.text(font, Component.translatable("hud.factoryascent.air"), 6, y - 1, 0xFFE0F4FF, true);
            bar(g, 26, y, 62, f, color);
            String label = tank.isEmpty() ? "--" : String.format("%d:%02d", oxygen / 20 / 60, oxygen / 20 % 60);
            g.text(font, label, 92, y - 1, 0xFFE0F4FF, true);
            Component where = switch (breath) {
                case BUBBLE -> Component.translatable("hud.factoryascent.breath_bubble");
                case CABIN -> Component.translatable("hud.factoryascent.breath_cabin");
                default -> null;
            };
            if (where != null) g.text(font, where, 122, y - 1, 0xFF80FFB0, true);
            y -= 12;
        }
        ItemStack pack = Jetpack.worn(player);
        if (!pack.isEmpty()) {
            Jetpack.Tier tier = Jetpack.tier(pack);
            float f = Jetpack.energy(pack) / (float) tier.capacity;
            g.text(font, Component.translatable("hud.factoryascent.jet"), 6, y - 1, 0xFFFFF0C0, true);
            bar(g, 26, y, 62, f, f < 0.15f ? 0xFFE04040 : 0xFFF0C040);
            g.text(font, Math.round(f * 100) + "%", 92, y - 1, 0xFFFFF0C0, true);
            if (Jetpack.hover(pack)) g.text(font, Component.translatable("hud.factoryascent.hover"), 122, y - 1, 0xFF80E0FF, true);
        }

        if (airless && breath == SpaceRules.Breath.NONE && !player.isCreative() && !player.isSpectator()) {
            float pulse = 0.55f + 0.35f * Mth.sin((time + partial) * 0.35f);
            vignette(g, w, h, 0xE01010, pulse);
            g.pose().pushMatrix();
            g.pose().translate(w / 2f, h / 2f - 46);
            g.pose().scale(2f, 2f);
            g.centeredText(font, Component.translatable("hud.factoryascent.no_air"), 0, 0, 0xFFFF4040);
            g.pose().popMatrix();
            g.centeredText(font, Component.translatable("hud.factoryascent.no_air_hint"), w / 2, h / 2 - 22, 0xFFFFD0D0);
        } else if (airless && breath == SpaceRules.Breath.SUIT && SuitItems.oxygen(tank) < 30 * 20) {
            if ((time / 10) % 2 == 0) g.centeredText(font, Component.translatable("hud.factoryascent.air_low"), w / 2, h / 2 - 30, 0xFFFFB040);
        }
    }

    /** The server's word on how we breathe (fresh within 1.5 s), else a local guess from the suit. */
    private static SpaceRules.Breath breath(LocalPlayer player, boolean airless, long time) {
        if (!airless) return SpaceRules.Breath.AIR;
        if (SpaceClient.breath >= 0 && time - SpaceClient.breathAt < 30 && SpaceClient.breath < SpaceRules.Breath.values().length) {
            return SpaceRules.Breath.values()[SpaceClient.breath];
        }
        if (SpaceRules.inSealedCabin(player)) return SpaceRules.Breath.CABIN;
        return SpaceRules.wearsFullSuit(player) && SuitItems.oxygen(SpaceRules.suitTank(player)) > 0
                ? SpaceRules.Breath.SUIT : SpaceRules.Breath.NONE;
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int width, float fraction, int color) {
        g.fill(x - 1, y - 1, x + width + 1, y + 7, 0xC0000000);
        g.fill(x, y, x + width, y + 6, 0xFF1A2230);
        int filled = Math.round(width * Mth.clamp(fraction, 0f, 1f));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + 6, color);
            g.fill(x, y, x + filled, y + 2, 0x40FFFFFF);
        }
    }

    /** Coloured edges fading inwards. */
    private static void vignette(GuiGraphicsExtractor g, int w, int h, int rgb, float alpha) {
        int steps = 12, band = Math.max(3, Math.min(w, h) / 40);
        for (int i = 0; i < steps; i++) {
            int a = Math.round(255 * alpha * (1f - i / (float) steps) * 0.55f);
            if (a <= 0) continue;
            int c = (a << 24) | (rgb & 0xFFFFFF);
            int o = i * band;
            g.fill(o, o, w - o, o + band, c);
            g.fill(o, h - o - band, w - o, h - o, c);
            g.fill(o, o + band, o + band, h - o - band, c);
            g.fill(w - o - band, o + band, w - o, h - o - band, c);
        }
    }

    /** In a rocket: countdown, then altitude, the sky going black near the top. */
    private static void climb(GuiGraphicsExtractor g, Font font, LocalPlayer player, int w, int h, float partial) {
        if (!(player.getVehicle() instanceof RocketSeatEntity seat)) return;
        if (!(player.level().getBlockEntity(seat.pad()) instanceof LaunchControllerBlockEntity pad) || !pad.launching()) return;
        int tick = pad.launchTick();
        if (tick < LaunchControllerBlockEntity.LIFTOFF) {
            int seconds = (LaunchControllerBlockEntity.LIFTOFF - tick + 19) / 20;
            g.pose().pushMatrix();
            g.pose().translate(w / 2f, 30);
            g.pose().scale(2f, 2f);
            g.centeredText(font, Component.translatable("hud.factoryascent.t_minus", seconds), 0, 0, 0xFFFFD040);
            g.pose().popMatrix();
            g.centeredText(font, Component.translatable("hud.factoryascent.climb_out"), w / 2, 52, 0xFFC0C0C0);
            return;
        }
        double height = RocketSeatEntity.climb(pad, partial);
        float dark = (float) Mth.clamp((height - 30) / 320.0, 0, 0.9);
        if (dark > 0) g.fill(0, 0, w, h, ((int) (dark * 255) << 24));
        g.centeredText(font, Component.translatable("hud.factoryascent.altitude", (int) height), w / 2, 20, 0xFFE0F0FF);
    }
}
