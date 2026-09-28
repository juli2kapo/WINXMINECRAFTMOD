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
    public static final ModConfigSpec.IntValue SEALER_RADIUS;
    public static final ModConfigSpec.IntValue SEALER_ENERGY;
    public static final ModConfigSpec.IntValue COMPRESSOR_ENERGY_PER_SECOND;
    public static final ModConfigSpec.DoubleValue JETPACK_ENERGY;

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
        SEALER_RADIUS = b.comment("Oxygen Sealer: radius in blocks of the breathable bubble it keeps up.")
                .defineInRange("sealerRadius", 8, 1, 32);
        SEALER_ENERGY = b.comment("Oxygen Sealer: FE per tick it uses while running.")
                .defineInRange("sealerEnergyPerTick", 16, 0, 10000);
        COMPRESSOR_ENERGY_PER_SECOND = b.comment("Oxygen Compressor: FE per second of air it puts into a suit.")
                .defineInRange("compressorEnergyPerSecondOfAir", 20, 0, 10000);
        JETPACK_ENERGY = b.comment("Multiplier on the energy jetpacks use while thrusting or hovering.")
                .defineInRange("jetpackEnergy", 1.0, 0.0, 100.0);
        b.pop();
        SPEC = b.build();
    }

    private SpaceConfig() {}

    /** Reads a value, falling back to its default before the config is loaded (tests, early client). */
    static int get(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    static double get(ModConfigSpec.DoubleValue value) {
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

    public static int crewFuel() {
        return get(CREW_FUEL);
    }
}
