package net.juli2kapo.minewinx.entity;

import net.juli2kapo.minewinx.effect.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TsunamiEntity extends Entity {
    private Vec3 direction;
    private double speed;
    private double width;
    private double height;
    private int lifetime;
    @Nullable
    private Player owner;
    @Nullable
    private UUID ownerUUID;
    private int stage;
    private final List<BlockPos> placedBlocks = new ArrayList<>();

    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(TsunamiEntity.class, EntityDataSerializers.INT);

    // Constructor para cuando un jugador invoca la entidad
    public TsunamiEntity(EntityType<?> type, Level world, Player player, int stage) {
        super(type, world);
        this.setOwner(player);
        this.setStage(stage);
        this.recalculateDimensions();
        this.setPos(player.position().subtract(player.getLookAngle().scale(10)));
        this.direction = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize();
    }

    // Constructor para el registro y la carga desde el mundo
    public TsunamiEntity(EntityType<?> type, Level world) {
        super(type, world);
        this.direction = Vec3.ZERO;
        this.lifetime = 120;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STAGE, 1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
        if (DATA_STAGE.equals(pKey)) {
            this.recalculateDimensions();
        }
        super.onSyncedDataUpdated(pKey);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.owner == null && this.ownerUUID != null && this.level() instanceof ServerLevel) {
            Entity entity = ((ServerLevel)this.level()).getEntity(this.ownerUUID);
            if (entity instanceof Player) {
                this.setOwner((Player)entity);
            }
        }

        if (!this.level().isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) this.level();

            // Empujar y dañar entidades
            AABB pushBox = this.getBoundingBox().inflate(width / 2, height / 2, width / 2);
            List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, pushBox,
                    e -> e != this.owner && !e.isSpectator() && e.isAlive());
            for (LivingEntity entity : entities) {
                entity.push(direction.x * (0.1), 0.1, direction.z * (0.1));
                if (this.getOwner() != null) {
                    entity.hurt(this.damageSources().indirectMagic(this, this.getOwner()), 1.0f + stage);
                    // Efecto de presión de agua para Stage 3
                    if (this.stage >= 3) {
                        int amplifier = entity.hasEffect(ModEffects.DROWNING_TARGET.get()) ? 1 : 0;
                        entity.addEffect(new MobEffectInstance(ModEffects.WATER_PRESSURE.get(), 100, amplifier, false, true));
                    }
                }
            }

            // Colocar bloques de agua temporales
            Vec3 perpendicular = new Vec3(-direction.z, 0, direction.x).normalize();
            Vec3 waveCenter = this.position();
            for (double w = -width / 2; w <= width / 2; w += 1.0) {
                for (double h = 0; h < height; h += 1.0) {
                    BlockPos waterPos = BlockPos.containing(waveCenter.add(perpendicular.scale(w)).add(0, h, 0));
                    if (serverLevel.getBlockState(waterPos).canBeReplaced() &&
                            !serverLevel.getBlockState(waterPos).is(Blocks.WATER)) {
                        serverLevel.setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
                        placedBlocks.add(waterPos.immutable());
                    }
                }
            }

            // Sonido ocasional
            if (this.tickCount % 40 == 0) {
                serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, SoundSource.PLAYERS, 1.5F, 1.0F);
            }
        } else {
            // Lado del cliente: Partículas
            Vec3 perpendicular = new Vec3(-direction.z, 0, direction.x).normalize();
            for (double w = -width / 2; w <= width / 2; w += 0.5) {
                double particleHeight = height * Mth.sin((float) (this.tickCount * 0.1));
                if (particleHeight <= 0) continue;
                Vec3 particlePos = this.position().add(perpendicular.scale(w)).add(0, particleHeight, 0);
                this.level().addParticle(ParticleTypes.SPLASH, particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
                this.level().addParticle(ParticleTypes.BUBBLE_COLUMN_UP, particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
            }
        }

        // Moverse hacia adelante
        this.setPos(this.getX() + direction.x * speed, this.getY(), this.getZ() + direction.z * speed);

        // Eliminar entidad después de su vida útil
        if (this.tickCount >= lifetime) {
            this.discard();
        }
    }


    @Override
    protected void readAdditionalSaveData(CompoundTag pCompound) {
        if (pCompound.hasUUID("Owner")) {
            this.ownerUUID = pCompound.getUUID("Owner");
        }
        if (pCompound.contains("Direction", 9)) {
            this.direction = new Vec3(pCompound.getList("Direction", 6).getDouble(0), 0, pCompound.getList("Direction", 6).getDouble(2)).normalize();
        }
        this.setStage(pCompound.getInt("Stage"));
        this.tickCount = pCompound.getInt("TickCount");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag pCompound) {
        if (this.ownerUUID != null) {
            pCompound.putUUID("Owner", this.ownerUUID);
        }
        if (this.direction != null) {
            pCompound.put("Direction", this.newDoubleList(this.direction.x, this.direction.y, this.direction.z));
        }
        pCompound.putInt("Stage", this.getStage());
        pCompound.putInt("TickCount", this.tickCount);
    }

    private void recalculateDimensions() {
        this.stage = getStage();
        switch (this.stage) {
            case 1:
                this.speed = 0.25;
                this.width = 8.0;
                this.height = 3.0;
                this.lifetime = 120; // 6 segundos
                break;
            case 2:
                this.speed = 0.30;
                this.width = 20.0;
                this.height = 4.0;
                this.lifetime = 160; // 8 segundos
                break;
            default: // Stage 3 y superior
                this.speed = 0.30;
                this.width = 30.0;
                this.height = 5.0;
                this.lifetime = 160; // 8 segundos
                break;
        }
    }

    public void setOwner(@Nullable Player owner) {
        this.owner = owner;
        this.ownerUUID = owner == null ? null : owner.getUUID();
    }

    @Nullable
    public Player getOwner() {
        return owner;
    }

    public int getStage() {
        return this.entityData.get(DATA_STAGE);
    }

    public void setStage(int stage) {
        this.entityData.set(DATA_STAGE, stage);
        recalculateDimensions();
    }
}