package net.juli2kapo.factoryascent.trains.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.trains.Locomotive;
import net.juli2kapo.factoryascent.trains.RollingStock;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The driver's instruments (top left): the locomotive and its state, speed, the throttle lever
 * (a centred bar: right = ahead, left = reverse), brake, the fuel gauges and the train's length.
 * Passengers in other vehicles just see the speed. Hidden while a screen is open.
 */
final class TrainHud {
    private static final int BG = 0x9010131A, EDGE = 0xC03A4050, TEXT = 0xFFE0E4EC;

    private TrainHud() {}

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof RollingStock stock) || mc.gui.screen() != null) return;
        Font font = mc.font;
        List<Component> lines = new ArrayList<>();
        List<float[]> bars = new ArrayList<>(); // {line, fraction, color, centred}
        if (stock instanceof Locomotive loco) {
            lines.add(loco.getDisplayName().copy().append("  ").append(TrainText.status(loco.status())));
            lines.add(TrainText.speed(loco.syncedSpeed()));
            Component thr = TrainText.throttle(loco.throttle());
            if ((loco.syncedInput() & Locomotive.IN_BRAKE) != 0) {
                thr = thr.copy().append("  ").append(Component.translatable("gui.factoryascent.train.brake").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            lines.add(thr);
            bars.add(new float[] {lines.size(), (float) loco.throttle(), 0xFF50D050, 1});
            lines.add(Component.empty());
            for (TrainText.Gauge gauge : TrainText.gauges(loco)) {
                lines.add(gauge.text());
                bars.add(new float[] {lines.size(), gauge.fraction(), gauge.color(), 0});
                lines.add(Component.empty());
            }
            lines.add(Component.translatable("gui.factoryascent.train.cars", loco.cars()).withStyle(ChatFormatting.GRAY));
            lines.add(TrainText.lights(loco.lightsOn()).copy().withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(stock.getDisplayName().copy());
            lines.add(TrainText.speed(stock.syncedSpeed()));
        }
        int w = 0;
        for (Component c : lines) w = Math.max(w, font.width(c));
        w = Math.max(w, 120) + 10;
        int x = 6, y = 6, h = lines.size() * 10 + 6;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
        g.fill(x, y, x + w, y + h, BG);
        for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), x + 5, y + 4 + i * 10, TEXT, false);
        for (float[] bar : bars) {
            int by = y + 4 + (int) bar[0] * 10 + 1;
            int bw = w - 10;
            g.fill(x + 5, by, x + 5 + bw, by + 6, 0xFF2A2A2A);
            int color = (int) (long) bar[2] | 0xFF000000;
            if (bar[3] > 0) {
                int mid = x + 5 + bw / 2;
                g.fill(mid, by - 1, mid + 1, by + 7, 0xFFFFFFFF);
                int len = Math.round(bw / 2f * Math.min(1, Math.abs(bar[1])));
                if (bar[1] > 0) g.fill(mid + 1, by + 1, mid + 1 + len, by + 5, color);
                else if (bar[1] < 0) g.fill(mid - len, by + 1, mid, by + 5, 0xFFE0A030);
            } else {
                int filled = Math.round(bw * Math.max(0, Math.min(1, bar[1])));
                if (filled > 0) g.fill(x + 5, by, x + 5 + filled, by + 6, color);
            }
        }
    }
}
