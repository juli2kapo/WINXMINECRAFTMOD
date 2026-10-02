package net.juli2kapo.factoryascent.dyson.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.dyson.DysonPayloads;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Dyson Monitor's screen: a live preview of the team's cube turning around its star, the
 * completion, collectors in orbit, the power the swarm beams down and what the team's receivers
 * take, and the milestones. The server sends the numbers ({@link DysonPayloads.MonitorView}); the
 * screen asks for fresh ones every second.
 */
public class DysonScreen extends Screen {
    private static final int W = 330, H = 208;
    private static final int PREVIEW = 150;
    private static final int OUTLINE = 0xFF000000, BG = 0xFF14161E, INSET = 0xFF07080C, EDGE_LIGHT = 0xFF363C4C,
            EDGE_DARK = 0xFF06070A, ACCENT = 0xFF40E0F0, TEXT = 0xFFE4E8F0, MUTED = 0xFF8890A0, GOLD = 0xFFF0C050;
    private static final int[] MILESTONE_PERCENT = {0, 10, 25, 50, 100};
    private static final String[] MILESTONE_KEYS = {"first", "10", "25", "50", "100"};

    private DysonPayloads.MonitorView view;
    private final int[] canvas = new int[PREVIEW * PREVIEW];
    private DynamicTexture texture;
    private long lastRaster;
    private int ticks;

    public DysonScreen(DysonPayloads.MonitorView view) {
        super(Component.translatable("block.factoryascent.dyson_monitor"));
        this.view = view;
    }

    public BlockPos pos() {
        return view.pos();
    }

    public void update(DysonPayloads.MonitorView view) {
        this.view = view;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) ClientPacketDistributor.sendToServer(new DysonPayloads.MonitorRequest(view.pos()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        g.fill(x + 1, y, x + W - 1, y + H, OUTLINE);
        g.fill(x, y + 1, x + W, y + H - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, BG);
        g.fill(x + 1, y + 1, x + W - 2, y + 2, EDGE_LIGHT);
        g.fill(x + 1, y + 1, x + 2, y + H - 2, EDGE_LIGHT);
        g.fill(x + 2, y + H - 2, x + W - 1, y + H - 1, EDGE_DARK);
        g.fill(x + W - 2, y + 2, x + W - 1, y + H - 1, EDGE_DARK);
        g.fill(x + 3, y + 3, x + W - 3, y + 4, ACCENT);
        int px = x + 8, py = y + 22;
        g.fill(px - 1, py - 1, px + PREVIEW + 1, py + PREVIEW + 1, EDGE_LIGHT);
        // The preview is rasterised into a texture (at most ~20 times a second) and drawn with one
        // blit: drawing it pixel by pixel as GUI rectangles meant tens of thousands per frame.
        long now = System.nanoTime();
        if (texture == null || now - lastRaster > 50_000_000L) {
            lastRaster = now;
            raster(partial);
        }
        g.blit(texture.getTextureView(), texture.getSampler(), px, py, px + PREVIEW, py + PREVIEW, 0f, 1f, 0f, 1f);
    }

    @Override
    public void removed() {
        super.removed();
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    private void raster(float partial) {
        if (texture == null) texture = new DynamicTexture(() -> "factoryascent dyson preview", PREVIEW, PREVIEW, true);
        java.util.Arrays.fill(canvas, INSET);
        // a few fixed stars behind the cube
        for (int i = 0; i < 60; i++) {
            int sx = (int) (DysonShape.hash(i * 3L) * PREVIEW), sy = (int) (DysonShape.hash(i * 3L + 1) * PREVIEW);
            int c = 0x40 + (int) (DysonShape.hash(i * 3L + 2) * 0x80);
            fill(sx, sy, sx + 1, sy + 1, 0xFF000000 | c << 16 | c << 8 | c);
        }
        preview(PREVIEW / 2, PREVIEW / 2, partial);
        NativeImage image = texture.getPixels();
        for (int y = 0; y < PREVIEW; y++) {
            for (int x = 0; x < PREVIEW; x++) image.setPixel(x, y, canvas[y * PREVIEW + x]);
        }
        texture.upload();
    }

    /** Fills a rectangle of the preview canvas, blending by the colour's alpha. */
    private void fill(int x0, int y0, int x1, int y1, int argb) {
        int a = argb >>> 24;
        if (a == 0) return;
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(PREVIEW, x1); y1 = Math.min(PREVIEW, y1);
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int i = y * PREVIEW + x;
                canvas[i] = a == 255 ? argb : 0xFF000000 | mix(canvas[i] & 0xFFFFFF, argb & 0xFFFFFF, a / 255f);
            }
        }
    }

