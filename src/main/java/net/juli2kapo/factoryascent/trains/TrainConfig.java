package net.juli2kapo.factoryascent.trains;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config of the trains, in its own file ({@code factoryascent-trains-server.toml}, per world
 * under serverconfig/). Every getter falls back to the default before the config is loaded (game
 * tests, early ticks).
 */
public final class TrainConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue STEAM_SPEED;
    public static final ModConfigSpec.DoubleValue DIESEL_SPEED;
    public static final ModConfigSpec.DoubleValue ACCELERATION;
    public static final ModConfigSpec.DoubleValue STEAM_FUEL_USE;
    public static final ModConfigSpec.IntValue STEAM_WATER_PER_TICK;
    public static final ModConfigSpec.IntValue DIESEL_FE_PER_TICK;
    public static final ModConfigSpec.IntValue DIESEL_FE_PER_MB;
    public static final ModConfigSpec.IntValue MAX_TRAIN_LENGTH;
    public static final ModConfigSpec.BooleanValue DRIVERLESS;
    public static final ModConfigSpec.BooleanValue CARGO_IN_ITEM;
    public static final ModConfigSpec.IntValue STATION_ITEMS;
    public static final ModConfigSpec.IntValue STATION_FLUID;
    public static final ModConfigSpec.IntValue STATION_ENERGY;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("locomotives");
        STEAM_SPEED = b.comment("Top speed of the Steam Locomotive in blocks per tick (0.5 = 36 km/h; a minecart tops out at 0.4).")
                .defineInRange("steamTopSpeed", 0.5, 0.05, 3.0);
        DIESEL_SPEED = b.comment("Top speed of the Diesel-Electric Locomotive in blocks per tick (0.9 = 65 km/h).")
                .defineInRange("dieselTopSpeed", 0.9, 0.05, 3.0);
        ACCELERATION = b.comment("Multiplier on how fast locomotives speed up (heavier trains always pull away slower).")
                .defineInRange("acceleration", 1.0, 0.1, 10.0);
        STEAM_FUEL_USE = b.comment("Multiplier on the fuel the Steam Locomotive burns (one coal lasts 80 s at full throttle with 1.0).")
                .defineInRange("steamFuelUse", 1.0, 0.0, 100.0);
        STEAM_WATER_PER_TICK = b.comment("mB of water the Steam Locomotive boils per tick at full throttle.")
                .defineInRange("steamWaterPerTick", 2, 0, 1000);
        DIESEL_FE_PER_TICK = b.comment("FE per tick the Diesel-Electric Locomotive's traction motors draw at full throttle.")
                .defineInRange("dieselFePerTick", 150, 0, 100_000);
        DIESEL_FE_PER_MB = b.comment("FE its on-board diesel generator makes from 1 mB of diesel (the Diesel Generator makes 300).")
                .defineInRange("dieselFePerMb", 300, 1, 100_000);
        DRIVERLESS = b.comment("Locomotives keep running at their throttle setting with nobody aboard (for automated lines).")
                .define("driverless", true);
        b.pop();
        b.push("trains");
        MAX_TRAIN_LENGTH = b.comment("Most vehicles (locomotives + wagons) one train may have.")
                .defineInRange("maxTrainLength", 12, 2, 64);
        CARGO_IN_ITEM = b.comment("Breaking a locomotive or wagon keeps its inventory, tank and charge inside the dropped item.",
                        "If false the cargo spills on the ground instead (fluids are lost).")
                .define("cargoInItem", true);
        b.pop();
        b.push("station");
        STATION_ITEMS = b.comment("Items a Train Station moves per wagon every 4 ticks.").defineInRange("itemsPerTransfer", 16, 1, 64 * 54);
        STATION_FLUID = b.comment("mB a Train Station moves per wagon every 4 ticks.").defineInRange("fluidPerTransfer", 1000, 1, 1_000_000);
        STATION_ENERGY = b.comment("FE a Train Station moves into a locomotive every 4 ticks.").defineInRange("energyPerTransfer", 20_000, 1, 10_000_000);
        b.pop();
        SPEC = b.build();
    }

    private TrainConfig() {}

    private static boolean loaded() {
        return SPEC.isLoaded();
    }

    public static double steamSpeed() {
        return loaded() ? STEAM_SPEED.get() : 0.5;
    }

    public static double dieselSpeed() {
        return loaded() ? DIESEL_SPEED.get() : 0.9;
    }

    public static double acceleration() {
        return loaded() ? ACCELERATION.get() : 1.0;
    }

    public static double steamFuelUse() {
        return loaded() ? STEAM_FUEL_USE.get() : 1.0;
    }

    public static int steamWaterPerTick() {
        return loaded() ? STEAM_WATER_PER_TICK.get() : 2;
    }

    public static int dieselFePerTick() {
        return loaded() ? DIESEL_FE_PER_TICK.get() : 150;
    }

    public static int dieselFePerMb() {
        return loaded() ? DIESEL_FE_PER_MB.get() : 300;
    }

    public static boolean driverless() {
        return !loaded() || DRIVERLESS.get();
    }

    public static int maxTrainLength() {
        return loaded() ? MAX_TRAIN_LENGTH.get() : 12;
    }

    public static boolean cargoInItem() {
        return !loaded() || CARGO_IN_ITEM.get();
    }

    public static int stationItems() {
        return loaded() ? STATION_ITEMS.get() : 16;
    }

    public static int stationFluid() {
        return loaded() ? STATION_FLUID.get() : 1000;
    }

    public static int stationEnergy() {
        return loaded() ? STATION_ENERGY.get() : 20_000;
    }
}
