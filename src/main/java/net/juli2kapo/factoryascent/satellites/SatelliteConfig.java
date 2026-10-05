package net.juli2kapo.factoryascent.satellites;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Knobs of the satellites flying in Earth orbit, in their own server config file
 * ({@code factoryascent-satellites-server.toml}, synced to clients).
 */
public final class SatelliteConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PHYSICAL;
    public static final ModConfigSpec.IntValue ALTITUDE;
    public static final ModConfigSpec.BooleanValue WRECK_DROPS_SHIP;
    public static final ModConfigSpec.IntValue WRECK_SCRAP;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("satellites");
        PHYSICAL = b.comment("The Overworld's satellites really fly in Earth orbit (factoryascent:orbit) as entities you can reach",
                        "with a shuttle and crash into (both are destroyed). Off: they are only drawn in the sky, as before.")
                .define("physicalSatellites", true);
        ALTITUDE = b.comment("Lowest height of the satellites' orbits in factoryascent:orbit (they fly up to 60 blocks higher).",
                        "Stations sit at y=100 and shuttles arrive at y=112.")
                .defineInRange("orbitAltitude", 320, 140, 2000);
        WRECK_DROPS_SHIP = b.comment("A ship that crashes into a satellite drops its own item (like a ship broken by hand).",
                        "Off: the hull is lost; only its cargo and some Scrap float away from the wreck.")
                .define("wreckDropsShip", false);
        WRECK_SCRAP = b.comment("Scrap left floating where a ship and a satellite collided.")
                .defineInRange("wreckScrap", 6, 0, 64);
        b.pop();
        SPEC = b.build();
    }

    private SatelliteConfig() {}

    public static boolean physical() {
        return !SPEC.isLoaded() || PHYSICAL.get();
    }

    public static int altitude() {
        return SPEC.isLoaded() ? ALTITUDE.get() : 320;
    }

    public static boolean wreckDropsShip() {
        return SPEC.isLoaded() && WRECK_DROPS_SHIP.get();
    }

    public static int wreckScrap() {
        return SPEC.isLoaded() ? WRECK_SCRAP.get() : 6;
    }
}
