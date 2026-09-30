package net.juli2kapo.factoryascent.dyson;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance knobs of the Dyson Sphere megaproject, in their own server config file
 * ({@code factoryascent-dyson-server.toml}, synced to clients like the main config).
 */
public final class DysonConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue SWARM_TARGET;
    public static final ModConfigSpec.IntValue FE_PER_COLLECTOR;
    public static final ModConfigSpec.IntValue RECEIVER_MAX_OUTPUT;
    public static final ModConfigSpec.IntValue LAUNCH_ENERGY;
    public static final ModConfigSpec.IntValue LAUNCH_TICKS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("dyson");
        SWARM_TARGET = b.comment("Solar Collectors a team needs in solar orbit for a complete Dyson Sphere (100%).")
                .defineInRange("swarmTarget", 1000, 10, 1_000_000);
        FE_PER_COLLECTOR = b.comment("FE per tick each collector beams down in full sunlight, shared by the team's Dyson Receivers.")
                .defineInRange("fePerCollector", 64, 1, 1_000_000);
        RECEIVER_MAX_OUTPUT = b.comment("Most FE per tick one Dyson Receiver can take in (build more receivers for a bigger swarm).")
                .defineInRange("receiverMaxOutput", 32768, 1, Integer.MAX_VALUE / 4);
        LAUNCH_ENERGY = b.comment("Mass Driver: FE one launch takes (it charges its coils up to this before each shot).")
                .defineInRange("massDriverEnergyPerLaunch", 400_000, 0, Integer.MAX_VALUE / 4);
        LAUNCH_TICKS = b.comment("Mass Driver: fewest ticks between two launches (20 ticks = 1 second).")
                .defineInRange("massDriverTicksPerLaunch", 100, 1, 72000);
        b.pop();
        SPEC = b.build();
    }

    private DysonConfig() {}
}
