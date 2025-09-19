package net.juli2kapo.minewinx.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class LightRayEntity extends ThrowableProjectile {

    private float damage = 2.0F; // Default damage value

    // --- Constructors ---

    public LightRayEntity(EntityType<? extends LightRayEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public LightRayEntity(Level level, LivingEntity owner) {
        // Replace 'ModEntities.LIGHT_RAY.get()' with your actual registered entity type
        super(ModEntities.LIGHT_RAY.get(), owner, level);
        this.setNoGravity(true);
    }


    /**
     * A public method to allow your power system to set the damage for this projectile.
     * @param damage The amount of damage this light ray should deal.
     */
    public void setDamage(float damage) {
        this.damage = damage;
    }


    // --- Core Logic ---

    @Override
    protected void defineSynchedData() {

    }

    @Override
    public void tick() {
        super.tick(); // Handles movement physics
    }


    // --- Collision Handling ---

    /**
     * This is the main collision handler for ThrowableProjectiles. It's called for both entities and blocks.
     * @param result The result of the collision.
     */
    @Override
    protected void onHit(HitResult result) {
        super.onHit(result); // Let the parent class handle some base logic

        // Check if we hit an entity
        if (result.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHitResult = (EntityHitResult) result;
            var entity = entityHitResult.getEntity();

            // Get the owner (the player who shot this) to prevent self-damage and correctly attribute the damage
            var owner = this.getOwner();

            // Make sure we didn't hit ourselves
            if (owner != entity) {
                // This is where you deal damage MANUALLY.
                // It uses a built-in damage source for thrown projectiles. The damage value is what we set!
                entity.hurt(this.level().damageSources().thrown(this, owner), this.damage);
            }
        }

        // No matter what we hit (entity or block), the projectile should be removed.
        // The !this.level().isClientSide() check ensures we only remove it on the server, which then syncs to the client.
        if (!this.level().isClientSide()) {
            this.discard();
        }
    }
}