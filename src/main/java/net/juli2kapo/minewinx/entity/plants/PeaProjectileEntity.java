package net.juli2kapo.minewinx.entity.plants;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Arveja de las plantas tiradoras. Variantes: normal, congelada (ralentiza) y
 * de fuego (más daño + prende fuego). Al pasar cerca de un Torchwood, las
 * variantes normal y congelada se convierten en fuego (regla PvZ clásica: el
 * fuego cancela el frío).
 */
public class PeaProjectileEntity extends ThrowableProjectile {

    public enum Variant { NORMAL, FROZEN, FIRE }

    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(PeaProjectileEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID ownerPlayerUUID;

    public PeaProjectileEntity(EntityType<? extends PeaProjectileEntity> type, Level level) {
        super(type, level);
    }

    public void setVariant(Variant variant) {
        this.entityData.set(DATA_VARIANT, variant.ordinal());
    }

    public Variant getVariant() {
        return Variant.values()[this.entityData.get(DATA_VARIANT)];
    }

    public void setOwnerPlayer(@Nullable UUID owner) {
        this.ownerPlayerUUID = owner;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_VARIANT, Variant.NORMAL.ordinal());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) this.level();

        if (this.tickCount > 60) {
            this.discard();
            return;
        }

        // Upgrade al pasar cerca de un Torchwood (una sola vez, a FIRE)
        if (getVariant() != Variant.FIRE) {
            List<PlantEntity> torchwoods = serverLevel.getEntitiesOfClass(PlantEntity.class,
                    this.getBoundingBox().inflate(1.2),
                    p -> p.getPlantType() == PlantType.TORCHWOOD);
            if (!torchwoods.isEmpty()) {
                setVariant(Variant.FIRE);
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        this.getX(), this.getY(), this.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
                serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.juli2kapo.minewinx.sound.ModSounds.FIREPEA.get(),
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.0F);
            }
        }

        if (getVariant() == Variant.FIRE && this.tickCount % 2 == 0) {
            serverLevel.sendParticles(ParticleTypes.SMALL_FLAME, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide()) return;
        if (!(result.getEntity() instanceof LivingEntity victim)) return;
        // No dañar a la dueña, su equipo ni sus plantas/ilusiones
        if (net.juli2kapo.minewinx.util.Targeting.isAlly(victim, ownerPlayerUUID)) return;

        Variant variant = getVariant();
        float damage = variant == Variant.FIRE ? 7.0F : 4.0F;
        victim.hurt(this.damageSources().thrown(this, null), damage);
        switch (variant) {
            case FROZEN -> {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.juli2kapo.minewinx.sound.ModSounds.FROZEN.get(),
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.0F);
            }
            case FIRE -> victim.setSecondsOnFire(3);
            default -> {}
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide()) {
            ((ServerLevel) this.level()).sendParticles(
                    getVariant() == Variant.FROZEN ? ParticleTypes.SNOWFLAKE : ParticleTypes.CRIT,
                    this.getX(), this.getY(), this.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.juli2kapo.minewinx.sound.ModSounds.PEA_HIT.get(),
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.0F);
            this.discard();
        }
    }

    @Override
    protected float getGravity() {
        return 0.01F; // casi recta, estilo PvZ
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", this.entityData.get(DATA_VARIANT));
        if (ownerPlayerUUID != null) tag.putUUID("OwnerPlayer", ownerPlayerUUID);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_VARIANT, tag.getInt("Variant"));
        if (tag.hasUUID("OwnerPlayer")) this.ownerPlayerUUID = tag.getUUID("OwnerPlayer");
    }
}
