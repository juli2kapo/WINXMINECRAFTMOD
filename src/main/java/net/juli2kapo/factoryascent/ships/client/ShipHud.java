package net.juli2kapo.factoryascent.ships.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.ships.AbstractShip;
import net.juli2kapo.factoryascent.ships.BronzeCog;
import net.juli2kapo.factoryascent.ships.MotorShip;
import net.juli2kapo.factoryascent.ships.OrbitTransfer;
import net.juli2kapo.factoryascent.ships.ShipConfig;
import net.juli2kapo.factoryascent.ships.ShipMath;
import net.juli2kapo.factoryascent.ships.Shuttle;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * The instruments while aboard (top left): state, speed and heading for everyone; wind and sail
 * pull on the cog, the battery on the motor ship, and the cockpit read-out on the shuttle
 * (altitude, climb rate, tank, where climbing or descending takes you). Hidden with F1 and while a
 * screen is open.
 */
final class ShipHud {
    private static final int BG = 0x9010131A, EDGE = 0xC03A4050, TEXT = 0xFFE0E4EC, MUTED = 0xFF8C95A3;

    private ShipHud() {}

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        AbstractShip ship = ShipsClient.riding();
        if (ship == null || mc.options.hideGui || mc.screen != null) return;
        Font font = mc.font;
        List<Component> lines = new ArrayList<>();
        List<float[]> bars = new ArrayList<>(); // {line index, fraction, color}
        boolean shuttle = ship instanceof Shuttle;
        lines.add(ship.getDisplayName().copy().append(Component.literal("  ")).append(ShipText.state(shuttle, ship.state())));
        double kmh = ship.horizontalSpeed() * 20 * 3.6;
        int heading = ShipMath.compassHeading(ship.getYRot());
        lines.add(ShipText.speed(kmh));
        lines.add(ShipText.heading(heading));
        Level level = ship.level();
        if (ship instanceof BronzeCog) {
            float wind = ShipMath.windYaw(level.getGameTime());
            float strength = ShipMath.windStrength(level.getGameTime(), level.getRainLevel(1f), level.getThunderLevel(1f));
            lines.add(ShipText.wind(wind, strength));
            lines.add(ShipText.sails(ShipMath.sailSpeedFactor(ship.getYRot(), wind, strength, ShipConfig.windInfluence())));
        } else if (ship instanceof MotorShip) {
            lines.add(ShipText.battery(ship.syncedFuel(), MotorShip.CAPACITY));
            bars.add(new float[] {lines.size(), ship.syncedFuel() / (float) MotorShip.CAPACITY, 0xFFE0C040});
            lines.add(Component.empty());
        } else if (ship instanceof Shuttle) {
            lines.add(ShipText.altitude((int) Math.floor(ship.getY())));
            lines.add(ShipText.climb((ship.getY() - ship.yo) * 20));
            int cap = Shuttle.TANK_ITEMS * ShipConfig.fuelPerItem();
            lines.add(ShipText.tank(ship.syncedFuel(), cap));
            bars.add(new float[] {lines.size(), ship.syncedFuel() / (float) cap, 0xFFE08030});
            lines.add(Component.empty());
            var realm = OrbitTransfer.realm(level);
            lines.add(ShipText.destination(realm == ShipMath.Realm.ORBIT, realm == ShipMath.Realm.OVERWORLD,
                    realm == ShipMath.Realm.OVERWORLD ? level.getMaxY() + 1 : 320));
        }
        lines.add(ShipText.lights(ship.lightsOn()));
        int w = 0;
        for (Component c : lines) w = Math.max(w, font.width(c));
        w = Math.max(w, 110) + 10;
        int x = 6, y = 6, h = lines.size() * 10 + 6;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
        g.fill(x, y, x + w, y + h, BG);
        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i), x + 5, y + 4 + i * 10, i == 0 ? TEXT : i >= 3 ? MUTED : TEXT, false);
        }
        for (float[] bar : bars) {
            int by = y + 4 + (int) bar[0] * 10 + 1;
            int bw = w - 10;
            g.fill(x + 5, by, x + 5 + bw, by + 6, 0xFF2A2A2A);
            int filled = Math.round(bw * Math.max(0, Math.min(1, bar[1])));
            if (filled > 0) g.fill(x + 5, by, x + 5 + filled, by + 6, (int) (long) bar[2] | 0xFF000000);
        }
        if (ship instanceof BronzeCog) windArrow(g, x + w - 16, y + 30, ShipMath.windYaw(level.getGameTime()) - ship.getYRot());
    }

    /** A little dial: the ship's bow up, the arrow showing where the wind blows toward. */
    private static void windArrow(GuiGraphicsExtractor g, int cx, int cy, float relYaw) {
        g.fill(cx - 9, cy - 9, cx + 10, cy + 10, 0x60000000);
        g.fill(cx, cy - 8, cx + 1, cy - 5, 0xFF8C95A3); // bow mark
        double a = Math.toRadians(relYaw);
        // relative yaw 0 = the bow (up on the dial); positive yaw is to starboard (right)
        double dx = Math.sin(a), dy = -Math.cos(a);
        for (int i = -6; i <= 6; i++) {
            int px = cx + (int) Math.round(dx * i), py = cy + (int) Math.round(dy * i);
            g.fill(px, py, px + 1, py + 1, i > 3 ? 0xFFFFFFFF : 0xFF7FB8FF);
        }
    }
}
