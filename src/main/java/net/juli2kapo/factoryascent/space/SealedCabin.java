package net.juli2kapo.factoryascent.space;

import net.minecraft.world.entity.Entity;

/**
 * A vehicle with its own air: players riding an entity that implements this (anywhere up its
 * vehicle chain) breathe in airless dimensions without a suit and use no suit oxygen. Ships,
 * capsules and the rocket seat implement it; {@link #isSealed()} lets a vehicle open its hatch.
 *
 * @see SpaceRules#inSealedCabin(Entity)
 */
public interface SealedCabin {
    /** Whether the cabin currently holds air (default: always). */
    default boolean isSealed() {
        return true;
    }
}
