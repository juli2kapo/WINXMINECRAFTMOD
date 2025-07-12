package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.particles.ModParticles;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class FirePowers {

    public static void activateFireBarrier(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0) return;

        player.addEffect(new MobEffectInstance(ModEffects.FIRE_BARRIER.get(), 15 * 20, 0, false, false, true));
    }

    public static void executeFireBarrierTick(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0) return;

        ServerLevel level = (ServerLevel) player.level();
        double burnRadius = (stage == 3) ? 5.0 : 4.0;
        double pushRadius = (stage == 3) ? 4.5 : 3.5;
        AABB area = player.getBoundingBox().inflate(burnRadius);

        List<LivingEntity> entitiesToAffect = level.getEntitiesOfClass(LivingEntity.class, area, entity ->
                entity != player && player.distanceToSqr(entity) <= burnRadius * burnRadius);

        for (LivingEntity entity : entitiesToAffect) {
            if (entity.tickCount % 20 == 0) {
                entity.hurt(level.damageSources().magic(), 2.0F);
                entity.setSecondsOnFire(2);
            }
            if (stage >= 2 && player.distanceToSqr(entity) <= pushRadius * pushRadius) {
                Vec3 knockbackDirection = entity.position().subtract(player.position()).normalize();
                entity.setDeltaMovement(entity.getDeltaMovement().add(knockbackDirection.scale(0.5)));
            }
        }

        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, area, projectile ->
                !player.is(projectile.getOwner()) && player.distanceToSqr(projectile) <= burnRadius * burnRadius);

        for (Projectile projectile : projectiles) {
            if (stage == 3) {
                Entity owner = projectile.getOwner();
                Vec3 explosionPos = projectile.position();

                if (owner != null) {
                    Vec3 directionToOwner = owner.position().subtract(projectile.position()).normalize();
                    explosionPos = projectile.position().add(directionToOwner.scale(1.0));
                }
                level.explode(player, explosionPos.x(), explosionPos.y(), explosionPos.z(), 4.0F, Level.ExplosionInteraction.NONE);
            } else {
                level.sendParticles(ParticleTypes.LAVA, projectile.getX(), projectile.getY(), projectile.getZ(), 20, 0.3, 0.3, 0.3, 0);
                level.sendParticles(ParticleTypes.FLAME, projectile.getX(), projectile.getY(), projectile.getZ(), 15, 0.4, 0.4, 0.4, 0.05);
                level.playSound(null, projectile.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            projectile.discard();
        }

        if (player.tickCount % 2 == 0) {
            for (int i = 0; i < 360; i += 10) {
                for (int j = 0; j < 180; j += 10) {
                    double equatorFactor = 1.0 - Math.abs(j - 90.0) / 90.0;
                    if (level.random.nextFloat() > (0.7 - equatorFactor * 0.5)) {
                        double angleI = Math.toRadians(i);
                        double angleJ = Math.toRadians(j);

                        double pX = player.getX() + burnRadius * Math.sin(angleJ) * Math.cos(angleI);
                        double pY = player.getY() + burnRadius * Math.cos(angleJ);
                        double pZ = player.getZ() + burnRadius * Math.sin(angleJ) * Math.sin(angleI);

                        level.sendParticles(ModParticles.FIRE_PARTICLE.get(), pX, pY, pZ, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    public static void fireLaser(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0) return;

        if (stage == 1) {
            performRaycast(player, 25.0, stage);
        } else {
            int duration = (stage == 2) ? 60 : 100; // 3 seg para etapa 2, 5 para etapa 3
            player.addEffect(new MobEffectInstance(ModEffects.FIRE_LASER.get(), duration, 0, false, false, true));
        }
    }

    public static void performRaycast(Player player, double range, int stage) {
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);

        // Calcular el punto de origen del láser desde las manos
        Vec3 rightVec = lookVec.yRot(90); // Vector hacia la derecha del jugador
        Vec3 handOffset = rightVec.scale(0.25).add(lookVec.scale(0.5));
        Vec3 startPos = eyePos.subtract(0, 0.4, 0).add(handOffset);

        // El punto final del raycast sigue basándose en la dirección de la mirada
        Vec3 endPos = eyePos.add(lookVec.scale(range));

        // Ajustar la dirección del raycast para que vaya desde el nuevo punto de inicio al punto de mira
        Vec3 raycastDirection = endPos.subtract(startPos).normalize();
        Vec3 raycastEndPos = startPos.add(raycastDirection.scale(range));

        BlockHitResult blockHit = level.clip(new ClipContext(startPos, raycastEndPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        double hitRange = blockHit.getType() == HitResult.Type.MISS ? range : startPos.distanceTo(blockHit.getLocation());
        AABB searchBox = player.getBoundingBox().expandTowards(raycastDirection.scale(hitRange)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, startPos, raycastEndPos, searchBox, (entity) -> !entity.isSpectator() && entity.isPickable(), hitRange * hitRange);

        HitResult finalHit = (entityHit != null && startPos.distanceToSqr(entityHit.getLocation()) < startPos.distanceToSqr(blockHit.getLocation())) ? entityHit : blockHit;

        Vec3 hitLocation = finalHit.getLocation();
        drawLaserParticles(player, startPos, hitLocation);

        if (finalHit.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) finalHit).getEntity();
            if (target instanceof LivingEntity livingTarget) {
                livingTarget.hurt(player.damageSources().indirectMagic(player, player), 4.0F);
                livingTarget.setSecondsOnFire(5);
                if (stage == 3) {
                    Vec3 currentMotion = livingTarget.getDeltaMovement();
                    livingTarget.setDeltaMovement(currentMotion.x, 0.4, currentMotion.z);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.EXPLOSION, hitLocation.x, hitLocation.y, hitLocation.z, 1, 0, 0, 0, 0);
                        serverLevel.sendParticles(ParticleTypes.LAVA, hitLocation.x, hitLocation.y, hitLocation.z, 10, 0.2, 0.2, 0.2, 0);
                    }
                }
            }
        } else if (finalHit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockResult = (BlockHitResult) finalHit;
            BlockPos hitPos = blockResult.getBlockPos();
            BlockState hitState = level.getBlockState(hitPos);

            if (stage == 3) {
                boolean isFlammable = hitState.getBlock().isFlammable(hitState, level, hitPos, blockResult.getDirection());
                boolean isDirt = hitState.is(Blocks.DIRT);
                if ((isFlammable || isDirt) && level.random.nextFloat() < 0.25F) {
                    level.destroyBlock(hitPos, false, player);
                    return;
                }
            }

            if (blockResult.getDirection() == Direction.UP) {
                BlockPos firePos = hitPos.above();
                if (BaseFireBlock.canBePlacedAt(level, firePos, blockResult.getDirection())) {
                    level.setBlockAndUpdate(firePos, BaseFireBlock.getState(level, firePos));
                }
            }
        }
    }

    private static void drawLaserParticles(Player player, Vec3 startPoint, Vec3 endPoint) {
        if (!(player.level() instanceof ServerLevel level)) return;

        Vec3 direction = endPoint.subtract(startPoint).normalize();
        double distance = startPoint.distanceTo(endPoint);

        if (distance < 0.1) return; // Evitar dibujar si el punto final está muy cerca del nuevo punto de inicio

        for (double d = 0; d < distance; d += 0.25) {
            Vec3 particlePos = startPoint.add(direction.scale(d));
            level.sendParticles(DustParticleOptions.REDSTONE, particlePos.x, particlePos.y, particlePos.z, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Aplica efectos pasivos de resistencia al fuego basados en la etapa del jugador.
     * Debe ser llamado cada tick para jugadores con el elemento fuego.
     * @param player El jugador a afectar.
     */
    public static void applyPassiveFireResistance(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0) return;

        // Etapa 1+: Inmunidad a ser incendiado
        if (player.isOnFire()) {
            player.clearFire();
        }

        // Etapa 3: Inmunidad completa al fuego y la lava
        if (stage == 3) {
            // Aplicar el efecto de Resistencia al Fuego de Minecraft para inmunidad a la lava
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 5, 0, true, false, false));
        }
    }
}