package net.juli2kapo.factoryascent.phone.client.apps;

import net.juli2kapo.factoryascent.phone.client.PhoneAppView;
import net.juli2kapo.factoryascent.phone.client.PhoneUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Dyson app: the team's Dyson Cube progress (read-only), drawn as a sun inside a filling lattice. */
public final class DysonView extends PhoneAppView {
    public DysonView(String app) {
        super(app);
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, float partial) {
        long collectors = data.getLongOr("collectors", 0);
        int target = Math.max(1, data.getIntOr("target", 1000));
        double completion = data.getDoubleOr("completion", 0);
        int cx = W / 2, cy = 44;
        // the sun
        long t = System.currentTimeMillis();
        float pulse = 0.5f + 0.5f * Mth.sin(t / 400f);
        PhoneUi.round2(g, cx - 9, cy - 9, 18, 18, 0xFFFFB020);
        PhoneUi.round2(g, cx - 6, cy - 6, 12, 12, 0xFFFFE070);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, PhoneUi.withAlpha(0xFFFFFF, 0.5f + 0.5f * pulse));
        // the cube lattice: 40 panels around the sun, lit up to the completion
        int panels = 40;
        int lit = (int) Math.round(completion * panels);
        if (collectors > 0 && lit == 0) lit = 1;
        int r = 30;
        for (int i = 0; i < panels; i++) {
            // walk the square's perimeter
            float p = i / (float) panels * 4f;
            int side = (int) p;
            float f = p - side;
            int px, py;
            switch (side) {
                case 0 -> { px = cx - r + Math.round(f * 2 * r); py = cy - r; }
                case 1 -> { px = cx + r; py = cy - r + Math.round(f * 2 * r); }
                case 2 -> { px = cx + r - Math.round(f * 2 * r); py = cy + r; }
                default -> { px = cx - r; py = cy + r - Math.round(f * 2 * r); }
            }
            g.fill(px - 2, py - 2, px + 3, py + 3, i < lit ? 0xFF3A5AD8 : 0xFF1C2232);
            if (i < lit) g.fill(px - 1, py - 1, px + 1, py, 0xFF9AB0FF);
        }
        if (collectors <= 0) {
            PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.dyson.none"), W / 2, 86, W - 8, PhoneUi.TEXT);
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.dyson.how"), 8, 100, W - 16, 6, PhoneUi.MUTED);
            return;
        }
        String pct = String.format(java.util.Locale.ROOT, "%.1f%%", completion * 100);
        g.pose().pushMatrix();
        g.pose().translate(W / 2f, 82);
        g.pose().scale(2f, 2f);
        g.text(PhoneUi.font(), pct, -PhoneUi.font().width(pct) / 2, 0, 0xFFFFD86A, true);
        g.pose().popMatrix();
        PhoneUi.bar(g, 8, 102, W - 16, 6, (float) completion, 0xFF3A5AD8);
        // milestones along the bar
        int[] milestones = data.getIntArray("milestones").orElse(new int[0]);
        for (int m : milestones) {
            if (m <= 0) continue;
            int x = 8 + Math.round((W - 16) * m / 100f) - 1;
            boolean reached = completion * 100 >= m - 1e-6;
            g.fill(x, 100, x + 2, 110, reached ? 0xFFFFD86A : 0xFF4A5268);
        }
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.dyson.collectors", PhoneUi.compact(collectors), PhoneUi.compact(target)),
                6, 116, W - 10, PhoneUi.TEXT);
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.dyson.power", PhoneUi.compact(data.getLongOr("power", 0))),
                6, 128, W - 10, PhoneUi.MUTED);
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.dyson.received", PhoneUi.compact(data.getLongOr("received", 0))),
                6, 140, W - 10, PhoneUi.MUTED);
    }
}
