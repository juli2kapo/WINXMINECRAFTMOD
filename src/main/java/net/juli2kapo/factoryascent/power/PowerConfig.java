package net.juli2kapo.factoryascent.power;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance knobs of the power ladder (generators, fission, radiation, fusion), in their own server
 * config file ({@code factoryascent-power-server.toml}, synced to clients like the main config).
 * Every generator output is also multiplied by the main config's {@code generatorOutput}.
 */
public final class PowerConfig {
    public static final ModConfigSpec SPEC;
    // generators
    public static final ModConfigSpec.IntValue DYNAMO_FE_PER_POINT;
    public static final ModConfigSpec.IntValue STEAM_ENGINE_OUTPUT;
    public static final ModConfigSpec.DoubleValue STEAM_ENGINE_BURN_TIME;
    public static final ModConfigSpec.IntValue WIND_TURBINE_OUTPUT;
    public static final ModConfigSpec.IntValue BIOGAS_OUTPUT;
    public static final ModConfigSpec.IntValue MAGMATIC_OUTPUT;
    public static final ModConfigSpec.IntValue SOLAR_ARRAY_OUTPUT;
    public static final ModConfigSpec.IntValue RTG_OUTPUT;
    public static final ModConfigSpec.IntValue RTG_PELLET_SECONDS;
    // fission
    public static final ModConfigSpec.DoubleValue REACTOR_ROD_HEAT;
    public static final ModConfigSpec.DoubleValue REACTOR_FE_PER_HEAT;
    public static final ModConfigSpec.IntValue REACTOR_ROD_LIFE_SECONDS;
    public static final ModConfigSpec.IntValue MELTDOWN_TEMPERATURE;
    public static final ModConfigSpec.IntValue ALARM_TEMPERATURE;
    public static final ModConfigSpec.DoubleValue MELTDOWN_EXPLOSION_POWER;
    public static final ModConfigSpec.BooleanValue MELTDOWN_DESTROYS_BLOCKS;
    public static final ModConfigSpec.BooleanValue MELTDOWN_LEAVES_CORIUM;
    // radiation
    public static final ModConfigSpec.BooleanValue RADIATION_ENABLED;
    public static final ModConfigSpec.DoubleValue RADIATION_DAMAGE;
    public static final ModConfigSpec.IntValue RADIATION_SCAN_RADIUS;
    // fusion
    public static final ModConfigSpec.IntValue FUSION_OUTPUT;
    public static final ModConfigSpec.IntValue FUSION_STARTUP_ENERGY;
    public static final ModConfigSpec.IntValue FUSION_DEUTERIUM_SECONDS;
    public static final ModConfigSpec.IntValue FUSION_HELIUM3_SECONDS;
    public static final ModConfigSpec.BooleanValue FUSION_DISRUPTION_DESTROYS_BLOCKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("generators");
        DYNAMO_FE_PER_POINT = b.comment("Kinetic Dynamo: FE per tick for each work point of rotation it receives",
                        "(a Water Wheel in flowing water makes 0.8 points, a Windmill 0.15..0.9).")
                .defineInRange("kineticDynamoFePerPoint", 25, 0, 10000);
        STEAM_ENGINE_OUTPUT = b.comment("Steam Engine: FE/t at full boiler pressure.")
                .defineInRange("steamEngineOutput", 100, 0, 1_000_000);
        STEAM_ENGINE_BURN_TIME = b.comment("Steam Engine: a fuel item burns for its furnace burn time times this (coal: 1600 ticks x 0.75).")
                .defineInRange("steamEngineBurnTimeMultiplier", 0.75, 0.05, 20.0);
        WIND_TURBINE_OUTPUT = b.comment("Wind Turbine: FE/t at 96+ blocks above sea level in clear weather (rain x1.3, thunder x1.8).")
                .defineInRange("windTurbineOutput", 64, 0, 1_000_000);
        BIOGAS_OUTPUT = b.comment("Biogas Generator: FE/t while it has gas (burns 2 mB of gas per tick).")
                .defineInRange("biogasOutput", 80, 0, 1_000_000);
        MAGMATIC_OUTPUT = b.comment("Magmatic Generator: FE/t while it has lava (1 mB of lava per tick: a bucket lasts 50 s).")
                .defineInRange("magmaticOutput", 120, 0, 1_000_000);
        SOLAR_ARRAY_OUTPUT = b.comment("Advanced Solar Array: FE/t in full daylight (half in rain, x1.5 in airless space).")
                .defineInRange("solarArrayOutput", 48, 0, 1_000_000);
        RTG_OUTPUT = b.comment("RTG: FE/t while it holds a Radioisotope Pellet. Works anywhere, day and night, airless or not.")
                .defineInRange("rtgOutput", 20, 0, 1_000_000);
        RTG_PELLET_SECONDS = b.comment("RTG: seconds one Radioisotope Pellet lasts.")
                .defineInRange("rtgPelletSeconds", 3600, 1, 10_000_000);
        b.pop();
        b.push("fission");
        REACTOR_ROD_HEAT = b.comment("Fission reactor: heat per tick of one fuel rod with the control rods out (before the neighbour bonus).")
                .defineInRange("rodHeat", 10.0, 0.0, 10000.0);
        REACTOR_FE_PER_HEAT = b.comment("Fission reactor: FE made from each unit of heat the coolant carries away.")
                .defineInRange("fePerHeat", 8.0, 0.0, 10000.0);
        REACTOR_ROD_LIFE_SECONDS = b.comment("Fission reactor: seconds a fuel rod lasts at full power before it is spent.")
                .defineInRange("rodLifeSeconds", 1800, 1, 10_000_000);
        MELTDOWN_TEMPERATURE = b.comment("Fission reactor: core temperature (C) at which it melts down.")
                .defineInRange("meltdownTemperature", 1200, 200, 100_000);
        ALARM_TEMPERATURE = b.comment("Fission reactor: core temperature (C) at which the alarm sounds.")
                .defineInRange("alarmTemperature", 900, 100, 100_000);
        MELTDOWN_EXPLOSION_POWER = b.comment("Fission reactor: explosion power of a meltdown (TNT is 4). Bigger cores add up to x2.")
                .defineInRange("meltdownExplosionPower", 6.0, 0.0, 40.0);
        MELTDOWN_DESTROYS_BLOCKS = b.comment("Fission reactor: whether a meltdown explosion breaks blocks (false: it only hurts, and the core turns to corium).")
                .define("meltdownDestroysBlocks", true);
        MELTDOWN_LEAVES_CORIUM = b.comment("Fission reactor: whether a meltdown leaves radioactive corium behind.")
                .define("meltdownLeavesCorium", true);
        b.pop();
        b.push("radiation");
        RADIATION_ENABLED = b.comment("Whether radioactive items and blocks irradiate players.")
                .define("radiationEnabled", true);
        RADIATION_DAMAGE = b.comment("Multiplier on the damage of radiation sickness.")
                .defineInRange("radiationDamage", 1.0, 0.0, 100.0);
        RADIATION_SCAN_RADIUS = b.comment("How far (blocks) radioactive blocks and containers reach players.")
                .defineInRange("scanRadius", 5, 1, 12);
        b.pop();
        b.push("fusion");
        FUSION_OUTPUT = b.comment("Tokamak: FE/t while the plasma burns.")
                .defineInRange("fusionOutput", 30000, 0, Integer.MAX_VALUE / 4);
        FUSION_STARTUP_ENERGY = b.comment("Tokamak: FE the magnets need before the plasma can be ignited.")
                .defineInRange("fusionStartupEnergy", 5_000_000, 0, Integer.MAX_VALUE / 2);
        FUSION_DEUTERIUM_SECONDS = b.comment("Tokamak: seconds of burn from one Deuterium Cell.")
                .defineInRange("deuteriumSeconds", 30, 1, 100_000);
        FUSION_HELIUM3_SECONDS = b.comment("Tokamak: seconds of burn from one Helium-3.")
                .defineInRange("helium3Seconds", 60, 1, 100_000);
        FUSION_DISRUPTION_DESTROYS_BLOCKS = b.comment("Tokamak: whether a plasma disruption (containment failure) breaks blocks.")
                .define("disruptionDestroysBlocks", false);
        b.pop();
        SPEC = b.build();
    }

    private PowerConfig() {}

    /** Reads a value, falling back to its default before the config is loaded (tests, early client). */
    public static int get(ModConfigSpec.IntValue value) {
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

    public static boolean get(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    /** The main config's global generator multiplier (1 before it loads). */
    public static double generatorMultiplier() {
        try {
            return net.juli2kapo.factoryascent.Config.GENERATOR_OUTPUT.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }
}
