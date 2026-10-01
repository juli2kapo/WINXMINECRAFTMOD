package net.juli2kapo.factoryascent.outpost;

import net.juli2kapo.factoryascent.space.planet.Navigation;
import org.jspecify.annotations.Nullable;

/**
 * The Ascent Module's rules, free of the world so game tests can check them: what the climb from
 * a planet back to Earth orbit costs, and which of the fuel a player carries pays for it.
 *
 * <p>The cost follows the shuttle's navigation ladder ({@link Navigation#fuelCost}, planet to Earth
 * orbit) times {@link OutpostConfig#ASCENT_FUEL_FRACTION}: a one-seat capsule is lighter than a
 * shuttle. With the defaults: the Moon 1500 units (2 Rocket Fuel, or 1 Hydrolox Cell or Helium-3
 * Fuel Cell), Mars 3500, Io 5500.
 */
public final class Ascent {
    private Ascent() {}

    /** Fuel units to climb from {@code here} (a planet) to Earth orbit; 0 anywhere that isn't a planet. */
    public static int fuelNeeded(Navigation.@Nullable Destination here, Navigation.Costs costs, double fraction) {
        if (here == null || here.planet == null) return 0;
        int full = Navigation.fuelCost(here, Navigation.Destination.EARTH_ORBIT, costs, false);
        return (int) Math.ceil(full * Math.max(0.0, fraction));
    }

    /**
     * Which fuel to burn: {@code values[i]} units per item of fuel kind {@code i}, {@code counts[i]}
     * items carried. Spends the smallest fuels first (Rocket Fuel before Hydrolox before Helium-3
     * cells, when listed that way), so the precious cells are kept when cheaper fuel covers the
     * cost. Returns the items to take of each kind, or null if everything carried isn't enough.
     */
    public static int @Nullable [] pay(int needed, int[] values, int[] counts) {
        int[] take = new int[values.length];
        if (needed <= 0) return take;
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(values[a], values[b]));
        int paid = 0;
        for (int i : order) {
            if (values[i] <= 0) continue;
            while (take[i] < counts[i] && paid < needed) {
                take[i]++;
                paid += values[i];
            }
            if (paid >= needed) break;
        }
        if (paid < needed) return null;
        // a bigger cell may have made smaller items unnecessary: hand those back
        for (int k = order.length - 1; k >= 0; k--) {
            int i = order[k];
            while (take[i] > 0 && paid - values[i] >= needed) {
                take[i]--;
                paid -= values[i];
            }
        }
        return take;
    }

    /** Total units a set of carried fuel is worth. */
    public static int worth(int[] values, int[] counts) {
        long sum = 0;
        for (int i = 0; i < values.length; i++) sum += (long) Math.max(0, values[i]) * counts[i];
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /** Upward speed of the climbing capsule at tick {@code t} of {@code total}: a quick build-up to full thrust. */
    public static double climbSpeed(int t, int total) {
        if (total <= 0) return 0;
        return Math.min(1.4, 0.15 + 1.25 * Math.min(1.0, t / (total * 0.5)));
    }
}
