package net.juli2kapo.factoryascent.orbital.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.locale.Language;

/** Rectangle-only drawing for the Orbital screens: a dark console panel with the Orbital age accent. */
final class OrbitalGui {
    static final int OUTLINE = 0xFF000000;
    static final int BG = 0xFF1B1E26;
    static final int INSET = 0xFF10131A;
    static final int EDGE_LIGHT = 0xFF3A4050;
    static final int EDGE_DARK = 0xFF0A0C10;
    /** Orbital age accent (same violet as the machine screens of the age). */
    static final int ACCENT = 0xFFB060E0;
    static final int TEXT = 0xFFE0E4EC;
    static final int MUTED = 0xFF8890A0;

    private OrbitalGui() {}

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, EDGE_LIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, EDGE_LIGHT);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, EDGE_DARK);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, EDGE_DARK);
        g.fill(x + 3, y + 3, x + w - 3, y + 4, ACCENT);
    }

    /** A recessed area (list background). */
    static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, EDGE_LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, INSET);
    }

    /** A horizontal bar filled to {@code fraction}. */
    static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x, y, x + w, y + h, EDGE_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, INSET);
        int filled = Math.round((w - 2) * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
    }

    /** Text cut to fit {@code width} pixels (with an ellipsis when cut). */
    static FormattedCharSequence fit(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        FormattedText cut = font.substrByWidth(text, width - font.width("…"));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
    }
}
