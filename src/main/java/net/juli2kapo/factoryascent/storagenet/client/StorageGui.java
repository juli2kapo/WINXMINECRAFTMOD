package net.juli2kapo.factoryascent.storagenet.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Rectangle-only drawing in the same bevelled style as the machine screens. */
final class StorageGui {
    static final int BG = 0xFFC6C6C6;
    static final int LIGHT = 0xFFFFFFFF;
    static final int SHADOW = 0xFF555555;
    static final int OUTLINE = 0xFF000000;
    static final int SLOT_DARK = 0xFF373737;
    static final int SLOT_FILL = 0xFF8B8B8B;
    static final int TEXT = 0xFF404040;
    /** Storage accent: a cool teal, distinct from the per-age machine accents. */
    static final int ACCENT = 0xFF3FA7B5;

    private StorageGui() {}

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + 3, y + 3, x + w - 3, y + 5, ACCENT);
    }

    /** An 18x18 slot whose item area starts at (x, y). */
    static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DARK);
        g.fill(x, y, x + 17, y + 17, LIGHT);
        g.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    static void playerInventory(GuiGraphicsExtractor g, int left, int top, int invY) {
        for (int i = 0; i < 27; i++) slot(g, left + 8 + (i % 9) * 18, top + invY + (i / 9) * 18);
        for (int i = 0; i < 9; i++) slot(g, left + 8 + i * 18, top + invY + 58);
    }

    /** Count text in the bottom-right corner of an item, shrunk when it would not fit. */
    static void count(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
        int w = font.width(text);
        if (w <= 16) {
            g.text(font, text, x + 17 - w, y + 9, 0xFFFFFFFF, true);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x + 16, y + 16);
        g.pose().scale(0.6f, 0.6f);
        g.text(font, text, -w, -8, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
