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
    public static final ModConfigSpec.IntValue FUEL_MOON;
    public static final ModConfigSpec.IntValue FUEL_MARS;
    public static final ModConfigSpec.IntValue FUEL_IO;
    public static final ModConfigSpec.IntValue CRUISE_MOON;
    public static final ModConfigSpec.IntValue CRUISE_MARS;
    public static final ModConfigSpec.IntValue CRUISE_IO;

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
        ORBIT_ARRIVAL_Y = b.comment("Height at which a shuttle arrives in the factoryascent:orbit dimension",
                        "(stations unfold at y = 100, so the default arrives just above them).")
                .defineInRange("orbitArrivalHeight", 112, -2000, 4000);
        ORBIT_REENTRY_Y = b.comment("Descending below this height in orbit re-enters the Overworld above the same x/z.")
                .defineInRange("orbitReentryY", 16, -2000, 4000);
        REENTRY_DROP = b.comment("Blocks below the orbit threshold at which a re-entering shuttle appears in the Overworld.")
                .defineInRange("reentryDrop", 40, 5, 2000);
        b.pop();
        b.push("navigation");
        FUEL_MOON = b.comment("Shuttle navigation: tank units a trip between Earth orbit and the Moon costs.",
                        "Trips cost the difference between two destinations' values (Moon -> Mars = Mars - Moon); an Ion Drive halves it.")
                .defineInRange("fuelMoon", 3000, 0, 1_000_000);
        FUEL_MARS = b.comment("Tank units from Earth orbit to Mars.").defineInRange("fuelMars", 7000, 0, 1_000_000);
        FUEL_IO = b.comment("Tank units from Earth orbit to Io.").defineInRange("fuelIo", 11000, 0, 1_000_000);
        CRUISE_MOON = b.comment("Seconds of cruise from Earth orbit to the Moon (trips take the difference, at least 5 s).")
                .defineInRange("cruiseSecondsMoon", 8, 1, 600);
        CRUISE_MARS = b.comment("Seconds of cruise from Earth orbit to Mars.").defineInRange("cruiseSecondsMars", 15, 1, 600);
        CRUISE_IO = b.comment("Seconds of cruise from Earth orbit to Io.").defineInRange("cruiseSecondsIo", 22, 1, 600);
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

    /** The navigation ladder currently configured (fuel units and cruise ticks from Earth orbit). */
    public static net.juli2kapo.factoryascent.space.planet.Navigation.Costs navCosts() {
        if (!SPEC.isLoaded()) return net.juli2kapo.factoryascent.space.planet.Navigation.Costs.DEFAULT;
        return new net.juli2kapo.factoryascent.space.planet.Navigation.Costs(
                new int[] {0, FUEL_MOON.get(), FUEL_MARS.get(), FUEL_IO.get()},
                new int[] {0, CRUISE_MOON.get() * 20, CRUISE_MARS.get() * 20, CRUISE_IO.get() * 20});
    }

    /** The orbit-transfer thresholds currently configured. */
    public static ShipMath.Thresholds thresholds() {
        return SPEC.isLoaded()
                ? new ShipMath.Thresholds(ORBIT_MARGIN.get(), ORBIT_ARRIVAL_Y.get(), ORBIT_REENTRY_Y.get(), REENTRY_DROP.get())
                : ShipMath.Thresholds.DEFAULT;
    }
}
