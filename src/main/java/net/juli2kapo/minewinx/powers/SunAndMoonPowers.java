package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.LightRayEntity;
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
import net.minecraft.world.entity.projectile.ThrowableProjectile;
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
            damage = 4.0F + (stage * 4.0F); // 8 / 12 / 16 (antes 15/20/25: rompía el balance con CD de 3s)
            particleEffect = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.2f), 1.5f);
        } else {
            damage = 4.0F + (stage * 2.0F);
            particleEffect = new DustParticleOptions(new Vector3f(0.8f, 0.8f, 1.0f), 1.5f);
        }

        // --- 2. Puntería asistida: mirar "más o menos" a un mob alcanza ---
        Vec3 targetPos = findAimPoint(player, maxRange);

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
                raysPerSecond = 6;
                break;
            case 2:
                durationSeconds = 5;
                raysPerSecond = 12;
                break;
            default: // Stage 3 and above
                durationSeconds = 10;
                raysPerSecond = 24;
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

        pendingArrows.removeIf(pending -> {
            pending.updateTick();

            Player caster = serverLevel.getPlayerByUUID(pending.casterUUID);
            Entity arrowEntity = serverLevel.getEntity(pending.arrowUUID);

            if (caster == null || !(arrowEntity instanceof ThrowableProjectile arrow) || !arrow.isAlive()) {
                return true; // Remove if caster is gone, arrow is dead, or arrow is not a SpectralArrow
            }

            if (pending.isWaitingToStart()) {
                return false; // Still waiting for initial delay, keep it
            }

            // --- CORE LOGIC FIX ---
            // The arrow is now ready to be redirected.
            // 1. Calculate the player's current target
            // Punto focal con puntería asistida: los rayos convergen sobre el
            // enemigo apuntado (a CUALQUIER distancia), no a 8 bloques fijos
            Vec3 focalPoint = findAimPoint(caster, 32.0);

            Vec3 targetDirection = focalPoint.subtract(arrow.position()).normalize();

            // 2. If this is the first redirection tick, set the initial state.
            if (!pending.isRedirecting()) {
                pending.startRedirecting(arrow.getDeltaMovement());
            }

            // 3. Update the arrow's target direction EVERY tick.
            pending.updateTargetDirection(targetDirection);

            // 4. Get the new interpolated velocity and apply it.
            Vec3 newVelocity = pending.updateVelocity();
            arrow.setDeltaMovement(newVelocity);
            arrow.hasImpulse = true; // Force client sync

            return pending.isComplete(); // Remove when redirection is finished.
        });
    }

    /**
     * Spawns a single projectile ray for the Light Barrage ability.
     * The ray spawns behind the player with an initial outward trajectory,
     * then is later redirected to the player's target by the onServerTick method.
     */
    /**
     * Puntería asistida compartida por los poderes de luz:
     * 1) el enemigo válido mejor alineado con la mirada (cono de ~12°),
     * 2) si no hay, el bloque apuntado,
     * 3) si no, el punto a maxRange.
     */
    public static Vec3 findAimPoint(Player player, double maxRange) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);

        net.minecraft.world.entity.LivingEntity best = null;
        double bestAngle = Math.toRadians(12.0);
        for (net.minecraft.world.entity.LivingEntity candidate :
                player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(maxRange),
                        c -> net.juli2kapo.minewinx.util.Targeting.isValidTarget(c, player.getUUID()))) {
            Vec3 to = candidate.position().add(0, candidate.getBbHeight() * 0.5, 0).subtract(eyePos);
            if (to.length() > maxRange) continue;
            double dot = Math.max(-1.0, Math.min(1.0, look.dot(to.normalize())));
            double angle = Math.acos(dot);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = candidate;
            }
        }
        if (best != null) {
            return best.position().add(0, best.getBbHeight() * 0.5, 0);
        }

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                eyePos, eyePos.add(look.scale(maxRange)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            return blockHit.getLocation();
        }
        return eyePos.add(look.scale(maxRange));
    }

    private static void spawnSingleRay(Player player, CastingState state) {
        ServerLevel serverLevel = (ServerLevel) player.level();

        // 1. Define the spawn area behind the player. (This part is perfect)
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 spawnCenter = eyePos.add(lookVec.scale(-state.distanceBehind));
        Vec3 rightVec = new Vec3(-lookVec.z, 0, lookVec.x).normalize(); // Vector to player's right

        double totalWidth = 3.0;
        double randomHOffset = (random.nextDouble() - 0.5) * totalWidth;
        double randomVOffset = (random.nextDouble() - 0.5);
//        Vec3 spawnPos = spawnCenter.add(rightVec.scale(randomHOffset)).add(0, randomVOffset, 0);

        double verticalShift = -1;
        Vec3 spawnPos = spawnCenter
                .add(rightVec.scale(randomHOffset))
                .add(0, randomVOffset + verticalShift, 0);

        // 2. Create and configure the spectral arrow. (This is also fine)
//        SpectralArrow arrow = new SpectralArrow(EntityType.SPECTRAL_ARROW, serverLevel);
//        arrow.setPos(spawnPos);
//        arrow.setOwner(player);
//        arrow.setBaseDamage(state.damagePerRay);
//        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

        LightRayEntity lightRay = new LightRayEntity(serverLevel, player);
        lightRay.setPos(spawnPos);
        lightRay.setDamage(state.damagePerRay);




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
//        arrow.shoot(initialDirection.x, initialDirection.y, initialDirection.z, 0.4F, 0.0F);
//        serverLevel.addFreshEntity(arrow);
        lightRay.shoot(initialDirection.x, initialDirection.y, initialDirection.z, 0.4F, 0.0F);
        serverLevel.addFreshEntity(lightRay);


        // 4. Schedule the arrow to be redirected. (This part is perfect)
        int delayTicks = 1 + random.nextInt(2); // 0.05 to 0.1 seconds delay.
        pendingArrows.add(new PendingArrow(lightRay, player, delayTicks, state.velocity));
    }

    // --- Helper Class to store the state of an ongoing cast ---
    private static class CastingState {
        final UUID casterUUID;
        final int stage;
        final float damagePerRay;

        // Ability constants
        final float velocity = 2.5F;
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

        PendingArrow(ThrowableProjectile arrow, Player caster, int delayTicks, float finalVelocity) {
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

//        void startRedirecting(Vec3 currentVelocity, Vec3 targetDirection) {
//            this.redirecting = true;
//            this.startVelocity = currentVelocity;
//            this.targetVelocity = targetDirection.scale(this.finalVelocity);
//            this.redirectionTicks = 0;
//        }

        void startRedirecting(Vec3 currentVelocity) {
            this.redirecting = true;
            this.startVelocity = currentVelocity;
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

    /**
     * Slot 3: Destello Solar. Nova instantánea de luz alrededor de Stella:
     * ciega y daña a los enemigos en el radio. De día además los incendia (la
     * furia del sol); de noche los marca con brillo (la luna los revela) y los
     * ralentiza. Radio y potencia escalan con stage.
     */
    public static void castSolarFlare(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;

        double radius = 8.0 + 2.0 * stage; // 10 / 12 / 14
        boolean day = level.isDay();
        float damage = day ? (4.0F + 2.0F * stage) : (2.0F + stage);

        java.util.List<net.minecraft.world.entity.LivingEntity> victims =
                serverLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(radius),
                        e -> net.juli2kapo.minewinx.util.Targeting.isValidTarget(e, player.getUUID()));
        for (net.minecraft.world.entity.LivingEntity victim : victims) {
            victim.hurt(serverLevel.damageSources().indirectMagic(player, player), damage);
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.BLINDNESS, 100 + 40 * stage, 0));
            if (day) {
                victim.setSecondsOnFire(3 + stage);
            } else {
                victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING, 200, 0));
                victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            }
        }

        // Fogonazo: anillo de destellos + estallido de luz real
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.2, 0.2, 0.2, 0);
        for (int i = 0; i < 24; i++) {
            double angle = i * (Math.PI * 2.0 / 24.0);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX() + Math.cos(angle) * radius * 0.5,
                    player.getY() + 1.0,
                    player.getZ() + Math.sin(angle) * radius * 0.5,
                    2, 0.2, 0.4, 0.2, 0.06);
        }
        net.juli2kapo.minewinx.util.TransientLights.place(serverLevel, player.blockPosition().above(), 15, 20);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                net.minecraft.sounds.SoundSource.PLAYERS, 2.0F, 1.6F);
    }
}