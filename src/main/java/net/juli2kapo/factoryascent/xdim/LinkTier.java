package net.juli2kapo.factoryascent.xdim;

import java.util.Locale;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The two kinds of endpoint. A link between two endpoints works with the rules of the lower one:
 * an Ender Link and a Quantum Entangler talk like two Ender Links.
 */
public enum LinkTier {
    /** Automation age: items and fluids, inside one dimension or between the Overworld and the Nether. Free. */
    ENDER("ender_link"),
    /** Quantum age: items, fluids and energy, any two dimensions (orbit and planets too), paid in FE. */
    QUANTUM("quantum_entangler");

    public static final LinkTier[] VALUES = values();

    private final String id;

    LinkTier(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LinkTier lower(LinkTier a, LinkTier b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    public boolean carries(Resource r) {
        return r != Resource.ENERGY || this == QUANTUM;
    }

    /** What one endpoint of this tier sends per tick of the resource (items, mB or FE). */
    public int rate(Resource r) {
        return switch (r) {
            case ITEM -> this == ENDER ? XdimConfig.ENDER_ITEMS.get() : XdimConfig.QUANTUM_ITEMS.get();
            case FLUID -> this == ENDER ? XdimConfig.ENDER_FLUID.get() : XdimConfig.QUANTUM_FLUID.get();
            case ENERGY -> this == ENDER ? 0 : XdimConfig.QUANTUM_ENERGY.get();
        };
    }

    /** Whether this tier bridges the two dimensions. */
    public boolean reaches(ResourceKey<Level> a, ResourceKey<Level> b) {
        if (a.equals(b) || this == QUANTUM) return true;
        return XdimConfig.ENDER_NETHER.get() && isOverworldOrNether(a) && isOverworldOrNether(b);
    }

    private static boolean isOverworldOrNether(ResourceKey<Level> d) {
        return d.equals(Level.OVERWORLD) || d.equals(Level.NETHER);
    }

    /**
     * How "far apart" two dimensions are for the Quantum Entangler's FE cost: 1 inside one
     * dimension, 2 between two surface dimensions, plus 2 for each end in orbit or on a planet.
     */
    public static int distanceFactor(ResourceKey<Level> a, ResourceKey<Level> b) {
        if (a.equals(b)) return 1;
        int f = 2;
        if (SpaceRules.isAirless(a)) f += 2;
        if (SpaceRules.isAirless(b)) f += 2;
        return f;
    }

    /** The three things a link moves. */
    public enum Resource {
        ITEM, FLUID, ENERGY;

        public static final Resource[] VALUES = values();

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
