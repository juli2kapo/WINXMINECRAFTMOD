package net.juli2kapo.factoryascent.space;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance knobs of space travel and personal gear, in their own server config file
 * ({@code factoryascent-space-server.toml}, synced to clients like the main config).
 */
public final class SpaceConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue CREW_FUEL;
    public static final ModConfigSpec.IntValue SUIT_OXYGEN_SECONDS;
    public static final ModConfigSpec.DoubleValue VACUUM_DAMAGE;
    public static final ModConfigSpec.DoubleValue ORBIT_GRAVITY;
    public static final ModConfigSpec.IntValue AIR_VOLUME_LIMIT;
    public static final ModConfigSpec.IntValue AIR_LEAK_SECONDS;
    public static final ModConfigSpec.DoubleValue MOON_GRAVITY;
    public static final ModConfigSpec.DoubleValue MARS_GRAVITY;
    public static final ModConfigSpec.DoubleValue IO_GRAVITY;
    public static final ModConfigSpec.DoubleValue MARS_SUIT_DRAIN;
    public static final ModConfigSpec.DoubleValue HEAT_DAMAGE;
    public static final ModConfigSpec.BooleanValue DUST_STORMS;
    public static final ModConfigSpec.IntValue STATION_RADIUS;
    public static final ModConfigSpec.BooleanValue STATION_PROTECTION;
    public static final ModConfigSpec.IntValue SEALER_ENERGY;
    public static final ModConfigSpec.IntValue COMPRESSOR_ENERGY_PER_SECOND;
    public static final ModConfigSpec.DoubleValue JETPACK_ENERGY;
    public static final ModConfigSpec.BooleanValue BOOTS_ROTATE_GRAVITY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("space");
        CREW_FUEL = b.comment("Fuel units a crewed launch burns (Blaze Powder 1, Rocket Fuel 4; the tank holds 8).")
                .defineInRange("crewFuelCost", 8, 1, 8);
        SUIT_OXYGEN_SECONDS = b.comment("Seconds of air a full Astronaut Suit holds.")
                .defineInRange("suitOxygenSeconds", 600, 10, 36000);
        VACUUM_DAMAGE = b.comment("Damage per second taken in an airless dimension without air (20 = dead in one second).")
                .defineInRange("vacuumDamage", 4.0, 0.0, 1000.0);
        ORBIT_GRAVITY = b.comment("Gravity in orbit, as a fraction of normal gravity (falls hurt less by the same fraction).")
                .defineInRange("orbitGravity", 0.25, 0.01, 1.0);
        AIR_VOLUME_LIMIT = b.comment("Oxygen Sealer: the largest sealed volume (in blocks of air) it can fill. A room that is bigger",
                        "(or open to space) counts as breached. An Air Vent fills a quarter of this.")
                .defineInRange("airVolumeLimit", 4096, 8, 65536);
        AIR_LEAK_SECONDS = b.comment("Seconds a breached room keeps its air while it leaks out (warning, particles).")
                .defineInRange("airLeakSeconds", 5, 0, 120);
        SEALER_ENERGY = b.comment("Oxygen Sealer: FE per tick it uses while running.")
                .defineInRange("sealerEnergyPerTick", 16, 0, 10000);
        COMPRESSOR_ENERGY_PER_SECOND = b.comment("Oxygen Compressor: FE per second of air it puts into a suit.")
                .defineInRange("compressorEnergyPerSecondOfAir", 20, 0, 10000);
        JETPACK_ENERGY = b.comment("Multiplier on the energy jetpacks use while thrusting or hovering.")
                .defineInRange("jetpackEnergy", 1.0, 0.0, 100.0);
        b.pop();
        b.push("planets");
        MOON_GRAVITY = b.comment("Gravity on the Moon, as a fraction of normal gravity.")
                .defineInRange("moonGravity", 0.166, 0.01, 1.0);
        MARS_GRAVITY = b.comment("Gravity on Mars, as a fraction of normal gravity.")
                .defineInRange("marsGravity", 0.38, 0.01, 1.0);
        IO_GRAVITY = b.comment("Gravity on Io, as a fraction of normal gravity.")
                .defineInRange("ioGravity", 0.18, 0.01, 1.0);
        MARS_SUIT_DRAIN = b.comment("How fast a suit uses air on Mars compared to vacuum (its thin air helps a little).")
                .defineInRange("marsSuitDrain", 0.5, 0.0, 1.0);
        HEAT_DAMAGE = b.comment("Damage per second on Io's surface without a suit Thermal Lining (0 = no heat hazard).")
                .defineInRange("heatDamage", 2.0, 0.0, 1000.0);
        DUST_STORMS = b.comment("Dust storms on Mars (particles and short sight while they blow).")
                .define("dustStorms", true);
        b.pop();
        b.push("stations");
        STATION_RADIUS = b.comment("Station Core: radius in blocks of the station it claims and reports on.")
                .defineInRange("stationRadius", 32, 4, 128);
        STATION_PROTECTION = b.comment("Only the Station Core owner's team may break blocks inside a claimed station.")
                .define("stationProtection", true);
        BOOTS_ROTATE_GRAVITY = b.comment("Magnetic Boots really turn your gravity in low gravity: walk into a wall and it becomes",
                        "your floor (view, movement and body turn with it). false = the old behaviour: climb walls and hang",
                        "from ceilings with an upright view.")
                .define("magneticBootsRotateGravity", true);
        b.pop();
        SPEC = b.build();
    }

    private SpaceConfig() {}

    /** Reads a value, falling back to its default before the config is loaded (tests, early client). */
    public static int get(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static boolean get(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static double get(ModConfigSpec.DoubleValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    /** Oxygen units (1 unit = 1 tick of breathing) a full suit holds. */
    public static int suitOxygen() {
        return get(SUIT_OXYGEN_SECONDS) * 20;
    }

    public static double moonGravity() {
        return get(MOON_GRAVITY);
    }

    public static double marsGravity() {
        return get(MARS_GRAVITY);
    }

    public static double ioGravity() {
        return get(IO_GRAVITY);
    }

    public static double orbitGravity() {
        return get(ORBIT_GRAVITY);
    }

    public static double marsSuitDrain() {
        return get(MARS_SUIT_DRAIN);
    }

    public static int airVolumeLimit() {
        return get(AIR_VOLUME_LIMIT);
    }

    public static int crewFuel() {
        return get(CREW_FUEL);
    }
}
