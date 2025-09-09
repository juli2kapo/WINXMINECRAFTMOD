package net.juli2kapo.minewinx.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class PistonEntity extends Entity {
    // Animation and timing constants - adjust these to align with your animation
    private static final int ANIMATION_DURATION = 40; // 2 seconds at 20 TPS
    private static final int IMPACT_START_TICK = 2; // Initial impact delay (when to start breaking)
    private static final int LAYER_BREAK_DELAY = 3; // Ticks between each layer break (adjust for timing)
    
    private final AnimationState animationState = new AnimationState();
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(PistonEntity.class, EntityDataSerializers.FLOAT);

    private boolean hasStartedImpact = false;
    private int currentLayer = 0;
    private int lastLayerBreakTick = 0;

    public PistonEntity(EntityType<? extends PistonEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true; // So it doesn't fall
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SCALE, 1.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
        if (DATA_SCALE.equals(pKey)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(pKey);
    }

    @Override
    public EntityDimensions getDimensions(Pose pPose) {
        return super.getDimensions(pPose).scale(getScale());
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide()) {
            this.animationState.startIfStopped(this.tickCount);
        }

        if (!this.level().isClientSide()) {
            // Ensure scale is set from persistent data on the server side
            float scale = getPersistentData().getFloat("scale");
            if (this.getScale() != scale) {
                this.setScale(scale);
            }

            // Handle initial impact
            if (this.tickCount >= IMPACT_START_TICK && !hasStartedImpact) {
                performInitialImpact();
                hasStartedImpact = true;
                lastLayerBreakTick = this.tickCount;
            }

            // Break blocks layer by layer with artificial delays
            if (hasStartedImpact && this.tickCount >= IMPACT_START_TICK) {
                int depth = getPersistentData().getInt("depth") + 1;
                
                // Check if enough time has passed since the last layer break
                if (this.tickCount >= lastLayerBreakTick + LAYER_BREAK_DELAY && currentLayer < depth - 1) {
                    breakLayer(currentLayer + 1);
                    currentLayer++;
                    lastLayerBreakTick = this.tickCount;
                }
            }
        }

        // Remove after animation completes
        if (this.tickCount > ANIMATION_DURATION + 20) {
            this.discard();
        }
    }

    private void performInitialImpact() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        BlockPos impactPos = new BlockPos(
                getPersistentData().getInt("impactX"),
                getPersistentData().getInt("impactY"),
                getPersistentData().getInt("impactZ")
        );

        float damage = getPersistentData().getFloat("damage");
        float scale = getScale();

        // Get the caster
        String casterUUIDStr = getPersistentData().getString("casterUUID");
        Player caster = null;
        if (!casterUUIDStr.isEmpty()) {
            try {
                UUID casterUUID = UUID.fromString(casterUUIDStr);
                caster = serverLevel.getPlayerByUUID(casterUUID);
            } catch (IllegalArgumentException ignored) {}
        }

        // Create final reference for lambda
        final Player finalCaster = caster;

        // Damage entities in impact area
        float impactSize = scale;
        AABB damageArea = new AABB(impactPos.getX() - impactSize / 2, impactPos.getY() - 2, impactPos.getZ() - impactSize / 2,
                impactPos.getX() + impactSize / 2, impactPos.getY() + 2, impactPos.getZ() + impactSize / 2);
        List<LivingEntity> entities = serverLevel.getEntitiesOfClass(LivingEntity.class, damageArea,
                entity -> finalCaster == null || (entity != finalCaster && entity.isAlive()));

        for (LivingEntity entity : entities) {
            if (finalCaster != null) {
                entity.hurt(finalCaster.damageSources().indirectMagic(finalCaster, finalCaster), damage);
            } else {
                entity.hurt(serverLevel.damageSources().magic(), damage);
            }

            // Push entities down
            Vec3 pushDirection = new Vec3(0, -1.5, 0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pushDirection));
        }

        // Break the surface layer immediately
        breakLayer(0);

        // Create initial impact effects
        serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                impactPos.getX(), impactPos.getY(), impactPos.getZ(),
                20, impactSize / 2.0, 1.0, impactSize / 2.0, 0.2);

        // Play impact sound
        serverLevel.playSound(null, impactPos.getX(), impactPos.getY(), impactPos.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 2.0F, 0.8F);
    }

    private void breakLayer(int layer) {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        BlockPos impactPos = new BlockPos(
                getPersistentData().getInt("impactX"),
                getPersistentData().getInt("impactY"),
                getPersistentData().getInt("impactZ")
        );

        float scale = getScale();
        String casterUUIDStr = getPersistentData().getString("casterUUID");
        Player caster = null;
        if (!casterUUIDStr.isEmpty()) {
            try {
                UUID casterUUID = UUID.fromString(casterUUIDStr);
                caster = serverLevel.getPlayerByUUID(casterUUID);
            } catch (IllegalArgumentException ignored) {}
        }

        int breakRadius = (int) (scale / 2.0f);
        int y = layer; // Layer depth

        // Break blocks in this layer
        for (int x = -breakRadius; x <= breakRadius; x++) {
            for (int z = -breakRadius; z <= breakRadius; z++) {
                BlockPos blockPos = impactPos.offset(x, -y, z);
                BlockState state = serverLevel.getBlockState(blockPos);

                if (!state.isAir() && state.getDestroySpeed(serverLevel, blockPos) >= 0) {
                    serverLevel.destroyBlock(blockPos, true, caster);
                }
            }
        }

        // Add particles for this layer
        serverLevel.sendParticles(ParticleTypes.CLOUD,
                impactPos.getX(), impactPos.getY() - layer, impactPos.getZ(),
                15, scale / 2.0, 0.2, scale / 2.0, 0.1);

        // Play breaking sound for each layer
        if (layer > 0) {
            serverLevel.playSound(null, impactPos.getX(), impactPos.getY() - layer, impactPos.getZ(),
                    SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F + (layer * 0.1F));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("hasStartedImpact", hasStartedImpact);
        tag.putInt("currentLayer", currentLayer);
        tag.putInt("lastLayerBreakTick", lastLayerBreakTick);
        tag.putFloat("scale", this.getScale());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        hasStartedImpact = tag.getBoolean("hasStartedImpact");
        currentLayer = tag.getInt("currentLayer");
        lastLayerBreakTick = tag.getInt("lastLayerBreakTick");
        if (tag.contains("scale")) {
            this.setScale(tag.getFloat("scale"));
        }
    }

    public float getAnimationProgress() {
        if (this.tickCount <= ANIMATION_DURATION) {
            return Math.min(1.0f, (float) this.tickCount / ANIMATION_DURATION);
        }
        return 1.0f;
    }

    public AnimationState getAnimationState() {
        return animationState;
    }

    public void setScale(float scale) {
        this.entityData.set(DATA_SCALE, scale);
        this.refreshDimensions();
    }

    public float getScale() {
        return this.entityData.get(DATA_SCALE);
    }
}