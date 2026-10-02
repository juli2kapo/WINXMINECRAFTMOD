package net.juli2kapo.factoryascent.phone;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance knobs of the Factory Phone, in their own server config file
 * ({@code factoryascent-phone-server.toml}).
 */
public final class PhoneConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue DRAIN_OPEN;
    public static final ModConfigSpec.IntValue DRAIN_STANDBY;
    public static final ModConfigSpec.IntValue MAX_MACHINES;
    public static final ModConfigSpec.IntValue MAX_POWER;
    public static final ModConfigSpec.IntValue MAX_BEACONS;
    public static final ModConfigSpec.IntValue LOCAL_RANGE;
    public static final ModConfigSpec.IntValue CHAT_HISTORY;
    public static final ModConfigSpec.IntValue STOP_SECONDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("phone");
        DRAIN_OPEN = b.comment("FE per second the phone uses while its screen is open (battery: 100,000 FE).")
                .defineInRange("drainOpenPerSecond", 40, 0, 100_000);
        DRAIN_STANDBY = b.comment("FE per second the phone uses in your inventory while it watches machines for alerts.")
                .defineInRange("drainStandbyPerSecond", 4, 0, 100_000);
        MAX_MACHINES = b.comment("How many machines one phone can watch (Machines app).")
                .defineInRange("maxWatchedMachines", 16, 1, 32);
        MAX_POWER = b.comment("How many energy networks one phone can link (Power app).")
                .defineInRange("maxPowerNetworks", 4, 1, 16);
        MAX_BEACONS = b.comment("How many Ender Beacons one phone can link (Recall app: no Recall Charm needed).")
                .defineInRange("maxRecallBeacons", 16, 1, 24);
        LOCAL_RANGE = b.comment("Blocks within which the phone reaches linked machines and cables without uplink signal (short-range radio).",
                        "Farther away (same dimension) it needs your team's Uplink Satellite overhead.")
                .defineInRange("localRange", 48, 0, 1024);
        CHAT_HISTORY = b.comment("Team chat messages kept per team.")
                .defineInRange("chatHistory", 50, 5, 500);
        STOP_SECONDS = b.comment("Seconds a watched machine must stay stopped before the phone raises an alert (avoids alerts between batches).")
                .defineInRange("stopAlertSeconds", 3, 1, 600);
        b.pop();
        SPEC = b.build();
    }

    private PhoneConfig() {}
}
