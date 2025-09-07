package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.SpeakerEntity;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MusicPowers {

    /**
     * Summons speakers at the location where the player is looking using ray tracing.
     * The speakers damage nearby entities and can be destroyed.
     */
    public static void summonSpeakers(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        // Calculate range based on stage
        double maxRange = 15.0 + (stage * 10.0); // Stage 1: 25, Stage 2: 35, Stage 3: 45

        // Perform raycast to find target location
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(maxRange));

        BlockHitResult blockHit = level.clip(new ClipContext(
            eyePos, endPos, 
            ClipContext.Block.OUTLINE, 
            ClipContext.Fluid.NONE, 
            player
        ));

        Vec3 targetPos = blockHit.getType() == HitResult.Type.MISS ? endPos : blockHit.getLocation();

        // Create and spawn speaker entity
        try {
            SpeakerEntity speaker = new SpeakerEntity(ModEntities.SPEAKER.get(), level);
            speaker.setPos(targetPos.x, targetPos.y, targetPos.z);
            speaker.setOwner(player);
            level.addFreshEntity(speaker);

            // Play summoning sound
            ServerLevel serverLevel = (ServerLevel) level;
            serverLevel.playSound(null, targetPos.x, targetPos.y, targetPos.z, 
                SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.8F);

            // Spawn summoning particles
            serverLevel.sendParticles(ParticleTypes.NOTE, 
                targetPos.x, targetPos.y + 1.0, targetPos.z, 
                20, 0.5, 0.5, 0.5, 0.1);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a cone-shaped vocal blast that damages entities in front of the player.
     * Range, angle, and damage scale with stage.
     */
    public static void vocalBlast(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) level;

        // Calculate cone properties based on stage
        double range = 8.0 + (stage * 4.0); // Stage 1: 12, Stage 2: 16, Stage 3: 20
        double coneAngle = Math.toRadians(30.0 + (stage * 15.0)); // Stage 1: 45°, Stage 2: 60°, Stage 3: 75°
        float damage = 3.0F + (stage * 2.0F); // Stage 1: 5.0, Stage 2: 7.0, Stage 3: 9.0

        Vec3 playerPos = player.getEyePosition();
        Vec3 lookDirection = player.getViewVector(1.0F);

        // Draw cone particles
        drawConeParticles(serverLevel, playerPos, lookDirection, range, coneAngle);

        // Find entities in cone area
        AABB searchArea = player.getBoundingBox().inflate(range);
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, searchArea);

        for (LivingEntity entity : nearbyEntities) {
            if (entity == player) continue;

            Vec3 entityPos = entity.getEyePosition();
            Vec3 toEntity = entityPos.subtract(playerPos).normalize();
            
            // Check if entity is within cone angle
            double dotProduct = lookDirection.dot(toEntity);
            double angleToEntity = Math.acos(Mth.clamp(dotProduct, -1.0, 1.0));
            
            if (angleToEntity <= coneAngle) {
                // Check distance
                double distanceToEntity = playerPos.distanceTo(entityPos);
                if (distanceToEntity <= range) {
                    // Calculate damage falloff based on distance and angle
                    double distanceFactor = 1.0 - (distanceToEntity / range);
                    double angleFactor = 1.0 - (angleToEntity / coneAngle);
                    float finalDamage = damage * (float) (distanceFactor * angleFactor);

                    // Apply damage and knockback
                    entity.hurt(level.damageSources().indirectMagic(player, player), finalDamage);
                    
                    // Apply knockback
                    Vec3 knockback = toEntity.scale(0.5 + (stage * 0.2));
                    entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));

                    // Apply confusion effect at higher stages
                    if (stage >= 2) {
                        int confusionDuration = 60 + (stage * 40); // 3-7 seconds
                        entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, confusionDuration, 0));
                    }

                    // Spawn impact particles
                    serverLevel.sendParticles(ParticleTypes.NOTE,
                        entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                        10, 0.3, 0.3, 0.3, 0.1);
                }
            }
        }

        // Play vocal blast sound
        serverLevel.playSound(null, playerPos.x, playerPos.y, playerPos.z,
            SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5F, 1.2F);
    }

    /**
     * Draws cone-shaped particles to visualize the vocal blast.
     */
    private static void drawConeParticles(ServerLevel level, Vec3 origin, Vec3 direction, double range, double coneAngle) {
        // Calculate perpendicular vectors for cone generation
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = direction.cross(up).normalize();
        Vec3 actualUp = right.cross(direction).normalize();

        // Generate cone particles
        for (double distance = 0.5; distance <= range; distance += 0.5) {
            double currentRadius = distance * Math.tan(coneAngle);
            int particlesAtDistance = (int) (currentRadius * 8); // More particles for larger radius
            
            for (int i = 0; i < particlesAtDistance; i++) {
                double angle = (2 * Math.PI * i) / particlesAtDistance;
                double x = Math.cos(angle) * currentRadius;
                double y = Math.sin(angle) * currentRadius;
                
                Vec3 offset = right.scale(x).add(actualUp.scale(y));
                Vec3 particlePos = origin.add(direction.scale(distance)).add(offset);
                
                // Use sound wave particles (note particles with purple color)
                level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.6f, 0.2f, 0.8f), 1.0f),
                    particlePos.x, particlePos.y, particlePos.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Creates a confusion song effect that affects entities in a circle around the player.
     * Radius and intensity scale with stage.
     */
    public static void confusionSong(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        ServerLevel serverLevel = (ServerLevel) level;

        // Calculate effect properties based on stage
        double radius = 8.0 + (stage * 6.0); // Stage 1: 14, Stage 2: 20, Stage 3: 26
        int duration = 100 + (stage * 60); // Stage 1: 8s, Stage 2: 11s, Stage 3: 14s
        int amplifier = stage - 1; // Stage 1: 0, Stage 2: 1, Stage 3: 2

        Vec3 playerPos = player.position();

        // Create particle ring to show effect area
        drawSongParticles(serverLevel, playerPos, radius);

        // Find and affect entities in radius
        AABB effectArea = player.getBoundingBox().inflate(radius);
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, effectArea);

        int affectedCount = 0;
        for (LivingEntity entity : nearbyEntities) {
            if (entity == player) continue;

            double distanceToEntity = playerPos.distanceTo(entity.position());
            if (distanceToEntity <= radius) {
                // Apply confusion effect
                entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, amplifier));
                
                // Apply additional effects based on stage
                if (stage >= 2) {
                    // Stage 2+: Add slowness
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 0));
                }
                
                if (stage >= 3) {
                    // Stage 3: Add weakness and occasional levitation
                    entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 0));
                    
                    if (level.random.nextFloat() < 0.3F) {
                        entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0));
                    }
                }

                // Spawn confusion particles around affected entity
                serverLevel.sendParticles(ParticleTypes.NOTE,
                    entity.getX(), entity.getY() + entity.getBbHeight() + 0.5, entity.getZ(),
                    5, 0.5, 0.2, 0.5, 0.05);

                affectedCount++;
            }
        }

        // Play different sounds based on how many entities were affected
        if (affectedCount > 0) {
            serverLevel.playSound(null, playerPos.x, playerPos.y, playerPos.z,
                SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 2.0F, 0.7F);
            
            // Play additional harmony notes for higher stages
            if (stage >= 2) {
                serverLevel.playSound(null, playerPos.x, playerPos.y, playerPos.z,
                    SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.5F, 0.9F);
            }
            if (stage >= 3) {
                serverLevel.playSound(null, playerPos.x, playerPos.y, playerPos.z,
                    SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.PLAYERS, 1.8F, 1.1F);
            }
        }
    }

    /**
     * Draws circular particle effects to visualize the confusion song area.
     */
    private static void drawSongParticles(ServerLevel level, Vec3 center, double radius) {
        // Draw multiple concentric circles with music notes
        for (double currentRadius = 2.0; currentRadius <= radius; currentRadius += 2.0) {
            int particleCount = (int) (currentRadius * 4);
            
            for (int i = 0; i < particleCount; i++) {
                double angle = (2 * Math.PI * i) / particleCount;
                double x = center.x + Math.cos(angle) * currentRadius;
                double z = center.z + Math.sin(angle) * currentRadius;
                double y = center.y + 0.5 + Math.sin(angle * 3) * 0.3; // Wavy height pattern
                
                level.sendParticles(ParticleTypes.NOTE, x, y, z, 1, 0, 0, 0, 0);
            }
        }

        // Add some floating musical notes in the center
        for (int i = 0; i < 10; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 4.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * 4.0;
            double offsetY = level.random.nextDouble() * 2.0;
            
            level.sendParticles(ParticleTypes.NOTE,
                center.x + offsetX, center.y + offsetY + 1.0, center.z + offsetZ,
                1, 0, 0.1, 0, 0.02);
        }
    }
}