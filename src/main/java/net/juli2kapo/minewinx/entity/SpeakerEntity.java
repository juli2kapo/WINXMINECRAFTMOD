package net.juli2kapo.minewinx.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SpeakerEntity extends Entity {
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(SpeakerEntity.class, EntityDataSerializers.INT);
    private static final int MAX_LIFETIME = 600; // 30 segundos (20 ticks por segundo)
    private static final double DAMAGE_RADIUS = 5.0;
    private static final float DAMAGE_AMOUNT = 4.0F;
    private static final int DAMAGE_INTERVAL = 40; // 2 segundos

    private Player owner;
    private int damageTimer = 0;

    public SpeakerEntity(EntityType<? extends SpeakerEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true; // No cae al suelo
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LIFETIME, 0);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            // Incrementar tiempo de vida
            int currentLifetime = this.entityData.get(LIFETIME);
            currentLifetime++;
            this.entityData.set(LIFETIME, currentLifetime);

            // Eliminar después de tiempo máximo
            if (currentLifetime >= MAX_LIFETIME) {
                this.destroySpeaker();
                return;
            }

            // Sistema de daño por intervalos
            damageTimer++;
            if (damageTimer >= DAMAGE_INTERVAL) {
                this.dealDamageToNearbyEntities();
                damageTimer = 0;
            }

            // Efectos de partículas y sonido cada cierto tiempo
            if (currentLifetime % 20 == 0) { // Cada segundo
                this.playMusicEffects();
            }
        }
    }

    private void dealDamageToNearbyEntities() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 speakerPos = this.position();
        AABB damageArea = this.getBoundingBox().inflate(DAMAGE_RADIUS);
        List<LivingEntity> nearbyEntities = this.level().getEntitiesOfClass(LivingEntity.class, damageArea);

        for (LivingEntity entity : nearbyEntities) {
            if (entity == this.owner) continue; // No dañar al propietario

            double distanceToSpeaker = speakerPos.distanceTo(entity.position());
            if (distanceToSpeaker <= DAMAGE_RADIUS) {
                // Aplicar daño con reducción por distancia
                double distanceFactor = 1.0 - (distanceToSpeaker / DAMAGE_RADIUS);
                float finalDamage = DAMAGE_AMOUNT * (float) distanceFactor;

                DamageSource damageSource = this.level().damageSources().indirectMagic(this, this.owner);
                entity.hurt(damageSource, finalDamage);

                // Efectos de partículas en la entidad dañada
                serverLevel.sendParticles(ParticleTypes.NOTE,
                        entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                        5, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    private void playMusicEffects() {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        Vec3 pos = this.position();

        // Reproducir sonido de altavoz
        serverLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8F, 0.9F);

        // Efectos de partículas musicales
        serverLevel.sendParticles(ParticleTypes.NOTE,
                pos.x, pos.y + 1.0, pos.z,
                3, 0.5, 0.5, 0.5, 0.05);
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    public Player getOwner() {
        return this.owner;
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (!this.level().isClientSide()) {
            this.destroySpeaker();
        }
        return true;
    }

    private void destroySpeaker() {
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = this.position();

            // Efectos de destrucción
            serverLevel.playSound(null, pos.x, pos.y, pos.z,
                    SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);

            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    pos.x, pos.y + 0.5, pos.z,
                    10, 0.3, 0.3, 0.3, 0.1);
        }

        this.discard();
    }

    @Override
    public boolean isPickable() {
        return true; // Permite que sea seleccionable para ataques
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Lifetime", this.entityData.get(LIFETIME));
        tag.putInt("DamageTimer", this.damageTimer);

        if (this.owner != null) {
            tag.putUUID("Owner", this.owner.getUUID());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(LIFETIME, tag.getInt("Lifetime"));
        this.damageTimer = tag.getInt("DamageTimer");

        if (tag.hasUUID("Owner")) {
            // El owner se restablecerá cuando el jugador esté disponible
        }
    }
}