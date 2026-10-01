package net.juli2kapo.factoryascent.xdim;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance knobs of the interdimensional links, in their own server config file
 * ({@code factoryascent-xdim-server.toml}). Rates are per tick (20 ticks = 1 second).
 */
public final class XdimConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue ENDER_ITEMS, ENDER_FLUID;
    public static final ModConfigSpec.IntValue QUANTUM_ITEMS, QUANTUM_FLUID, QUANTUM_ENERGY;
    public static final ModConfigSpec.IntValue FE_PER_ITEM, FE_PER_BUCKET, ENERGY_LOSS_PERMILLE;
    public static final ModConfigSpec.IntValue QUANTUM_POWER_CAPACITY;
    public static final ModConfigSpec.IntValue LOADED_PER_TEAM;
    public static final ModConfigSpec.BooleanValue ENDER_NETHER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("ender_link");
        ENDER_ITEMS = b.comment("Ender Link: items one link can send per tick.")
                .defineInRange("itemsPerTick", 1, 0, 1024);
        ENDER_FLUID = b.comment("Ender Link: mB of fluid one link can send per tick (50 = one bucket a second).")
                .defineInRange("fluidPerTick", 50, 0, 1_000_000);
        ENDER_NETHER = b.comment("Ender Link: may link the Overworld with the Nether (otherwise it only works inside one dimension).")
                .define("overworldNether", true);
        b.pop();
        b.push("quantum_entangler");
        QUANTUM_ITEMS = b.comment("Quantum Entangler: items one entangler can send per tick.")
                .defineInRange("itemsPerTick", 16, 0, 4096);
        QUANTUM_FLUID = b.comment("Quantum Entangler: mB of fluid one entangler can send per tick.")
                .defineInRange("fluidPerTick", 1000, 0, 10_000_000);
        QUANTUM_ENERGY = b.comment("Quantum Entangler: FE one entangler can send per tick.")
                .defineInRange("energyPerTick", 20_000, 0, Integer.MAX_VALUE / 64);
        FE_PER_ITEM = b.comment("Quantum Entangler: FE each item costs to send, times the distance factor",
                        "(1 inside a dimension, 2 between two surface dimensions, +2 for each end in orbit or on a planet).")
                .defineInRange("fePerItem", 4, 0, 1_000_000);
        FE_PER_BUCKET = b.comment("Quantum Entangler: FE every 1000 mB of fluid costs to send, times the distance factor.")
                .defineInRange("fePerBucket", 40, 0, 1_000_000);
        ENERGY_LOSS_PERMILLE = b.comment("Quantum Entangler: energy lost on the way, in per mille of what is sent, times the distance factor.")
                .defineInRange("energyLossPermille", 10, 0, 500);
        QUANTUM_POWER_CAPACITY = b.comment("Quantum Entangler: FE its operating buffer holds (fed through faces whose energy mode is off).")
                .defineInRange("powerCapacity", 100_000, 1000, Integer.MAX_VALUE / 4);
        b.pop();
        b.push("chunk_loading");
        LOADED_PER_TEAM = b.comment("How many linked endpoints of one team keep their own chunk loaded (0 = none). Endpoints over the limit only work while their chunk is loaded otherwise.")
                .defineInRange("loadedEndpointsPerTeam", 16, 0, 4096);
        b.pop();
        SPEC = b.build();
    }

    private XdimConfig() {}
}
