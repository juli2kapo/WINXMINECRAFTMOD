package net.juli2kapo.factoryascent.ender.client;

import net.juli2kapo.factoryascent.client.FactoryGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws the glass stasis chamber around the pearl slot of the Ender Anchor / Beacon screens. */
final class ChamberGui {
    private ChamberGui() {}

    /**
     * A glass tube of water on soul sand. {@code lid} adds the beacon's trapdoor; bubbles rise
     * while {@code active}.
     */
    static void chamber(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean lid, long time) {
        g.fill(x, y, x + w, y + h, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFB9D7E6);                 // glass rim
        int ix = x + 3, iy = y + 3, iw = w - 6, ih = h - 6;
        int sand = 7;
        // water, a little darker towards the bottom
        for (int row = 0; row < ih - sand; row++) {
            float t = row / (float) (ih - sand);
            int b = (int) (0xC8 - 0x40 * t), gg = (int) (0x7A - 0x28 * t);
            g.fill(ix, iy + row, ix + iw, iy + row + 1, 0xFF000000 | (0x24 << 16) | (gg << 8) | b);
        }
        // soul sand
        g.fill(ix, iy + ih - sand, ix + iw, iy + ih, 0xFF4E3A2C);
        for (int i = 0; i < iw; i += 3) g.fill(ix + i, iy + ih - sand + 1 + (i % 2), ix + i + 1, iy + ih - sand + 2 + (i % 2), 0xFF6B5240);
        if (lid) {
            g.fill(x + 1, y + 1, x + w - 1, y + 4, 0xFF7A5A38);
            g.fill(x + 3, y + 2, x + w - 3, y + 3, 0xFF94704A);
        }
        // glass glints
        g.fill(ix + 1, iy + 2, ix + 2, iy + ih - sand - 2, 0x60FFFFFF);
        if (active) {
            int span = ih - sand - 4;
            for (int i = 0; i < 7; i++) {
                int bx = ix + 2 + (i * 7) % Math.max(1, iw - 4);
                int by = iy + ih - sand - 2 - (int) ((time * (1.1 + (i % 3) * 0.35) + i * 11) % span);
                g.fill(bx, by, bx + 2, by + 2, 0xC0E6F6FF);
            }
        }
    }
}
