package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.TsunamiEntity;
import net.juli2kapo.minewinx.entity.WaterBlobProjectileEntity;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class WaterPowers {

    public static void startDrowningTarget(Player player) {
        Level world = player.level();
        if (!world.isClientSide) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.5F, 1.0F);

            WaterBlobProjectileEntity projectile = new WaterBlobProjectileEntity(ModEntities.WATER_BLOB_PROJECTILE.get(), player, world);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 2.0F, 0.5F);
            world.addFreshEntity(projectile);
        }
    }

    public static void summonTsunami(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0 || player.level().isClientSide()) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) player.level();

        TsunamiEntity tsunami = new TsunamiEntity(ModEntities.TSUNAMI.get(), serverLevel, player, stage);
        serverLevel.addFreshEntity(tsunami);

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

        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, SoundSource.PLAYERS, 1.5F, 1.0F);
    }
}