package net.juli2kapo.minewinx.entity;

import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class SunRay extends Entity {

    private int life;
    private boolean visualOnly;
    @Nullable
    private ServerPlayer cause;
    private final Set<Entity> hitEntities = Sets.newHashSet();
    private float damage = 5.0F;

    public SunRay(EntityType<? extends SunRay> entityType, Level level) {
        super(entityType, level);
        this.noCulling = true;
        this.life = 20; // Aumentamos la vida para un efecto más duradero
    }

    public void setVisualOnly(boolean visualOnly) {
        this.visualOnly = visualOnly;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.WEATHER;
    }

    public void setCause(@Nullable ServerPlayer cause) {
        this.cause = cause;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public float getDamage() {
        return this.damage;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            for (int i = 0; i < 2; ++i) {
                this.level().addParticle(ParticleTypes.WAX_ON, this.getX() + (this.random.nextDouble() - 0.5D) * 2.0D, this.getY() + this.random.nextDouble() * 10, this.getZ() + (this.random.nextDouble() - 0.5D) * 2.0D, 0.0D, 0.0D, 0.0D);
                this.level().addParticle(ParticleTypes.FLAME, this.getX() + (this.random.nextDouble() - 0.5D) * 2.0D, this.getY() + this.random.nextDouble() * 10, this.getZ() + (this.random.nextDouble() - 0.5D) * 2.0D, 0.0D, 0.0D, 0.0D);
            }
        } else {
            if (this.life == 20) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.WEATHER, 10000.0F, 0.8F + this.random.nextFloat() * 0.2F);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.WEATHER, 2.0F, 0.5F + this.random.nextFloat() * 0.2F);
            }

            --this.life;
            if (this.life < 0) {
                this.discard();
            }

            if (this.life >= 0 && !this.visualOnly) {
                List<Entity> list = this.level().getEntities(this, new AABB(this.getX() - 2.0D, this.getY(), this.getZ() - 2.0D, this.getX() + 2.0D, this.getY() + this.level().getHeight(), this.getZ() + 2.0D), Entity::isAlive);
                for (Entity entity : list) {
                    if (entity instanceof LivingEntity && !this.hitEntities.contains(entity)) {
                        entity.hurt(this.damageSources().inFire(), this.damage);
                        entity.setSecondsOnFire(5);
                    }
                }
                this.hitEntities.addAll(list);
            }

            if (!this.visualOnly && this.level().getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
                this.spawnFire(1);
            }
        }
    }

    private void spawnFire(int extraIgnitions) {
        if (this.visualOnly || !this.level().getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            return;
        }

        BlockPos blockpos = this.blockPosition();
        BlockState blockstate = BaseFireBlock.getState(this.level(), blockpos);
        if (this.level().getBlockState(blockpos).isAir() && blockstate.canSurvive(this.level(), blockpos)) {
            this.level().setBlockAndUpdate(blockpos, blockstate);
        }

        for (int i = 0; i < extraIgnitions; ++i) {
            BlockPos blockpos1 = blockpos.offset(this.random.nextInt(3) - 1, 0, this.random.nextInt(3) - 1);
            blockstate = BaseFireBlock.getState(this.level(), blockpos1);
            if (this.level().getBlockState(blockpos1).isAir() && blockstate.canSurvive(this.level(), blockpos1)) {
                this.level().setBlockAndUpdate(blockpos1, blockstate);
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        // No se necesitan datos sincronizados por ahora
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        // No se necesitan datos adicionales para leer
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        // No se necesitan datos adicionales para guardar
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}