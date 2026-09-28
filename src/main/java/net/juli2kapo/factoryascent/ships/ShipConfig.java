package net.juli2kapo.factoryascent.ships;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config of the ships, in its own file ({@code factoryascent-ships-server.toml}, per world
 * under serverconfig/) so it never collides with the main balance file.
 */
public final class ShipConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue SEA_SPEED;
    public static final ModConfigSpec.DoubleValue WIND_INFLUENCE;
    public static final ModConfigSpec.IntValue MOTOR_FE_PER_TICK;
    public static final ModConfigSpec.IntValue SHUTTLE_FUEL_PER_ITEM;
    public static final ModConfigSpec.DoubleValue SHUTTLE_FUEL_USE;
    public static final ModConfigSpec.IntValue ORBIT_MARGIN;
    public static final ModConfigSpec.IntValue ORBIT_ARRIVAL_Y;
    public static final ModConfigSpec.IntValue ORBIT_REENTRY_Y;
    public static final ModConfigSpec.IntValue REENTRY_DROP;
    public static final ModConfigSpec.BooleanValue CARGO_IN_ITEM;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("sea");
        SEA_SPEED = b.comment("Multiplier on the top speed of the Bronze Cog and the Motor Ship.")
                .defineInRange("seaSpeed", 1.0, 0.1, 5.0);
        WIND_INFLUENCE = b.comment("How much wind direction and weather change a sailing ship's speed",
                        "(0 = the sail always pulls at 85%, 1 = full effect: roughly 35% into the wind up to 110% in a storm).")
                .defineInRange("windInfluence", 1.0, 0.0, 1.0);
        MOTOR_FE_PER_TICK = b.comment("FE per tick the Motor Ship's engine uses at full ahead (astern uses half).")
                .defineInRange("motorFePerTick", 40, 0, 10000);
        CARGO_IN_ITEM = b.comment("Breaking a ship keeps its hold (and fuel) inside the dropped item, like a shulker box.",
                        "If false the cargo spills on the ground instead.")
                .define("cargoInItem", true);
        b.pop();
        b.push("shuttle");
        SHUTTLE_FUEL_PER_ITEM = b.comment("Tank units one Rocket Fuel item adds to the Orbital Shuttle (the tank holds 16 items' worth).")
                .defineInRange("fuelPerItem", 1000, 1, 100000);
        SHUTTLE_FUEL_USE = b.comment("Multiplier on the Orbital Shuttle's fuel consumption (0 = free flight).")
                .defineInRange("fuelUse", 1.0, 0.0, 100.0);
        ORBIT_MARGIN = b.comment("Blocks above the Overworld's build limit at which a climbing shuttle leaves for orbit",
                        "(the build limit is 320 in a default world, so 100 means y = 420).")
                .defineInRange("orbitMargin", 100, 0, 2000);
        ORBIT_ARRIVAL_Y = b.comment("Height at which a shuttle arrives in the factoryascent:orbit dimension.")
                .defineInRange("orbitArrivalY", 160, -2000, 4000);
        ORBIT_REENTRY_Y = b.comment("Descending below this height in orbit re-enters the Overworld above the same x/z.")
                .defineInRange("orbitReentryY", 16, -2000, 4000);
        REENTRY_DROP = b.comment("Blocks below the orbit threshold at which a re-entering shuttle appears in the Overworld.")
                .defineInRange("reentryDrop", 40, 5, 2000);
        b.pop();
        SPEC = b.build();
    }

    private ShipConfig() {}

    /** Config values as plain numbers, readable before the config is loaded (tests, early ticks). */
    public static double seaSpeed() {
        return SPEC.isLoaded() ? SEA_SPEED.get() : 1.0;
    }

    public static double windInfluence() {
        return SPEC.isLoaded() ? WIND_INFLUENCE.get() : 1.0;
    }

    public static int motorFePerTick() {
        return SPEC.isLoaded() ? MOTOR_FE_PER_TICK.get() : 40;
    }

    public static int fuelPerItem() {
        return SPEC.isLoaded() ? SHUTTLE_FUEL_PER_ITEM.get() : 1000;
    }

    public static double fuelUse() {
        return SPEC.isLoaded() ? SHUTTLE_FUEL_USE.get() : 1.0;
    }

    public static boolean cargoInItem() {
        return !SPEC.isLoaded() || CARGO_IN_ITEM.get();
    }

    /** The orbit-transfer thresholds currently configured. */
    public static ShipMath.Thresholds thresholds() {
        return SPEC.isLoaded()
                ? new ShipMath.Thresholds(ORBIT_MARGIN.get(), ORBIT_ARRIVAL_Y.get(), ORBIT_REENTRY_Y.get(), REENTRY_DROP.get())
                : ShipMath.Thresholds.DEFAULT;
    }
}
