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
    public static final ModConfigSpec.IntValue ANCHOR_RADIUS;
    public static final ModConfigSpec.IntValue ANCHORS_PER_PLAYER;
    public static final ModConfigSpec.IntValue RECALL_SECONDS;
    public static final ModConfigSpec.IntValue RECALL_COOLDOWN_SECONDS;
    public static final ModConfigSpec.BooleanValue RECALL_CROSS_DIMENSION;
    public static final ModConfigSpec.DoubleValue SIZE_RAY_MIN_SCALE;
    public static final ModConfigSpec.DoubleValue SIZE_RAY_MAX_SCALE;

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
        b.push("ender");
        ANCHOR_RADIUS = b.comment("Ender Anchor: chunks loaded around it (0 = its own chunk, 1 = 3x3, 2 = 5x5).")
                .defineInRange("anchorRadius", 1, 0, 4);
        ANCHORS_PER_PLAYER = b.comment("How many Ender Anchors one player may place (0 = no limit).")
                .defineInRange("anchorsPerPlayer", 4, 0, 1000);
        RECALL_SECONDS = b.comment("Recall Charm: seconds you must channel before teleporting home.")
                .defineInRange("recallSeconds", 5, 1, 60);
        RECALL_COOLDOWN_SECONDS = b.comment("Seconds before a Recall Charm can be used again.")
                .defineInRange("recallCooldownSeconds", 60, 0, 3600);
        RECALL_CROSS_DIMENSION = b.comment("Whether a Recall Charm can bring you home from another dimension.")
                .define("recallCrossDimension", false);
        b.pop();
        b.push("mob_tools");
        SIZE_RAY_MIN_SCALE = b.comment("Smallest size the Minimizer Ray can shrink a creature to (1.0 = normal; vanilla allows down to 0.0625).")
                .defineInRange("sizeRayMinScale", 0.25, 0.0625, 1.0);
        SIZE_RAY_MAX_SCALE = b.comment("Largest size the Maximizer Ray can grow a creature to (1.0 = normal; vanilla allows up to 16).")
                .defineInRange("sizeRayMaxScale", 4.0, 1.0, 16.0);
        b.pop();
        SPEC = b.build();
    }

    private Config() {}
}
