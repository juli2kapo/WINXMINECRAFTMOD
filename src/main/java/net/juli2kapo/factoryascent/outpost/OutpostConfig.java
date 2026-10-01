package net.juli2kapo.factoryascent.outpost;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance knobs of planetary outposts (getting home, distress beacons, local fuel and air), in
 * their own server config file ({@code factoryascent-outpost-server.toml}).
 */
public final class OutpostConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue ASCENT_FUEL_FRACTION;
    public static final ModConfigSpec.IntValue ASCENT_TICKS;
    public static final ModConfigSpec.IntValue OXYGEN_CELL_AIR;
    public static final ModConfigSpec.IntValue BEACONS_PER_TEAM;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("outpost");
        ASCENT_FUEL_FRACTION = b.comment("Ascent Module: fuel it burns to reach Earth orbit, as a fraction of what an Orbital Shuttle",
                        "needs for the same trip (a one-seat capsule is lighter). 0 makes it free.")
                .defineInRange("ascentFuelFraction", 0.5, 0.0, 10.0);
        ASCENT_TICKS = b.comment("Ascent Module: ticks of powered climb before the capsule reaches orbit (20 ticks = 1 second).")
                .defineInRange("ascentTicks", 60, 1, 1200);
        OXYGEN_CELL_AIR = b.comment("Oxygen Cell: air (ticks of breathing) one cell puts into a worn suit.")
                .defineInRange("oxygenCellAir", 2400, 1, 1_000_000);
        BEACONS_PER_TEAM = b.comment("Distress Beacons a team can have lit at once (the oldest goes dark when a new one is lit).")
                .defineInRange("beaconsPerTeam", 8, 1, 64);
        b.pop();
        SPEC = b.build();
    }

    private OutpostConfig() {}

    public static double ascentFuelFraction() {
        return SPEC.isLoaded() ? ASCENT_FUEL_FRACTION.get() : 0.5;
    }

    public static int ascentTicks() {
        return SPEC.isLoaded() ? ASCENT_TICKS.get() : 60;
    }

    public static int oxygenCellAir() {
        return SPEC.isLoaded() ? OXYGEN_CELL_AIR.get() : 2400;
    }

    public static int beaconsPerTeam() {
        return SPEC.isLoaded() ? BEACONS_PER_TEAM.get() : 8;
    }
}
