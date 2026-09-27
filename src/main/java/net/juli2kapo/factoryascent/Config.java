package net.juli2kapo.factoryascent;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side balance knobs. Synced to clients by NeoForge, editable per world. */
public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue MACHINE_SPEED;
    public static final ModConfigSpec.DoubleValue MACHINE_ENERGY;
    public static final ModConfigSpec.DoubleValue MINER_RATE;
    public static final ModConfigSpec.DoubleValue GENERATOR_OUTPUT;
    public static final ModConfigSpec.BooleanValue MINERS_NEED_POWER;
    public static final ModConfigSpec.IntValue MINER_MAX_RADIUS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("balance");
        MACHINE_SPEED = b.comment("Multiplier on the work speed of every processing machine.")
                .defineInRange("machineSpeed", 1.0, 0.05, 100.0);
        MACHINE_ENERGY = b.comment("Multiplier on the energy every machine uses.")
                .defineInRange("machineEnergy", 1.0, 0.0, 100.0);
        MINER_RATE = b.comment("Multiplier on how fast miners dig ore.")
                .defineInRange("minerRate", 1.0, 0.05, 100.0);
        GENERATOR_OUTPUT = b.comment("Multiplier on the energy every generator produces.")
                .defineInRange("generatorOutput", 1.0, 0.05, 100.0);
        MINERS_NEED_POWER = b.comment("If false, miners run without energy (handy for relaxed servers).")
                .define("minersNeedPower", true);
        b.pop();
        MINER_MAX_RADIUS = b.comment("Upper limit on miner radius (tiers use 5/8/12/16/24). Lower it on busy servers.")
                .defineInRange("minerMaxRadius", 24, 1, 64);
        SPEC = b.build();
    }

    private Config() {}
}
