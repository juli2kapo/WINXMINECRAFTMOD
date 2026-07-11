package net.juli2kapo.minewinx.entity.plants;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Misil de choclo del Cob Cannon: trayectoria balística, explota al impactar
 * con daño en área grande. No rompe bloques y nunca daña a la dueña ni a las
 * plantas.
 */
public class CobProjectileEntity extends ThrowableProjectile {

    private static final float RADIUS = 4.0F;
    private static final float DAMAGE = 24.0F;

    @Nullable
    private UUID ownerPlayerUUID;

    public CobProjectileEntity(EntityType<? extends CobProjectileEntity> type, Level level) {
        super(type, level);
    }

    public void setOwnerPlayer(@Nullable UUID owner) {
        this.ownerPlayerUUID = owner;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.tickCount > 200) {
                this.discard();
                return;
            }
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.CLOUD,
                    this.getX(), this.getY(), this.getZ(), 1, 0.02, 0.02, 0.02, 0.01);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) this.level();

        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(RADIUS),
                e -> e.isAlive() && !(e instanceof PlantEntity)
                        && !(e instanceof Player p && p.getUUID().equals(ownerPlayerUUID)));
        for (LivingEntity victim : victims) {
            double dist = victim.distanceTo(this);
            if (dist <= RADIUS) {
                victim.hurt(serverLevel.damageSources().explosion(this, this),
                        DAMAGE * (1.0F - (float) (dist / (RADIUS + 1))));
            }
        }

        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                this.getX(), this.getY(), this.getZ(), 3, 0.4, 0.4, 0.4, 0.2);
        serverLevel.sendParticles(net.juli2kapo.minewinx.particles.ModParticles.POWIE_PARTICLE.get(),
                this.getX(), this.getY() + 0.5, this.getZ(), 10, 1.2, 1.0, 1.2, 0.05);
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                net.juli2kapo.minewinx.sound.ModSounds.EXPLOSION.get(), SoundSource.NEUTRAL, 1.5F, 1.0F);
        this.discard();
    }

    @Override
    protected float getGravity() {
        return 0.05F;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerPlayerUUID != null) tag.putUUID("OwnerPlayer", ownerPlayerUUID);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerPlayer")) this.ownerPlayerUUID = tag.getUUID("OwnerPlayer");
    }
}