    // ---------------------------------------------------------------- the preview

    private record Prim(float depth, int kind, float[] pts, int color) {}

    /** Draws the cube, depth-sorted (painter's algorithm), in pixel art. */
    private void preview(int cx, int cy, float partial) {
        float time = (ticks + partial) / 20f;
        float turn = time * 0.25f;
        float ct = (float) Math.cos(turn), st = (float) Math.sin(turn);
        float scale = PREVIEW / 2f / 4.9f;
        List<Prim> prims = new ArrayList<>();
        DysonShape.build(view.collectors(), view.target(), time * 4f, new DysonShape.Sink() {
            private float[] p(float x, float y, float z) {
                // turn around the screen's vertical axis (z), view along y
                float rx = x * ct - y * st, depth = x * st + y * ct;
                return new float[] {cx + rx * scale, cy - z * scale, depth};
            }

            @Override
            public void collector(float x, float y, float z, float phase) {
                float[] a = p(x, y, z);
                float tw = (float) Math.pow(Math.max(0, Math.sin(time * 2 + phase * 40)), 16);
                int c = tw > 0.4f ? 0xFFFFFFFF : 0xFFFFE6B0;
                prims.add(new Prim(a[2], 0, a, c));
            }

            @Override
            public void panel(float[] c, float shade) {
                float[] a = p(c[0], c[1], c[2]), b = p(c[3], c[4], c[5]), d = p(c[6], c[7], c[8]), e = p(c[9], c[10], c[11]);
                float depth = (a[2] + b[2] + d[2] + e[2]) / 4;
                int color = depth > 0 ? 0xFFB05A20 : 0xFF000000 | mix(0x2A2630, 0x4A4452, shade);
                prims.add(new Prim(depth, 1, new float[] {a[0], a[1], b[0], b[1], d[0], d[1], e[0], e[1]}, color));
            }

            @Override
            public void line(float ax, float ay, float az, float bx, float by, float bz, int kind, float alpha) {
                float[] a = p(ax, ay, az), b = p(bx, by, bz);
                int base = kind == DysonShape.TRACK ? 0x503A2A : kind == DysonShape.SEAM ? 0xFFC060 : 0xC08040;
                float k = kind == DysonShape.TRACK ? 1f : alpha;
                if ((a[2] + b[2]) / 2 > 0) k *= 0.45f;
                int color = 0xFF000000 | mix(0x07080C, base, Math.min(1f, k));
                prims.add(new Prim((a[2] + b[2]) / 2 - 0.01f, 2, new float[] {a[0], a[1], b[0], b[1]}, color));
            }
        }, 700, true, 36);
        prims.add(new Prim(0.3f, 3, new float[] {cx, cy}, 0));
        prims.sort((a, b) -> Float.compare(b.depth(), a.depth()));
        for (Prim prim : prims) {
            float[] q = prim.pts();
            switch (prim.kind()) {
                case 0 -> fill((int) q[0], (int) q[1], (int) q[0] + 1, (int) q[1] + 1, prim.color());
                case 1 -> fillQuad(q, prim.color());
                case 2 -> dotted(q[0], q[1], q[2], q[3], prim.color());
                default -> sun((int) q[0], (int) q[1], time);
            }
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gg = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | gg << 8 | bl;
    }

    private void sun(int cx, int cy, float time) {
        int r = 7;
        fill(cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3, 0x40FFB030);
        fill(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, 0xA0FFC040);
        fill(cx - r, cy - r, cx + r, cy + r, 0xFFFFE070);
        int f = (int) (2 + Math.sin(time * 3) * 1.5);
        fill(cx - r + 2, cy - r + 2, cx + r - 2 - f, cy + r - 2 - f, 0xFFFFF6C0);
    }

    private void dotted(float x0, float y0, float x1, float y1, int color) {
        float len = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int n = Math.max(1, (int) len);
        for (int i = 0; i <= n; i++) {
            int x = (int) (x0 + (x1 - x0) * i / n), y = (int) (y0 + (y1 - y0) * i / n);
            fill(x, y, x + 1, y + 1, color);
        }
    }

    /** Fills a convex quad (screen points x0 y0 … x3 y3) row by row. */
    private void fillQuad(float[] q, int color) {
        float minY = Math.min(Math.min(q[1], q[3]), Math.min(q[5], q[7]));
        float maxY = Math.max(Math.max(q[1], q[3]), Math.max(q[5], q[7]));
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++) {
            float sy = y + 0.5f, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int e = 0; e < 4; e++) {
                float ax = q[e * 2], ay = q[e * 2 + 1], bx = q[(e * 2 + 2) % 8], by = q[(e * 2 + 3) % 8];
                if ((ay <= sy && by > sy) || (by <= sy && ay > sy)) {
                    float x = ax + (sy - ay) / (by - ay) * (bx - ax);
                    lo = Math.min(lo, x);
                    hi = Math.max(hi, x);
                }
            }
            if (hi >= lo) fill(Math.round(lo), y, Math.max(Math.round(lo) + 1, Math.round(hi)), y + 1, color);
        }
    }

    // ---------------------------------------------------------------- text

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x = left(), y = top();
        g.text(font, title, x + 9, y + 9, ACCENT, false);
        Component team = Component.translatable("gui.factoryascent.dyson.team", view.team());
        g.text(font, team, x + W - 9 - font.width(team), y + 9, MUTED, false);
        int tx = x + PREVIEW + 18, ty = y + 22, tw = W - PREVIEW - 26;
        double completion = view.target() <= 0 ? 0 : Math.min(1.0, view.collectors() / (double) view.target());
        Component pct = Component.literal(String.format("%.1f%%", completion * 100)).withStyle(ChatFormatting.BOLD);
        g.text(font, Component.translatable("gui.factoryascent.dyson.completion"), tx, ty, MUTED, false);
        g.text(font, pct, tx + tw - font.width(pct), ty, completion >= 1 ? 0xFFE080FF : GOLD, false);
        ty += 11;
        g.fill(tx, ty, tx + tw, ty + 7, EDGE_DARK);
        g.fill(tx + 1, ty + 1, tx + tw - 1, ty + 6, INSET);
        int filled = (int) Math.round((tw - 2) * completion);
        if (filled > 0) g.fillGradient(tx + 1, ty + 1, tx + 1 + filled, ty + 6, 0xFFFFD060, 0xFFE07020);
        for (int m = 1; m < MILESTONE_PERCENT.length - 1; m++) {
            int mx = tx + 1 + (tw - 2) * MILESTONE_PERCENT[m] / 100;
            g.fill(mx, ty, mx + 1, ty + 7, 0xFF8090A0);
        }
        ty += 12;
        row(g, tx, ty, tw, "collectors", view.collectors() + " / " + view.target());
        ty += 11;
        row(g, tx, ty, tw, "launched", Long.toString(view.launched()));
        ty += 11;
        row(g, tx, ty, tw, "potential", EnergyUtil.format(view.potential()) + " FE/t");
        ty += 11;
        row(g, tx, ty, tw, "received", EnergyUtil.format(view.received()) + " FE/t");
        ty += 15;
        g.text(font, Component.translatable("gui.factoryascent.dyson.milestones"), tx, ty, ACCENT, false);
        ty += 11;
        for (int m = 0; m < MILESTONE_KEYS.length; m++) {
            boolean done = (view.milestones() & (1 << m)) != 0;
            Component line = Component.translatable("gui.factoryascent.dyson.milestone." + MILESTONE_KEYS[m]);
            g.text(font, done ? "✔" : "•", tx, ty, done ? 0xFF60E070 : MUTED, false);
            g.text(font, line, tx + 9, ty, done ? TEXT : MUTED, false);
            ty += 10;
        }
        Component hint = view.collectors() <= 0 ? Component.translatable("gui.factoryascent.dyson.hint_empty")
                : completion >= 1 ? Component.translatable("gui.factoryascent.dyson.hint_complete")
                : Component.translatable("gui.factoryascent.dyson.hint");
        g.textWithWordWrap(font, hint, x + 9, y + H - 25, W - 18, MUTED, false);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    private void row(GuiGraphicsExtractor g, int x, int y, int w, String key, String value) {
        g.text(font, Component.translatable("gui.factoryascent.dyson." + key), x, y, MUTED, false);
        g.text(font, value, x + w - font.width(value), y, TEXT, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
}
