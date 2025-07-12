package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class WaterPowers {

    public static void startDrowningTarget(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0) return;

        double range = 15.0 + (stage * 5.0);
        HitResult hitResult = getTarget(player, range);

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) hitResult).getEntity();
            if (target instanceof LivingEntity livingTarget) {
                int duration = 20 * (5 + stage); // Duración aumenta con la etapa
                int amplifier = stage - 1; // Amplificador aumenta con la etapa
                livingTarget.addEffect(new MobEffectInstance(ModEffects.DROWNING_TARGET.get(), duration, amplifier, false, true));

                if (player.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, livingTarget.getX(), livingTarget.getY(), livingTarget.getZ(), 15, 0.5, 1.0, 0.5, 0.1);
                }
            }
        }
    }

    private static HitResult getTarget(Player player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D);

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eyePos, endPos, searchBox, (entity) -> !entity.isSpectator() && entity.isPickable() && entity instanceof LivingEntity, range * range);

        if (entityHit != null) {
            return entityHit;
        }

        return player.level().clip(new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }
}