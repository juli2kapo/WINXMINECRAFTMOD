package net.juli2kapo.factoryascent.space.gravity;

import net.minecraft.core.Direction;

/**
 * Added to every {@link net.minecraft.world.Entity} by {@code EntityMixin}: the entity's own
 * gravity direction (only players with Magnetic Boots ever get anything but DOWN) and whether its
 * delta movement is momentarily held in the local frame (inside {@code travel} / {@code jumpFromGround}).
 * Use {@link Gravity} rather than calling these directly.
 */
public interface GravityHolder {
    Direction factoryascent$gravity();

    /** Sets the direction only (no box, position or sync update): see {@link Gravity#change}. */
    void factoryascent$setGravityRaw(Direction gravity);

    /** The gravity the delta movement is currently expressed in, or null while it is in world coordinates. */
    Direction factoryascent$localFrame();

    void factoryascent$setLocalFrame(Direction frame);
}
