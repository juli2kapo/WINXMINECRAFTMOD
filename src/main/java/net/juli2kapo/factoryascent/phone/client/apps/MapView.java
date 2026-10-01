package net.juli2kapo.factoryascent.phone.client.apps;

import net.juli2kapo.factoryascent.phone.client.PhoneAppView;
import net.juli2kapo.factoryascent.phone.client.PhoneUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Map app: survey status and a big "Open map" button (the survey map opens; closing it comes back here). */
public final class MapView extends PhoneAppView {
    private static final int BX = 8, BY = 146, BW = 116, BH = 18;

    public MapView(String app) {
        super(app);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, float partial) {
        boolean surveying = data.getBooleanOr("surveying", false);
        // a stylised radar of the area around you
        int cx = W / 2, cy = 46, r = 38;
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF0C1A14);
        for (int i = -r; i <= r; i += 8) {
            g.fill(cx + i, cy - r, cx + i + 1, cy + r, 0xFF153024);
            g.fill(cx - r, cy + i, cx + r, cy + i + 1, 0xFF153024);
        }
        long t = System.currentTimeMillis();
        int seed = data.getIntOr("x", 0) * 31 + data.getIntOr("z", 0);
        if (surveying) {
            // imaged terrain patches
            java.util.Random rnd = new java.util.Random(seed >> 6);
            for (int i = 0; i < 26; i++) {
                int px = cx - r + rnd.nextInt(2 * r - 8), py = cy - r + rnd.nextInt(2 * r - 8);
                int c = switch (rnd.nextInt(4)) {
                    case 0 -> 0xFF2E6E3A;
                    case 1 -> 0xFF3F8A48;
                    case 2 -> 0xFF2A5A8C;
                    default -> 0xFF7A6A44;
                };
                g.fill(px, py, px + 4 + rnd.nextInt(8), py + 4 + rnd.nextInt(6), c);
            }
        }
        // sweep line
        float a = (t % 3000) / 3000f * Mth.TWO_PI;
        for (int i = 0; i < r; i += 2) {
            int px = cx + Math.round(Mth.cos(a) * i), py = cy + Math.round(Mth.sin(a) * i);
            g.fill(px, py, px + 2, py + 2, 0xAA6CF0A0);
        }
        // you
        PhoneUi.dot(g, cx - 2, cy - 2, 0xFFFFFFFF);
        g.fill(cx - r, cy - r, cx + r, cy - r + 1, 0xFF2E9A5E);
        g.fill(cx - r, cy + r - 1, cx + r, cy + r, 0xFF2E9A5E);
        g.fill(cx - r, cy - r, cx - r + 1, cy + r, 0xFF2E9A5E);
        g.fill(cx + r - 1, cy - r, cx + r, cy + r, 0xFF2E9A5E);

        int y = 90;
        PhoneUi.dot(g, 6, y + 2, surveying ? PhoneUi.GOOD : PhoneUi.WARN);
        PhoneUi.text(g, Component.translatable(surveying ? "gui.factoryascent.phone.map.surveying" : "gui.factoryascent.phone.map.no_survey"),
                14, y, W - 18, PhoneUi.TEXT);
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.map.chunks", data.getIntOr("chunks", 0)), 6, y + 12, W - 10, PhoneUi.MUTED);
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.map.position", data.getIntOr("x", 0), data.getIntOr("z", 0)),
                6, y + 24, W - 10, PhoneUi.MUTED);
        PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.map.hint"), 6, y + 36, W - 10, 2, PhoneUi.FAINT);
        PhoneUi.button(g, BX, BY, BW, BH, Component.translatable("gui.factoryascent.phone.map.open"), 0xFF2E9A5E,
                PhoneUi.inside(mx, my, BX, BY, BW, BH), true);
    }

    @Override
    public boolean click(double mx, double my, int button) {
        if (PhoneUi.inside(mx, my, BX, BY, BW, BH)) {
            sendLeaving("launch");
            return true;
        }
        return false;
    }
}
