package net.juli2kapo.factoryascent.space.planet;

import net.juli2kapo.factoryascent.space.SpaceRules;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The shuttle's navigation rules, kept free of the world so game tests can check them: where a
 * shuttle can fly to, what the trip costs in fuel and how long the cruise takes.
 *
 * <p>Costs and times climb a "ladder": Earth orbit (0), the Moon, Mars, then Io (config values,
 * see {@link Costs}). A trip costs the difference between the two rungs, so the Moon is a cheap
 * stepping stone and planet-to-planet hops cost less than going back to Earth first. An Ion Drive
 * in the hold halves both.
 */
public final class Navigation {
    private Navigation() {}

    /** Where a shuttle can go (and where it is, in space terms). */
    public enum Destination {
        EARTH_ORBIT(null),
        MOON(Planet.MOON),
        MARS(Planet.MARS),
        IO(Planet.IO);

        public final @Nullable Planet planet;

        Destination(@Nullable Planet planet) {
            this.planet = planet;
        }

        public ResourceKey<Level> dimension() {
            return planet == null ? SpaceRules.ORBIT : planet.key;
        }

        public Component displayName() {
            return planet == null ? Component.translatable("planet.factoryascent.earth_orbit") : planet.displayName();
        }

        public static Destination byIndex(int index) {
            Destination[] all = values();
            return all[Math.floorMod(index, all.length)];
        }

        /** The destination a dimension is (orbit or a planet), or null anywhere else (the Overworld...). */
        public static @Nullable Destination at(ResourceKey<Level> dimension) {
            if (dimension.equals(SpaceRules.ORBIT)) return EARTH_ORBIT;
            Planet p = Planet.of(dimension);
            return p == null ? null : values()[p.ordinal() + 1];
        }
    }

    /**
     * Rung heights of the ladder: fuel units and cruise ticks to reach each destination from Earth
     * orbit (index = {@link Destination#ordinal()}, Earth orbit is 0).
     */
    public record Costs(int[] fuel, int[] ticks) {
        public static final Costs DEFAULT = new Costs(new int[] {0, 3000, 7000, 11000}, new int[] {0, 160, 300, 440});
    }

    /** Fuel units a trip costs (0 for staying put). */
    public static int fuelCost(Destination from, Destination to, Costs costs, boolean ionDrive) {
        int c = Math.abs(costs.fuel()[to.ordinal()] - costs.fuel()[from.ordinal()]);
        return ionDrive ? (c + 1) / 2 : c;
    }

    /** Cruise ticks of a trip: at least five seconds of warp for any real trip. */
    public static int travelTicks(Destination from, Destination to, Costs costs, boolean ionDrive) {
        if (from == to) return 0;
        int t = Math.abs(costs.ticks()[to.ordinal()] - costs.ticks()[from.ordinal()]);
        if (ionDrive) t /= 2;
        return Math.max(100, t);
    }

    /** Why a trip can't start, or {@link Check#OK}. */
    public enum Check {
        OK, NOT_IN_SPACE, SAME_PLACE, NO_FUEL, BUSY;

        public String key() {
            return "message.factoryascent.nav." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * Whether a shuttle at {@code here} (null: not in space, e.g. the Overworld) with {@code fuel}
     * units can start the trip to {@code to}.
     */
    public static Check check(@Nullable Destination here, Destination to, int fuel, boolean cruising, Costs costs, boolean ionDrive) {
        if (cruising) return Check.BUSY;
        if (here == null) return Check.NOT_IN_SPACE;
        if (here == to) return Check.SAME_PLACE;
        if (fuel < fuelCost(here, to, costs, ionDrive)) return Check.NO_FUEL;
        return Check.OK;
    }

    /**
     * Where a shuttle climbing out of a planet's sky goes: the selected destination if it is
     * another place it can afford, otherwise Earth orbit; null when it can't even afford that
     * (it stays: the sky has a ceiling).
     */
    public static @Nullable Destination leavingPlanet(Destination here, Destination selected, int fuel, Costs costs, boolean ionDrive) {
        if (selected != here && fuel >= fuelCost(here, selected, costs, ionDrive)) return selected;
        if (here != Destination.EARTH_ORBIT && fuel >= fuelCost(here, Destination.EARTH_ORBIT, costs, ionDrive)) {
            return Destination.EARTH_ORBIT;
        }
        return null;
    }

    /** Progress 0..1 of a cruise from its ticks left and total. */
    public static float progress(int left, int total) {
        if (total <= 0) return 1f;
        return Math.max(0f, Math.min(1f, 1f - left / (float) total));
    }
}
