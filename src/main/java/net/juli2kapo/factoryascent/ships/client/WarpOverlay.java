package net.juli2kapo.factoryascent.ships.client;

import net.juli2kapo.factoryascent.ships.Shuttle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The cruise between worlds: while the shuttle is cruising the view fills with stars streaking out
 * from the middle of the screen, faster and longer in the middle of the trip, over a deep blue-black
 * that fades in and out at the ends. The destination and progress are written across the top.
 */
final class WarpOverlay {
    private static final int STARS = 180;
    private static final float[] ANGLE = new float[STARS], SEED = new float[STARS], SPEED = new float[STARS];
    private static final int[] COLOR = new int[STARS];

    static {
        RandomSource r = RandomSource.create(4242L);
        for (int i = 0; i < STARS; i++) {
            ANGLE[i] = r.nextFloat() * Mth.TWO_PI;
            SEED[i] = r.nextFloat();
            SPEED[i] = 0.35f + r.nextFloat() * 0.9f;
            int tint = r.nextInt(5);
            COLOR[i] = tint == 0 ? 0xFFB0C8FF : tint == 1 ? 0xFFFFE0C0 : 0xFFFFFFFF;
        }
    }

    private WarpOverlay() {}

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!(ShipsClient.riding() instanceof Shuttle shuttle) || !shuttle.cruising() || mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float progress = shuttle.cruiseProgress();
        float fade = Math.min(1f, Math.min(progress, 1f - progress) * 8f + 0.15f);
        int bg = (int) (fade * 0xD0) << 24 | 0x04061A;
        g.fill(0, 0, w, h, bg);
        float time = (mc.player.tickCount + delta.getGameTimeDeltaPartialTick(false)) / 20f;
        float warp = 0.4f + 1.6f * Mth.sin(Mth.PI * progress); // fastest mid-trip
        float cx = w / 2f, cy = h / 2f, maxR = (float) Math.hypot(cx, cy);
        for (int i = 0; i < STARS; i++) {
            float t = (SEED[i] + time * SPEED[i] * warp * 0.5f) % 1f;
            float r0 = t * t * maxR;
            float len = 2 + t * t * 38 * warp;
            float dx = Mth.cos(ANGLE[i]), dy = Mth.sin(ANGLE[i]);
            int alpha = (int) (Math.min(1f, t * 3f) * 255 * fade);
            int color = (alpha << 24) | (COLOR[i] & 0xFFFFFF);
            int steps = Math.max(1, (int) len);
            for (int s = 0; s < steps; s++) {
                int x = (int) (cx + dx * (r0 + s)), y = (int) (cy + dy * (r0 + s));
                if (x < 0 || y < 0 || x >= w || y >= h) break;
                int size = t > 0.7f ? 2 : 1;
                g.fill(x, y, x + size, y + size, color);
            }
        }
        var target = shuttle.cruiseTarget();
        if (target != null) {
            Component line = Component.translatable("gui.factoryascent.nav.cruising", target.displayName(), Math.round(progress * 100));
            g.centeredText(mc.font, line, w / 2, h / 2 - 40, 0xFFE0C8FF);
        }
    }
}
