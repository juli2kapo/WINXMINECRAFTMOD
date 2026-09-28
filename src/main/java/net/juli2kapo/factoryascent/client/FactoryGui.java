package net.juli2kapo.factoryascent.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Rectangle-only drawing shared by the info/config screens (ender tech, pipes, cables, storage
 * network, drill): the same bevelled grey panel as {@link MachineScreen} with an accent stripe,
 * plus a dark "display" inset for read-outs, bars, slots and flat buttons.
 */
public final class FactoryGui {
    public static final int BG = 0xFFC6C6C6;
    public static final int LIGHT = 0xFFFFFFFF;
    public static final int SHADOW = 0xFF555555;
    public static final int OUTLINE = 0xFF000000;
    public static final int SLOT_DARK = 0xFF373737;
    public static final int SLOT_FILL = 0xFF8B8B8B;
    public static final int TEXT = 0xFF404040;
    public static final int MUTED = 0xFF707070;
    /** Text colours on a dark display inset. */
    public static final int DISPLAY = 0xFF15181D;
    public static final int DISPLAY_TEXT = 0xFFE4E8EE;
    public static final int DISPLAY_MUTED = 0xFF8C95A3;
    public static final int GOOD = 0xFF4CD04C;
    public static final int WARN = 0xFFE0C040;
    public static final int BAD = 0xFFE05050;

    /** Accents: ender tech (violet), electric age (yellow), automation (green), storage network (teal). */
    public static final int ENDER = 0xFF8E4FD6;
    public static final int ELECTRIC = 0xFFE0C040;
    public static final int AUTOMATION = 0xFF4CB050;
    public static final int STORAGE = 0xFF3FA7B5;

    private FactoryGui() {}

    /** The bevelled machine-screen panel with a 2px accent stripe along the top. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int accent) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + 3, y + 3, x + w - 3, y + 5, accent);
    }

    /** A sunken dark read-out area. */
    public static void display(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, DISPLAY);
    }

    /** A sunken light area (like a slot, any size). */
    public static void well(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_FILL);
    }

    /** An 18x18 slot whose item area starts at (x, y). */
    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DARK);
        g.fill(x, y, x + 17, y + 17, LIGHT);
        g.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    public static void playerInventory(GuiGraphicsExtractor g, int left, int top, int invY) {
        for (int i = 0; i < 27; i++) slot(g, left + 8 + (i % 9) * 18, top + invY + (i / 9) * 18);
        for (int i = 0; i < 9; i++) slot(g, left + 8 + i * 18, top + invY + 58);
    }

    /** A horizontal bar filled to {@code fraction} on a dark track. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2A2A2A);
        int filled = Math.round((w - 2) * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
            g.fill(x + 1, y + 1, x + 1 + filled, y + 2, brighter(color));
        }
    }

    /** The machine screens' energy bar, horizontal: red at the bottom end to yellow at the top. */
    public static void energyBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction) {
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2A1010);
        int inner = w - 2;
        int filled = Math.round(inner * Math.max(0, Math.min(1, fraction)));
        for (int i = 0; i < filled; i++) {
            float t = (float) i / inner;
            int gr = Math.min(255, (int) (0x30 + 0xB0 * t));
            g.fill(x + 1 + i, y + 1, x + 2 + i, y + h - 1, 0xFF000000 | (0xD0 << 16) | (gr << 8) | 0x20);
        }
    }

    /** A flat clickable button face; {@code selected} draws it pressed-in with the accent. */
    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean selected, int accent) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        if (selected) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, darker(accent));
            g.fill(x + 2, y + 2, x + w - 1, y + h - 1, accent);
        } else {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFFB8B8B8 : 0xFFA0A0A0);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFDADADA);
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF6A6A6A);
        }
    }

    /** A small round-ish status lamp. */
    public static void lamp(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 1, y, x + 6, y + 7, OUTLINE);
        g.fill(x, y + 1, x + 7, y + 6, OUTLINE);
        g.fill(x + 1, y + 1, x + 6, y + 6, color);
        g.fill(x + 2, y + 2, x + 4, y + 3, brighter(brighter(color)));
    }

    /** A little lightning bolt (8x15). */
    public static void bolt(GuiGraphicsExtractor g, int x, int y, int c) {
        g.fill(x + 3, y, x + 7, y + 7, c);
        g.fill(x + 1, y + 6, x + 9, y + 8, c);
        g.fill(x + 3, y + 8, x + 7, y + 15, c);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static int brighter(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 40);
        int gg = Math.min(255, ((argb >> 8) & 0xFF) + 40);
        int b = Math.min(255, (argb & 0xFF) + 40);
        return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }

    public static int darker(int argb) {
        int r = ((argb >> 16) & 0xFF) * 3 / 5;
        int gg = ((argb >> 8) & 0xFF) * 3 / 5;
        int b = (argb & 0xFF) * 3 / 5;
        return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }

    /** Text cut to fit {@code width} pixels (with an ellipsis when cut). */
    public static FormattedCharSequence fit(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getVisualOrderText();
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("…")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
    }

    /** Draws a "label: value" line with the value right-aligned at {@code right}. */
    public static void row(GuiGraphicsExtractor g, Font font, Component label, Component value, int x, int right, int y,
                           int labelColor, int valueColor) {
        int vw = font.width(value);
        g.text(font, fit(font, label, right - x - vw - 4), x, y, labelColor, false);
        g.text(font, value, right - vw, y, valueColor, false);
    }
}
