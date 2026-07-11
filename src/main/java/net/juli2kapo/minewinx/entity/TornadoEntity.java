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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class TornadoEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);

    private double pullRadius;
    private float flingDamage;
    private int lifetime;
    private double driftX;
    private double driftZ;
    @Nullable
    private UUID ownerUUID;

    public TornadoEntity(EntityType<?> type, Level level, Player owner, int stage) {
        this(type, level);
        this.ownerUUID = owner.getUUID();
        this.setStage(stage);
    }

    public TornadoEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.recalculateStats();
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STAGE, 1);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            return; // el giro del embudo lo anima el modelo con la edad de la entidad
        }

        ServerLevel serverLevel = (ServerLevel) this.level();

        // --- Deambular: el tornado se desplaza y así SUELTA a las víctimas
        // en altura (daño de caída) en vez de tenerlas flotando eternamente ---
        if (this.tickCount % 40 == 0) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            this.driftX = Math.cos(angle) * 0.14;
            this.driftZ = Math.sin(angle) * 0.14;
        }
        this.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(driftX, 0, driftZ));

        // --- Física en espiral ---
        AABB pullBox = this.getBoundingBox().inflate(pullRadius, pullRadius / 2.0, pullRadius);
        List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, pullBox,
                e -> !e.getUUID().equals(ownerUUID) && !e.isSpectator() && e.isAlive());

        for (LivingEntity victim : victims) {
            Vec3 toAxis = new Vec3(this.getX() - victim.getX(), 0, this.getZ() - victim.getZ());
            double dist = toAxis.length();

            if (dist < 1.5) {
                // En el núcleo: lanzar hacia afuera y arriba con daño
                Vec3 outward = dist < 0.01 ? new Vec3(1, 0, 0) : toAxis.normalize().scale(-1);
                victim.setDeltaMovement(outward.x * 1.2, 0.9, outward.z * 1.2);
                victim.hasImpulse = true;
                Entity owner = ownerUUID == null ? null : serverLevel.getEntity(ownerUUID);
                victim.hurt(owner instanceof Player player
                        ? this.damageSources().indirectMagic(this, player)
                        : this.damageSources().magic(), flingDamage);
            } else if (dist <= pullRadius) {
                // En el campo: componente tangencial (giro) + hacia adentro + hacia arriba
                Vec3 inward = toAxis.normalize();
                Vec3 tangential = new Vec3(-inward.z, 0, inward.x);
                double strength = 1.0 - (dist / (pullRadius + 1.0)); // más fuerte cerca del centro
                Vec3 velocity = tangential.scale(0.45)
                        .add(inward.scale(0.20 + 0.15 * strength))
                        .add(0, 0.12 + 0.10 * strength, 0);
                victim.setDeltaMovement(victim.getDeltaMovement().scale(0.5).add(velocity));
                victim.hasImpulse = true;
            }
        }

        // --- Partículas (pocas: el modelo hace el trabajo visual) ---
        // Polvo en la base
        for (int i = 0; i < 4; i++) {
            double angle = (this.tickCount * 0.35 + i * (Math.PI / 2.0));
            double r = 1.5 + this.random.nextDouble();
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.getX() + Math.cos(angle) * r, this.getY() + 0.2, this.getZ() + Math.sin(angle) * r,
                    1, 0.1, 0.05, 0.1, 0.02);
        }
        // Escombros ocasionales saliendo despedidos
        if (this.tickCount % 5 == 0) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    this.getX(), this.getY() + 1.0 + this.random.nextDouble() * 4.0, this.getZ(),
                    2, 1.0, 0.5, 1.0, 0.15);
        }

        if (this.tickCount % 30 == 0) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.5F, 0.4F);
        }

        if (this.tickCount >= lifetime) {
            this.discard();
        }
    }

    private void recalculateStats() {
        switch (this.getStage()) {
            case 1 -> { this.pullRadius = 6.0;  this.flingDamage = 4.0F; this.lifetime = 6 * 20; }
            case 2 -> { this.pullRadius = 8.0;  this.flingDamage = 6.0F; this.lifetime = 8 * 20; }
            default -> { this.pullRadius = 10.0; this.flingDamage = 8.0F; this.lifetime = 10 * 20; }
        }
    }

    public int getStage() {
        return this.entityData.get(DATA_STAGE);
    }

    public void setStage(int stage) {
        this.entityData.set(DATA_STAGE, stage);
        recalculateStats();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
        this.setStage(tag.getInt("Stage"));
        this.tickCount = tag.getInt("TickCount");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
        tag.putInt("Stage", this.getStage());
        tag.putInt("TickCount", this.tickCount);
    }
}
