package net.juli2kapo.factoryascent.power;

import net.minecraft.world.level.Level;

/** Shared rules of the power ladder. */
public final class PowerRules {
    private PowerRules() {}

    /** No air here (orbit, other airless worlds): nothing can burn, but the RTG and solar still work. */
    public static boolean isAirless(Level level) {
        return net.juli2kapo.factoryascent.space.SpaceRules.isAirless(level);
    }
}
