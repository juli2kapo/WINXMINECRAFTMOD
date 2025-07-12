package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.TsunamiEntity;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class WaterPowers {

    public static void startDrowningTarget(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0) return;

        double range = 15.0 + (stage * 5.0);
        HitResult hitResult = getTarget(player, range);

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) hitResult).getEntity();
            if (target instanceof LivingEntity livingTarget) {
                int duration = 20 * 15; // 15 seconds
                int amplifier = stage - 1;
                livingTarget.addEffect(new MobEffectInstance(ModEffects.DROWNING_TARGET.get(), duration, amplifier, false, false));

                // Apply Slowness based on stage
                if (stage == 2) {
                    livingTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 0)); // Slowness I for 15 seconds
                } else if (stage >= 3) {
                    livingTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, 0)); // Slowness I for 15 seconds
                    livingTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2)); // Slowness III for 3 seconds
                }

                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, livingTarget.getX(), livingTarget.getY(), livingTarget.getZ(), 15, 0.5, 1.0, 0.5, 0.1);
                }
            }
        }
    }

    public static void summonTsunami(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0 || player.level().isClientSide()) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) player.level();

        // Crear y añadir la entidad Tsunami
        TsunamiEntity tsunami = new TsunamiEntity(ModEntities.TSUNAMI.get(), serverLevel, player, stage);
        serverLevel.addFreshEntity(tsunami);

        // Efectos de partículas iniciales para una aparición dramática
        double width = 6.0 + (stage * 2.0);
        double length = 12.0;
        Vec3 playerPos = player.position();
        Vec3 lookVec = player.getLookAngle();
        Vec3 direction = new Vec3(lookVec.x, 0, lookVec.z).normalize();
        Vec3 waveOrigin = playerPos.subtract(direction.scale(8));

        for (double l = 0; l < length; l += 0.8) {
            double particleHeight = (2.0 + stage) * Mth.sin((float) (l / length * Math.PI));
            if (particleHeight <= 0) continue;
            Vec3 particlePos = waveOrigin.add(direction.scale(l)).add(0, particleHeight, 0);
            serverLevel.sendParticles(ParticleTypes.SPLASH, particlePos.x, particlePos.y, particlePos.z, 10, width / 2, 0.5, width / 2, 0.2);
        }

        // Reproducir sonido
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, SoundSource.PLAYERS, 1.5F, 1.0F);
    }

    private static HitResult getTarget(Player player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D);

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eyePos, endPos, searchBox,
                (entity) -> !entity.isSpectator() && entity.isPickable() && entity instanceof LivingEntity, range * range);

        if (entityHit != null) {
            return entityHit;
        }

        return player.level().clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }
}