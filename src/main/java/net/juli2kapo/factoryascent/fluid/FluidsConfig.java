package net.juli2kapo.factoryascent.fluid;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Balance of the fluid machines, in {@code factoryascent-fluids-server.toml}. */
public final class FluidsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue BOILER_STEAM;
    public static final ModConfigSpec.DoubleValue BOILER_BURN_TIME;
    public static final ModConfigSpec.IntValue STEAM_FE_PER_MB;
    public static final ModConfigSpec.IntValue TURBINE_MAX_STEAM;
    public static final ModConfigSpec.DoubleValue REACTOR_STEAM_PER_HEAT;
    public static final ModConfigSpec.IntValue DIESEL_OUTPUT;
    public static final ModConfigSpec.IntValue DIESEL_PER_TICK;
    public static final ModConfigSpec.IntValue BIOGAS_DIGEST_TICKS;
    public static final ModConfigSpec.IntValue PUMP_RADIUS;
    public static final ModConfigSpec.IntValue PUMP_INTERVAL;
    public static final ModConfigSpec.IntValue PUMP_ENERGY;
    public static final ModConfigSpec.BooleanValue PUMP_KEEPS_WATER;
    public static final ModConfigSpec.IntValue DERRICK_INTERVAL;
    public static final ModConfigSpec.IntValue DERRICK_ENERGY;
    public static final ModConfigSpec.IntValue REFINERY_ENERGY;
    public static final ModConfigSpec.IntValue REFINERY_TICKS;
    public static final ModConfigSpec.IntValue ELECTROLYZER_ENERGY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("steam");
        BOILER_STEAM = b.comment("Boiler: mB of steam per tick at full pressure (it boils as much water).")
                .defineInRange("boilerSteam", 20, 1, 100_000);
        BOILER_BURN_TIME = b.comment("Boiler: a fuel item burns for its furnace burn time times this.")
                .defineInRange("boilerBurnTimeMultiplier", 0.75, 0.05, 20.0);
        STEAM_FE_PER_MB = b.comment("Steam Engine and Steam Turbine: FE made from each mB of steam.")
                .defineInRange("steamFePerMb", 10, 1, 10_000);
        TURBINE_MAX_STEAM = b.comment("Steam Turbine: most steam (mB) it can use per tick once spun up.")
                .defineInRange("turbineMaxSteam", 400, 1, 1_000_000);
        REACTOR_STEAM_PER_HEAT = b.comment("Fission reactor: mB of steam made from each unit of heat while a Coolant Port feeds a steam consumer",
                        "(in place of the FE the reactor would make itself; at 10 FE/mB that is 25% more power).")
                .defineInRange("reactorSteamPerHeat", 1.0, 0.0, 1000.0);
        b.pop();
        b.push("oil");
        DIESEL_OUTPUT = b.comment("Diesel Generator: FE/t while it burns.")
                .defineInRange("dieselOutput", 300, 0, 1_000_000);
        DIESEL_PER_TICK = b.comment("Diesel Generator: mB of diesel burnt per tick.")
                .defineInRange("dieselPerTick", 1, 1, 1000);
        DERRICK_INTERVAL = b.comment("Oil Derrick: ticks per 1000 mB (one oil source block) drawn from the pocket.")
                .defineInRange("derrickTicksPerBucket", 40, 1, 72_000);
        DERRICK_ENERGY = b.comment("Oil Derrick: FE/t while pumping.")
                .defineInRange("derrickEnergy", 60, 0, 1_000_000);
        REFINERY_ENERGY = b.comment("Refinery: FE/t while distilling.")
                .defineInRange("refineryEnergy", 120, 0, 1_000_000);
        REFINERY_TICKS = b.comment("Refinery: ticks to distil 1000 mB of crude oil (into 500 diesel, 250 rocket fuel, 1 plastic, 2 tar).")
                .defineInRange("refineryTicks", 100, 1, 72_000);
        b.pop();
        b.push("misc");
        BIOGAS_DIGEST_TICKS = b.comment("Biogas Digester: ticks to digest one organic item.")
                .defineInRange("digestTicks", 40, 1, 72_000);
        PUMP_RADIUS = b.comment("Pump: how far (blocks) it reaches for fluid source blocks.")
                .defineInRange("pumpRadius", 12, 1, 64);
        PUMP_INTERVAL = b.comment("Pump: ticks between two source blocks pumped.")
                .defineInRange("pumpInterval", 10, 1, 1200);
        PUMP_ENERGY = b.comment("Pump: FE per source block pumped.")
                .defineInRange("pumpEnergy", 200, 0, 1_000_000);
        PUMP_KEEPS_WATER = b.comment("Pump: whether water source blocks stay (an endless supply, like an infinite spring).")
                .define("pumpKeepsWater", true);
        ELECTROLYZER_ENERGY = b.comment("Electrolyzer: FE per mB of deuterium split from piped water (tritium costs 4x).")
                .defineInRange("electrolyzerFePerMb", 40, 0, 1_000_000);
        b.pop();
        SPEC = b.build();
    }

    private FluidsConfig() {}

    public static int get(ModConfigSpec.IntValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    public static double get(ModConfigSpec.DoubleValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    public static boolean get(ModConfigSpec.BooleanValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }
}
