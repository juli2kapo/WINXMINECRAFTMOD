package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.PlayerIllusionEntity;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class DarkPowers {
    private static final float EXPLOSION_RADIUS = 3.0F;
    private static final float EXPLOSION_DAMAGE = 12.0F;

    // Etiqueta NBT para identificar el creador de la ilusión
    public static final String CREATOR_UUID_TAG = "IllusionCreatorUUID";

    /**
     * Intercambia la posición del jugador con la ilusión que está mirando
     * @return true si se realizó el intercambio, false si no se encontró ilusión
     */
    public static boolean swapWithIllusion(Player player) {
        if (player.level().isClientSide()) return false;
        ServerLevel serverLevel = (ServerLevel) player.level();

        // Obtener la dirección de la mirada del jugador
        Vec3 viewVector = player.getViewVector(1.0F);
        Vec3 eyePosition = player.getEyePosition();

        // Parámetros para la detección
        double maxDistance = 32.0;
        double angleThreshold = Math.toRadians(10.0); // 10 grados de tolerancia

        // Buscar todas las ilusiones creadas por este jugador
        List<Entity> nearbyIllusions = serverLevel.getEntities(player,
                player.getBoundingBox().inflate(maxDistance),
                entity -> isOwnedIllusion(entity, player.getUUID()));

        // Encontrar la ilusión que mejor se alinea con la mirada del jugador
        Entity targetIllusion = null;
        double bestMatch = Double.MAX_VALUE;

        for (Entity illusion : nearbyIllusions) {
            // Calcular vector desde el ojo del jugador hacia la ilusión
            Vec3 toIllusion = illusion.position().add(0, illusion.getEyeHeight() / 2, 0).subtract(eyePosition);
            double distance = toIllusion.length();

            if (distance <= maxDistance) {
                // Normalizar para comparar direcciones
                Vec3 normalized = toIllusion.normalize();

                // Calcular ángulo entre la dirección de vista y la dirección a la ilusión
                double dot = viewVector.dot(normalized);
                double angle = Math.acos(Math.min(Math.max(dot, -1.0), 1.0)); // Prevenir errores numéricos

                // Si está dentro del umbral y es mejor que coincidencias anteriores
                if (angle <= angleThreshold && angle < bestMatch) {
                    bestMatch = angle;
                    targetIllusion = illusion;
                }
            }
        }

        // Si encontramos una ilusión, intercambiar posiciones
        if (targetIllusion != null) {
            // Guardar posiciones originales
            Vec3 playerPos = player.position();
            float playerYRot = player.getYRot();
            float playerXRot = player.getXRot();

            Vec3 illusionPos = targetIllusion.position();
            float illusionYRot = targetIllusion.getYRot();
            float illusionXRot = targetIllusion.getXRot();

            // Sonidos
            serverLevel.playSound(null, playerPos.x, playerPos.y, playerPos.z,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);

            serverLevel.playSound(null, illusionPos.x, illusionPos.y, illusionPos.z,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);

            // Intercambiar posiciones
            player.teleportTo(illusionPos.x, illusionPos.y, illusionPos.z);
            player.setYRot(illusionYRot);
            player.setXRot(illusionXRot);
            player.setDeltaMovement(0, 0, 0); // Evita movimiento residual

            targetIllusion.teleportTo(playerPos.x, playerPos.y, playerPos.z);
            targetIllusion.setYRot(playerYRot);
            targetIllusion.setXRot(playerXRot);

            return true;
        }

        return false;
    }

    /**
     * Hace explotar todas las ilusiones creadas por un jugador específico
     */
    public static int detonateIllusions(Player player) {
        if (player.level().isClientSide()) return 0;
        int stage = PlayerDataProvider.getStage(player);
        ServerLevel serverLevel = (ServerLevel) player.level();
        int count = 0;

        // Daño base escalado por la etapa
        float explosionDamage = EXPLOSION_DAMAGE * Math.max(1, stage);

        List<Entity> nearbyEntities = serverLevel.getEntities(player,
                player.getBoundingBox().inflate(32.0D),
                entity -> isOwnedIllusion(entity, player.getUUID()));

        for (Entity entity : nearbyEntities) {
            Vec3 pos = entity.position();

            // Partículas de explosión mejoradas
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    pos.x, pos.y + 0.5, pos.z,
                    3, 0.3, 0.3, 0.3, 0.2);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.x, pos.y + 0.5, pos.z,
                    30, 0.4, 0.4, 0.4, 0.2);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                    pos.x, pos.y + 0.5, pos.z,
                    20, 0.2, 0.2, 0.2, 0.1);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    pos.x, pos.y + 0.5, pos.z,
                    20, 0.3, 0.3, 0.3, 0.1);

            serverLevel.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F, 1.0F);

            List<Entity> victims = serverLevel.getEntities(entity,
                    new AABB(pos.x - EXPLOSION_RADIUS, pos.y - EXPLOSION_RADIUS, pos.z - EXPLOSION_RADIUS,
                            pos.x + EXPLOSION_RADIUS, pos.y + EXPLOSION_RADIUS, pos.z + EXPLOSION_RADIUS),
                    e -> !(e instanceof PlayerIllusionEntity) && e != player);

            for (Entity victim : victims) {
                double distance = victim.distanceTo(entity);
                if (distance <= EXPLOSION_RADIUS) {
                    float damage = explosionDamage * (1.0F - (float)(distance / EXPLOSION_RADIUS));
                    victim.hurt(victim.damageSources().explosion(null, player), damage);
                }
            }

            entity.discard();
            count++;
        }

        return count;
    }
    /**
     * Verifica si una entidad es una ilusión creada por el jugador indicado
     */
    private static boolean isOwnedIllusion(Entity entity, UUID playerUUID) {
        if (entity instanceof PlayerIllusionEntity ||
                (entity instanceof Mob && entity.getTags().contains("Illusion"))) {

            CompoundTag persistentData = entity.getPersistentData();
            if (persistentData.hasUUID(CREATOR_UUID_TAG)) {
                return persistentData.getUUID(CREATOR_UUID_TAG).equals(playerUUID);
            }
        }
        return false;
    }

    /**
     * Registra al creador de una ilusión
     */
    public static void markIllusionCreator(Entity illusion, Player creator) {
        CompoundTag persistentData = illusion.getPersistentData();
        persistentData.putUUID(CREATOR_UUID_TAG, creator.getUUID());
        illusion.addTag("Illusion"); // Marcar como ilusión
    }



    /**
     * Ordena a las ilusiones atacar o moverse hacia el objetivo que el jugador está mirando
     * Si el jugador está agachado, controla las ilusiones de mobs
     * Si no está agachado, controla las ilusiones de jugadores
     *
     * @return true si al menos una ilusión recibió una orden
     */
    public static boolean commandIllusions(Player player) {
        if (player.level().isClientSide()) return false;
        ServerLevel serverLevel = (ServerLevel) player.level();
        boolean isShifting = player.isShiftKeyDown();
        boolean commandExecuted = false;

        // Raytrace para encontrar lo que el jugador está mirando
        double rayDistance = 50.0;
        Vec3 startPos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = startPos.add(lookVec.x * rayDistance, lookVec.y * rayDistance, lookVec.z * rayDistance);

        // Buscar las ilusiones del jugador (mobIllusions si está agachado, playerIllusions si no)
        List<Entity> illusions = serverLevel.getEntities(player,
                player.getBoundingBox().inflate(32.0D),
                entity -> {
                    if (!isOwnedIllusion(entity, player.getUUID())) return false;

                    // Filtrar por tipo según si está agachado o no
                    if (isShifting) {
                        return entity instanceof Mob && !(entity instanceof PlayerIllusionEntity);
                    } else {
                        return entity instanceof PlayerIllusionEntity;
                    }
                });

        if (illusions.isEmpty()) {
            return false;
        }

        // Intentar encontrar una entidad en la mira del jugador
        Entity targetEntity = null;
        double closestDistance = Double.MAX_VALUE;
        Vec3 targetPos = null;

        // Primero, buscar entidades como objetivo
        for (Entity entity : serverLevel.getEntities(player,
                new AABB(startPos.x - rayDistance, startPos.y - rayDistance, startPos.z - rayDistance,
                        startPos.x + rayDistance, startPos.y + rayDistance, startPos.z + rayDistance),
                e -> e != player && !isOwnedIllusion(e, player.getUUID()))) {

            AABB boundingBox = entity.getBoundingBox();
            java.util.Optional<Vec3> hitResult = boundingBox.clip(startPos, endPos);

            if (hitResult.isPresent()) {
                double distance = startPos.distanceToSqr(hitResult.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    targetEntity = entity;
                    targetPos = entity.position();
                }
            }
        }

        // Si no se encontró entidad, buscar bloque
        if (targetEntity == null) {
            HitResult blockHit =
                    serverLevel.clip(new ClipContext(
                            startPos, endPos,
                            ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE,
                            player));

            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) blockHit;
                targetPos = Vec3.atCenterOf(blockHitResult.getBlockPos());
            } else {
                // Si no hay bloque ni entidad, usar el punto final del rayo
                targetPos = endPos;
            }
        }

        if (targetPos == null) {
            return false;
        }

        // Efecto visual y sonoro para el jugador que da la orden
        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 2.0, player.getZ(),
                15, 0.2, 0.2, 0.2, 0.5);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.0F);

        // Enviar órdenes a las ilusiones
        for (Entity illusion : illusions) {
            if (illusion instanceof Mob mob) {
                if (targetEntity != null) {
                    // Si hay una entidad objetivo
                    boolean isHostile = isHostileMob(mob);

                    if (isHostile) {
                        // Mobs agresivos atacan
                        mob.setTarget(targetEntity instanceof LivingEntity ? (LivingEntity)targetEntity : null);
                        mob.setAggressive(true);
                    }
                    // Todos los mobs se mueven hacia el objetivo
                    mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
                } else {
                    // Solo moverse hacia la posición
                    mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
                }
                commandExecuted = true;

            } else if (illusion instanceof PlayerIllusionEntity playerIllusion) {
                if (targetEntity != null) {
                    // Hacer que se mueva hacia el objetivo
                    playerIllusion.attackTarget(targetEntity);
                } else {
                    // Solo moverse hacia la posición
                    playerIllusion.moveToLocation(targetPos);
                }
                commandExecuted = true;
            }
        }

        return commandExecuted;
    }

    /**
     * Determina si un mob es agresivo/hostil
     */
    private static boolean isHostileMob(Mob mob) {
        return mob instanceof Monster ||
                mob.getType().is(net.minecraft.tags.EntityTypeTags.RAIDERS) ||
                mob instanceof EnderDragon ||
                mob instanceof WitherBoss;
    }

}