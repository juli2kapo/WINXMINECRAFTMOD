package net.juli2kapo.factoryascent.ships.client;

import net.juli2kapo.factoryascent.ships.SeaShip;
import net.juli2kapo.factoryascent.ships.ShipConfig;
import net.juli2kapo.factoryascent.ships.ShipMath;
import net.juli2kapo.factoryascent.ships.Shuttle;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The instrument read-outs shared by the HUD and the helm/cockpit screen. */
final class ShipText {
    private static final String[] POINTS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private ShipText() {}

    static Component point(int heading) {
        return Component.translatable("gui.factoryascent.ship.dir." + POINTS[ShipMath.compassPoint(heading)]);
    }

    static Component speed(double kmh) {
        return Component.translatable("gui.factoryascent.ship.speed", String.format("%.1f", kmh));
    }

    static Component heading(int heading) {
        return Component.translatable("gui.factoryascent.ship.heading", heading, point(heading));
    }

    static Component windStrength(float strength) {
        String key = strength < 0.7f ? "calm" : strength < 0.95f ? "breeze" : strength < 1.2f ? "strong" : "gale";
        return Component.translatable("gui.factoryascent.ship.wind." + key);
    }

    /** "Wind strong from NE" (the wind blows toward windYaw, so it comes from the opposite point). */
    static Component wind(float windYaw, float strength) {
        return Component.translatable("gui.factoryascent.ship.wind", windStrength(strength),
                point(ShipMath.compassHeading(windYaw + 180f)));
    }

    static Component sails(float factor) {
        return Component.translatable("gui.factoryascent.ship.sail_trim", Math.round(factor * 100));
    }

    static Component battery(int fe, int max) {
        return Component.translatable("gui.factoryascent.ship.battery", EnergyUtil.format(fe), EnergyUtil.format(max));
    }

    static Component tank(int units, int max) {
        return Component.translatable("gui.factoryascent.ship.tank", units, max);
    }

    static Component altitude(int y) {
        return Component.translatable("gui.factoryascent.ship.altitude", y);
    }

    static Component climb(double metresPerSecond) {
        return Component.translatable("gui.factoryascent.ship.vspeed", String.format("%+.1f", metresPerSecond));
    }

    static MutableComponent state(boolean shuttle, int state) {
        if (!shuttle) {
            return state == SeaShip.STATE_AFLOAT
                    ? Component.translatable("gui.factoryascent.ship.state.afloat").withStyle(ChatFormatting.AQUA)
                    : Component.translatable("gui.factoryascent.ship.state.aground").withStyle(ChatFormatting.GOLD);
        }
        return switch (state) {
            case Shuttle.STATE_DOCKED -> Component.translatable("gui.factoryascent.ship.state.docked").withStyle(ChatFormatting.GREEN);
            case Shuttle.STATE_FLYING -> Component.translatable("gui.factoryascent.ship.state.flying").withStyle(ChatFormatting.AQUA);
            case Shuttle.STATE_ORBIT -> Component.translatable("gui.factoryascent.ship.state.orbit").withStyle(ChatFormatting.LIGHT_PURPLE);
            case Shuttle.STATE_REENTRY -> Component.translatable("gui.factoryascent.ship.state.reentry").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            case Shuttle.STATE_CRUISE -> Component.translatable("gui.factoryascent.ship.state.cruise").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
            default -> Component.translatable("gui.factoryascent.ship.state.landed").withStyle(ChatFormatting.GRAY);
        };
    }

    /** Where climbing (or descending) will take the shuttle from here. */
    static Component destination(ShipMath.Realm realm, int overworldTop) {
        ShipMath.Thresholds t = ShipConfig.thresholds();
        Component where = switch (realm) {
            case ORBIT -> Component.translatable("gui.factoryascent.ship.dest_earth", t.reentryY());
            case OVERWORLD -> Component.translatable("gui.factoryascent.ship.dest_orbit", t.orbitY(overworldTop));
            case PLANET -> Component.translatable("gui.factoryascent.ship.dest_leave", net.juli2kapo.factoryascent.space.planet.Planet.ORBIT_LINE);
            case OTHER -> Component.translatable("gui.factoryascent.ship.dest_none");
        };
        return Component.translatable("gui.factoryascent.ship.destination", where);
    }

    static Component lights(boolean on) {
        return Component.translatable("gui.factoryascent.ship.lights",
                Component.translatable(on ? "gui.factoryascent.ship.on" : "gui.factoryascent.ship.off"));
    }
}
