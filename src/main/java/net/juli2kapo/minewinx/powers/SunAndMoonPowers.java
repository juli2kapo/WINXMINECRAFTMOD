package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.SunRay;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SunAndMoonPowers {

    private static final Map<UUID, CastingState> activeCasters = new ConcurrentHashMap<>();
    private static final List<PendingArrow> pendingArrows = new CopyOnWriteArrayList<>();
    private static final Random random = new Random();

    public static void castSunRay(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) level;

        // --- 1. Define Ability Properties ---
        double maxRange = 60.0 + (stage * 20.0);
        float damage;
        float blastRadius = 1.5F + (stage * 0.5F);
        DustParticleOptions particleEffect;

        if (level.isDay()) {
            damage = 10.0F + (stage * 5.0F);
            particleEffect = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.2f), 1.5f);
        } else {
            damage = 4.0F + (stage * 2.0F);
            particleEffect = new DustParticleOptions(new Vector3f(0.8f, 0.8f, 1.0f), 1.5f);
        }

        // --- 2. Perform Combined Raycast ---
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(maxRange));

        BlockHitResult blockHit = level.clip(new ClipContext(
                eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
        ));

        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(maxRange)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level, player, eyePos, endPos, searchBox, (entity) -> !entity.isSpectator() && entity.isPickable(), (float) (maxRange * maxRange)
        );

        Vec3 targetPos;
        if (entityHit != null) {
            double entityDistSq = eyePos.distanceToSqr(entityHit.getLocation());
            if (blockHit.getType() == HitResult.Type.MISS || entityDistSq < eyePos.distanceToSqr(blockHit.getLocation())) {
                targetPos = entityHit.getLocation();
            } else {
                targetPos = blockHit.getLocation();
            }
        } else {
            targetPos = blockHit.getType() == HitResult.Type.MISS ? endPos : blockHit.getLocation();
        }

        // --- 3. Summon the SunRay Entity ---
        SunRay sunRay = new SunRay(ModEntities.SUN_RAY.get(), level);
        sunRay.setPos(targetPos);
        sunRay.setDamage(damage);
        if (player instanceof ServerPlayer serverPlayer) {
            sunRay.setCause(serverPlayer);
        }
        serverLevel.addFreshEntity(sunRay);

        // --- 4. Spawn Initial Impact Particles ---
        serverLevel.sendParticles(particleEffect, targetPos.x, targetPos.y + 1.0, targetPos.z, 50, blastRadius, 0.5, blastRadius, 0.2);
    }


    public static void castLightBarrage(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0 || activeCasters.containsKey(player.getUUID())) {
            // Do nothing if stage is invalid or player is already casting
            return;
        }

        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }

        // --- 2. Define Ability Properties based on Stage ---
        int durationSeconds;
        int raysPerSecond;
        float damagePerRay = 2.0F + stage;

        switch (stage) {
            case 1:
                durationSeconds = 3;
                raysPerSecond = 4;
                break;
            case 2:
                durationSeconds = 5;
                raysPerSecond = 8;
                break;
            default: // Stage 3 and above
                durationSeconds = 10;
                raysPerSecond = 12;
                break;
        }

        // --- 3. Create and Store Casting State ---
        CastingState state = new CastingState(player, stage, durationSeconds, raysPerSecond, damagePerRay);
        activeCasters.put(player.getUUID(), state);
    }

    /**
     * This method should be called from a ServerTickEvent handler in your mod.
     * It processes all active barrages each tick.
     */
    public static void onServerTick(ServerLevel serverLevel) {
        if (activeCasters.isEmpty() && pendingArrows.isEmpty()) {
            return;
        }

        // Use an iterator to safely remove elements while looping
        activeCasters.entrySet().removeIf(entry -> {
            CastingState state = entry.getValue();
            Player player = serverLevel.getPlayerByUUID(state.casterUUID);

            // --- 4. End Condition Check ---
            // If player is null (logged off, changed dimension) or timer is up, remove them.
            if (player == null || state.updateTick()) {
                return true; // remove from map
            }

            // --- 5. Spawn Logic ---
            state.raysToSpawnThisTick += state.spawnRatePerTick;
            while (state.raysToSpawnThisTick >= 1.0f) {
                spawnSingleRay(player, state);
                state.raysToSpawnThisTick -= 1.0f;
            }

            return false; // keep in map
        });

        // Process pending arrows to redirect them mid-flight
        pendingArrows.removeIf(pending -> {
            // Update the pending arrow state
            pending.updateTick();

            Player caster = serverLevel.getPlayerByUUID(pending.casterUUID);
            Entity arrowEntity = serverLevel.getEntity(pending.arrowUUID);

            if (caster == null || !(arrowEntity instanceof SpectralArrow arrow) || !arrow.isAlive()) {
                return true; // Remove if caster is gone or arrow is dead
            }

            if (pending.isWaitingToStart()) {
                return false; // Still waiting for initial delay, keep it
            }

            if (!pending.isRedirecting()) {
                // Start redirecting - calculate target direction
                Vec3 eyePos = caster.getEyePosition();
                Vec3 lookVec = caster.getViewVector(1.0F);
                double convergenceDistance = 40.0;
                Vec3 focalPoint = eyePos.add(lookVec.scale(convergenceDistance));
                Vec3 targetDirection = focalPoint.subtract(arrow.position()).normalize();

                pending.startRedirecting(arrow.getDeltaMovement(), targetDirection);
            }

            // Perform gradual redirection with proper client sync
            Vec3 newVelocity = pending.updateVelocity();

            // Use shoot() method instead of setDeltaMovement for better client sync
            // But scale down the velocity change to make it more gradual
            Vec3 currentVel = arrow.getDeltaMovement();
            Vec3 velocityDiff = newVelocity.subtract(currentVel);

            // Apply only a fraction of the velocity change each tick for smoother transition
            double maxChangePerTick = 0.1; // Adjust this value to control smoothness
            double changeAmount = Math.min(velocityDiff.length(), maxChangePerTick);

            if (velocityDiff.length() > 0.001) { // Avoid division by zero
                Vec3 appliedVelocity = currentVel.add(velocityDiff.normalize().scale(changeAmount));

                // Force client synchronization by marking the entity as dirty
                arrow.setDeltaMovement(appliedVelocity);
                arrow.hasImpulse = true; // This forces client sync
            }

            // Remove when redirection is complete
            return pending.isComplete();
        });
    }

    /**
     * Spawns a single projectile ray for the Light Barrage ability.
     * The ray spawns behind the player with an initial outward trajectory,
     * then is later redirected to the player's target by the onServerTick method.
     */
    private static void spawnSingleRay(Player player, CastingState state) {
        ServerLevel serverLevel = (ServerLevel) player.level();

        // 1. Define the spawn area behind the player. (This part is perfect)
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 spawnCenter = eyePos.add(lookVec.scale(-state.distanceBehind));
        Vec3 rightVec = new Vec3(-lookVec.z, 0, lookVec.x).normalize(); // Vector to player's right

        double totalWidth = 3.0;
        double randomHOffset = (random.nextDouble() - 0.5) * totalWidth;
        double randomVOffset = (random.nextDouble() - 0.5) * 1.5;
        Vec3 spawnPos = spawnCenter.add(rightVec.scale(randomHOffset)).add(0, randomVOffset, 0);

        // 2. Create and configure the spectral arrow. (This is also fine)
        SpectralArrow arrow = new SpectralArrow(EntityType.SPECTRAL_ARROW, serverLevel);
        arrow.setPos(spawnPos);
        arrow.setOwner(player);
        arrow.setBaseDamage(state.damagePerRay);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

        // 3. Set an initial trajectory - sideways relative to the player's view.
        // This is the section we are changing.

        // Get the vector pointing "up" relative to the player's view using a cross product.
        Vec3 upVec = lookVec.cross(rightVec).normalize();

        double angleDeg;
        if (random.nextBoolean()) {
            // First sector: 150° to 210° (60° range)
            angleDeg = 150.0 + random.nextDouble() * 60.0;
        } else {
            // Second sector: 330° to 30° (60° range, wrapping around)
            // We can handle this as 330° to 390° where 390° = 30°
            double rawAngle = 330.0 + random.nextDouble() * 60.0;
            // Normalize to 0-360 range
            angleDeg = rawAngle >= 360.0 ? rawAngle - 360.0 : rawAngle;
        }
        double angle = Math.toRadians(angleDeg);
        Vec3 initialDirection = rightVec.scale(Math.cos(angle)).add(upVec.scale(Math.sin(angle))).normalize();

        // A low speed and slight inaccuracy makes the initial spread look more natural.
        arrow.shoot(initialDirection.x, initialDirection.y, initialDirection.z, 0.4F, 0.0F);
        serverLevel.addFreshEntity(arrow);

        // 4. Schedule the arrow to be redirected. (This part is perfect)
        int delayTicks = 1 + random.nextInt(2); // 0.05 to 0.1 seconds delay.
        pendingArrows.add(new PendingArrow(arrow, player, delayTicks, state.velocity));
    }

    // --- Helper Class to store the state of an ongoing cast ---
    private static class CastingState {
        final UUID casterUUID;
        final int stage;
        final float damagePerRay;

        // Ability constants
        final float velocity = 8F;
        final float distanceBehind = 1F;

        // Timing and rate variables
        private int ticksRemaining;
        final float spawnRatePerTick;
        float raysToSpawnThisTick = 0; // Accumulator for spawning

        CastingState(Player player, int stage, int durationSeconds, int raysPerSecond, float damage) {
            this.casterUUID = player.getUUID();
            this.stage = stage;
            this.damagePerRay = damage;
            this.ticksRemaining = durationSeconds * 20; // 20 ticks per second
            this.spawnRatePerTick = (float) raysPerSecond / 20.0f;
        }

        /**
         * Updates the timer.
         * @return true if the ability has finished, false otherwise.
         */
        boolean updateTick() {
            this.ticksRemaining--;
            return this.ticksRemaining <= 0;
        }
    }

    // --- Helper Class to track arrows that need to be redirected ---
    private static class PendingArrow {
        final UUID arrowUUID;
        final UUID casterUUID;
        final float finalVelocity;

        // State tracking
        private int ticksUntilRedirect;
        private boolean redirecting = false;
        private int redirectionTicks = 0;
        private final int maxRedirectionTicks = 10; // How many ticks to spread the redirection over

        // Velocity interpolation
        private Vec3 startVelocity;
        private Vec3 targetVelocity;

        PendingArrow(SpectralArrow arrow, Player caster, int delayTicks, float finalVelocity) {
            this.arrowUUID = arrow.getUUID();
            this.casterUUID = caster.getUUID();
            this.ticksUntilRedirect = delayTicks;
            this.finalVelocity = finalVelocity;
            this.targetVelocity = Vec3.ZERO;
        }

        /**
         * Updates the timer for the initial delay.
         */
        void updateTick() {
            if (this.ticksUntilRedirect > 0) {
                this.ticksUntilRedirect--;
            }
        }

        boolean isWaitingToStart() {
            return this.ticksUntilRedirect > 0;
        }

        boolean isRedirecting() {
            return this.redirecting;
        }

        void startRedirecting(Vec3 currentVelocity, Vec3 targetDirection) {
            this.redirecting = true;
            this.startVelocity = currentVelocity;
            this.targetVelocity = targetDirection.scale(this.finalVelocity);
            this.redirectionTicks = 0;
        }

        void updateTargetDirection(Vec3 targetDirection) {
            this.targetVelocity = targetDirection.scale(this.finalVelocity);
        }

        Vec3 updateVelocity() {
            if (!this.redirecting) {
                return Vec3.ZERO;
            }

            this.redirectionTicks++;
            float t = Math.min(1.0f, (float) this.redirectionTicks / (float) this.maxRedirectionTicks);
            t = t * t * (3.0f - 2.0f * t); // Smoothstep interpolation

            // Interpolate between the initial velocity and the LATEST target velocity
            return this.startVelocity.scale(1.0 - t).add(this.targetVelocity.scale(t));
        }

        boolean isComplete() {
            return this.redirecting && this.redirectionTicks >= this.maxRedirectionTicks;
        }
    }

    public static void castAngleTest(Player player) {
        Level level = player.level();
        if (level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 rightVec = new Vec3(-lookVec.z, 0, lookVec.x).normalize();
        Vec3 upVec = lookVec.cross(rightVec).normalize();
        Vec3 spawnPos = eyePos.add(lookVec.scale(-2.0)); // spawn a bit behind player

        int baseDelay = 5; // ticks between arrows (0.25s)
        float velocity = 1.0F;
        float damage = 2.0F;

        // Loop through 0 -> 360 in 10° increments
        for (int i = 0; i < 36; i++) {
            double angle = Math.toRadians(i * 10.0);
            Vec3 dir = rightVec.scale(Math.cos(angle)).add(upVec.scale(Math.sin(angle))).normalize();

            SpectralArrow arrow = new SpectralArrow(EntityType.SPECTRAL_ARROW, serverLevel);
            arrow.setPos(spawnPos);
            arrow.setOwner(player);
            arrow.setBaseDamage(damage);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

            int range = i * 10; // you can replace this with your own formula
            String name = "Range: " + range;
            arrow.setCustomName(Component.literal(name));
            arrow.setCustomNameVisible(true);

            // Shoot with fixed direction + small speed
            arrow.shoot(dir.x, dir.y, dir.z, 0.6F, 0.0F);
            serverLevel.addFreshEntity(arrow);

        }
    }
}