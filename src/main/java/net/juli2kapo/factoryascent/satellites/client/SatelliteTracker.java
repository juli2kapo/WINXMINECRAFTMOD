package net.juli2kapo.factoryascent.satellites.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.orbital.SatelliteSky;
import net.juli2kapo.factoryascent.ships.AbstractShip;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.juli2kapo.factoryascent.space.client.SatelliteSkyClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The satellite tracker (top right) while flying a ship in Earth orbit: the nearest satellite, its
 * distance and height above you, and a dial pointing to it (up = where you look). Every satellite
 * of the Overworld is listed, wherever it is (the server sends them all, see {@link SatelliteSky}).
 */
final class SatelliteTracker {
    private static final int BG = 0x9010131A, EDGE = 0xC03A4050, TEXT = 0xFFE0E4EC, MUTED = 0xFF8C95A3;

    private SatelliteTracker() {}

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        if (!mc.level.dimension().equals(SpaceRules.ORBIT) || SatelliteSkyClient.orbitAltitude() <= 0) return;
        if (!(mc.player.getVehicle() instanceof AbstractShip)) return;
        List<SatelliteSky.Entry> list = SatelliteSkyClient.current();
        Font font = mc.font;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.factoryascent.satellite_tracker.title"));
        double time = mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(delta.getGameTimeDeltaPartialTick(false));
        SatelliteSky.Entry nearest = null;
        Vec3 to = null;
        for (SatelliteSky.Entry e : list) {
            Vec3 d = SatelliteSkyClient.orbitPosition(e, time).add(0, 1.75, 0).subtract(eye);
            if (to == null || d.lengthSqr() < to.lengthSqr()) {
                nearest = e;
                to = d;
            }
        }
        if (nearest == null) {
            lines.add(Component.translatable("gui.factoryascent.satellite_tracker.none"));
        } else {
            lines.add(SatelliteSkyClient.label(nearest));
            lines.add(Component.translatable("gui.factoryascent.satellite_tracker.distance", Math.round(to.length()),
                    (to.y >= 0 ? "+" : "") + Math.round(to.y)));
            if (list.size() > 1) lines.add(Component.translatable("gui.factoryascent.satellite_tracker.count", list.size()));
        }
        int w = 0;
        for (Component c : lines) w = Math.max(w, font.width(c));
        boolean dial = nearest != null;
        w = Math.max(w, 90) + 10 + (dial ? 26 : 0);
        int h = Math.max(lines.size() * 10 + 6, dial ? 30 : 0);
        int x = g.guiWidth() - w - 6, y = 6;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
        g.fill(x, y, x + w, y + h, BG);
        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), x + 5, y + 4 + i * 10, i == 0 || i == 1 ? TEXT : MUTED, false);
        }
        if (dial) {
            int cx = x + w - 14, cy = y + h / 2;
            g.fill(cx - 10, cy - 10, cx + 11, cy + 11, 0x60000000);
            // bearing to it relative to where the player looks (0 = straight ahead, up on the dial)
            float bearing = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            double a = Math.toRadians(Mth.wrapDegrees(bearing - mc.player.getYRot()));
            double dx = Math.sin(a), dy = -Math.cos(a); // positive relative yaw is to the right
            for (int i = 0; i <= 8; i++) {
                int px = cx + (int) Math.round(dx * i), py = cy + (int) Math.round(dy * i);
                g.fill(px, py, px + 1, py + 1, i > 5 ? 0xFFFFD040 : 0xFF7FB8FF);
            }
            // above or below: a tick at the top or bottom edge
            int tick = to.y > 8 ? cy - 10 : to.y < -8 ? cy + 9 : -1;
            if (tick >= 0) g.fill(cx - 3, tick, cx + 4, tick + 1, 0xFFFFD040);
        }
    }
}
