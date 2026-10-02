package net.juli2kapo.factoryascent.capsule;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Balance of the capsule machines ({@code factoryascent-capsule-server.toml}). */
public final class CapsuleConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue RESIZE_FE;
    public static final ModConfigSpec.IntValue RESIZE_SECONDS;
    public static final ModConfigSpec.DoubleValue HEAL_PER_SECOND;
    public static final ModConfigSpec.IntValue HEAL_FE_PER_HEALTH;
    public static final ModConfigSpec.IntValue RELEASER_MAX_DISTANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("size_chamber");
        RESIZE_FE = b.comment("Size Chamber: FE to resize the captured mob once (spread over the resize time).")
                .defineInRange("resizeEnergy", 20_000, 0, 10_000_000);
        RESIZE_SECONDS = b.comment("Size Chamber: seconds one resize takes.")
                .defineInRange("resizeSeconds", 3, 1, 600);
        HEAL_PER_SECOND = b.comment("Size Chamber: health points restored per second to the captured mob (a Wither has 300).")
                .defineInRange("healPerSecond", 10.0, 0.05, 1000.0);
        HEAL_FE_PER_HEALTH = b.comment("Size Chamber: FE per health point restored.")
                .defineInRange("healEnergyPerHealth", 200, 0, 1_000_000);
        b.pop();
        b.push("mob_releaser");
        RELEASER_MAX_DISTANCE = b.comment("Mob Releaser: farthest the release point can be set in front of it, in blocks.")
                .defineInRange("maxDistance", 16, 1, 64);
        b.pop();
        SPEC = b.build();
    }

    private CapsuleConfig() {}
}
