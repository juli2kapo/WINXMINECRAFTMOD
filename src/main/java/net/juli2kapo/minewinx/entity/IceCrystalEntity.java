package net.juli2kapo.minewinx.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Prisión de hielo: encierra a la víctima dentro de un racimo de cristales
 * (solo visual, sin bloques en el mundo). Mientras dura, la víctima queda
 * clavada en el lugar, congelándose (overlay de powder snow) y recibiendo un
 * poco de daño de hielo. Termina al expirar o si la víctima muere.
 */
public class IceCrystalEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(IceCrystalEntity.class, EntityDataSerializers.FLOAT);

    @Nullable
    private UUID victimUUID;
    private int durationTicks = 8 * 20;

    public IceCrystalEntity(EntityType<? extends IceCrystalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void init(LivingEntity victim, int durationTicks) {
        this.victimUUID = victim.getUUID();
        this.durationTicks = durationTicks;
        this.setPos(victim.getX(), victim.getY(), victim.getZ());
        // El modelo mide 1.5 bloques: escalar para envolver a la víctima con margen
        this.setVisualScale(Math.max(1.0F, (victim.getBbHeight() + 0.6F) / 1.5F));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SCALE, 1.5F);
    }

    public float getVisualScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public void setVisualScale(float scale) {
        this.entityData.set(DATA_SCALE, scale);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) this.level();

        if (this.tickCount == 1) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.5F, 0.8F);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        LivingEntity victim = getVictim(serverLevel);
        if (victim == null || !victim.isAlive() || this.tickCount >= durationTicks) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.5F, 1.0F);
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.6, 0.9, 0.6, 0.05);
            this.discard();
            return;
        }

        // Clavar a la víctima en el centro del cristal (funciona también con jugadores)
        victim.teleportTo(this.getX(), this.getY(), this.getZ());
        victim.setDeltaMovement(0, Math.min(0, victim.getDeltaMovement().y), 0);
        victim.hasImpulse = true;
        victim.setTicksFrozen(Math.min(victim.getTicksFrozen() + 3, 300));
        if (this.tickCount % 40 == 0) {
            victim.hurt(serverLevel.damageSources().freeze(), 1.0F);
        }

        if (this.tickCount % 10 == 0) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    this.getX(), this.getY() + 1.0, this.getZ(), 4, 0.5, 0.8, 0.5, 0.01);
        }
    }

    @Nullable
    private LivingEntity getVictim(ServerLevel serverLevel) {
        if (victimUUID == null) return null;
        Entity entity = serverLevel.getEntity(victimUUID);
        return entity instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Victim")) {
            this.victimUUID = tag.getUUID("Victim");
        }
        this.durationTicks = tag.getInt("Duration");
        this.tickCount = tag.getInt("TickCount");
        if (tag.contains("Scale")) {
            this.setVisualScale(tag.getFloat("Scale"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.victimUUID != null) {
            tag.putUUID("Victim", this.victimUUID);
        }
        tag.putInt("Duration", this.durationTicks);
        tag.putInt("TickCount", this.tickCount);
        tag.putFloat("Scale", this.getVisualScale());
    }
}
