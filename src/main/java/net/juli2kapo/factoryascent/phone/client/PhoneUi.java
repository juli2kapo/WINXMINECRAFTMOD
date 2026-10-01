package net.juli2kapo.factoryascent.phone.client;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Drawing kit for the phone's screen: the flat dark "phone OS" look (cards, pill buttons, toggles,
 * bars, status dots) shared by every app view, so apps added later look like they belong.
 * Everything is in the phone's own coordinates (the screen sets up the transform).
 */
public final class PhoneUi {
    public static final int BG = 0xFF10141C;
    public static final int CARD = 0xFF1B2230;
    public static final int CARD_HI = 0xFF243044;
    public static final int LINE = 0xFF2C3850;
    public static final int TEXT = 0xFFE8EDF5;
    public static final int MUTED = 0xFF8A96AC;
    public static final int FAINT = 0xFF5A6478;
    public static final int GOOD = 0xFF4CD07A;
    public static final int WARN = 0xFFF0C040;
    public static final int BAD = 0xFFF05454;
    public static final int OFF = 0xFF6A7488;
    public static final int ACCENT = 0xFF3FA7E0;

    private PhoneUi() {}

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static Identifier tex(String path) {
        return Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "textures/gui/phone/" + path + ".png");
    }

    /** A rectangle with its corners clipped by one pixel (a "rounded" card at GUI scale). */
    public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** A more rounded rectangle (2px corners), for big shapes. */
    public static void round2(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 2, y, x + w - 2, y + h, color);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, color);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x, y + 2, x + 1, y + h - 2, color);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
    }

    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover) {
        round(g, x, y, w, h, hover ? CARD_HI : CARD);
    }

    /** A pill button with a centred label; returns nothing, hit-test with {@link #inside}. */
    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, int color, boolean hover, boolean enabled) {
        int c = !enabled ? 0xFF3A4254 : hover ? brighter(color) : color;
        round2(g, x, y, w, h, c);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, brighter(c));
        Font font = font();
        FormattedCharSequence text = fit(font, label, w - 4);
        g.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF8A93A6, false);
    }

    /** An on/off switch, 18x10. */
    public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        round2(g, x, y, 18, 10, on ? GOOD : 0xFF3A4254);
        int kx = on ? x + 9 : x + 1;
        round(g, kx, y + 1, 8, 8, 0xFFF4F6FA);
    }

    /** A thin progress bar on a dark track. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
        round(g, x, y, w, h, 0xFF0B0F16);
        int filled = Math.round((w - 2) * Math.max(0f, Math.min(1f, fraction)));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
            g.fill(x + 1, y + 1, x + 1 + filled, y + 2, brighter(color));
        }
    }

    /** A 5x5 status dot. */
    public static void dot(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 5, color);
        g.fill(x, y + 1, x + 5, y + 4, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, brighter(brighter(color)));
    }

    public static int levelColor(int level) {
        return switch (level) {
            case 0 -> GOOD;
            case 1 -> WARN;
            case 2 -> BAD;
            default -> OFF;
        };
    }

    /** An app's 24x24 icon. */
    public static void icon(GuiGraphicsExtractor g, String app, int x, int y, int alphaColor) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex("app/" + app), x, y, 0f, 0f, 24, 24, 24, 24, alphaColor);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void text(GuiGraphicsExtractor g, Component text, int x, int y, int maxWidth, int color) {
        g.text(font(), fit(font(), text, maxWidth), x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.text(font(), text, x, y, color, false);
    }

    /** Right-aligned at {@code right}. */
    public static void textRight(GuiGraphicsExtractor g, Component text, int right, int y, int color) {
        g.text(font(), text, right - font().width(text), y, color, false);
    }

    public static void centered(GuiGraphicsExtractor g, Component text, int cx, int y, int maxWidth, int color) {
        FormattedCharSequence t = fit(font(), text, maxWidth);
        g.text(font(), t, cx - font().width(t) / 2, y, color, false);
    }

    /** Word-wrapped lines (at most {@code maxLines}); returns the height used. */
    public static int wrapped(GuiGraphicsExtractor g, Component text, int x, int y, int width, int maxLines, int color) {
        var lines = font().split(text, width);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) g.text(font(), lines.get(i), x, y + i * 10, color, false);
        return n * 10;
    }

    /** Text cut to fit {@code width} pixels (with an ellipsis when cut). */
    public static FormattedCharSequence fit(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("…")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
    }

    /** "12.3k FE" style numbers. */
    public static String compact(long v) {
        long a = Math.abs(v);
        if (a >= 1_000_000_000L) return trim(v / 1e9) + "G";
        if (a >= 1_000_000L) return trim(v / 1e6) + "M";
        if (a >= 10_000L) return trim(v / 1e3) + "k";
        return Long.toString(v);
    }

    private static String trim(double d) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static int brighter(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 30);
        int gg = Math.min(255, ((argb >> 8) & 0xFF) + 30);
        int b = Math.min(255, (argb & 0xFF) + 30);
        return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }

    public static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /** World clock time as "HH:MM" (day starts at 06:00 like vanilla). */
    public static String clock(long ticks) {
        long t = Math.floorMod(ticks, 24000L);
        int hour = (int) ((t / 1000 + 6) % 24);
        int minute = (int) (t % 1000 * 60 / 1000);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute);
    }

    public static long day(long ticks) {
        return ticks / 24000L + 1;
    }
}
