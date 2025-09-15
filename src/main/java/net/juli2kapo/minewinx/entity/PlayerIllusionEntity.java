package net.juli2kapo.minewinx.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public class PlayerIllusionEntity extends Zombie {

    private static final EntityDataAccessor<CompoundTag> GAME_PROFILE_TAG =
            SynchedEntityData.defineId(PlayerIllusionEntity.class, EntityDataSerializers.COMPOUND_TAG);

    public PlayerIllusionEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.setCanPickUpLoot(false);
        this.setInvulnerable(true); // Hacer invulnerable a daño ambiental
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        // Solo se destruye si el daño proviene de un jugador
        if (source.getEntity() instanceof Player) {
            if (!this.level().isClientSide) {
                Vec3 pos = this.position();
                this.level().playSound(null, pos.x, pos.y, pos.z,
                        SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);

                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE,
                            pos.x, pos.y + this.getBbHeight() / 2.0, pos.z,
                            15, 0.3, 0.3, 0.3, 0.1);
                }

                this.discard();
            }
            return true;
        }
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PlayerLikeMovementGoal(this, 2D)); // Velocidad de carrera aumentada
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(5, new PlayerLikeMovementGoal(this, 1.0D));
    }

    public Optional<GameProfile> getGameProfile() {
        CompoundTag tag = this.entityData.get(GAME_PROFILE_TAG);
        if (!tag.isEmpty()) {
            return Optional.of(NbtUtils.readGameProfile(tag));
        }
        return Optional.empty();
    }

    public void setGameProfile(GameProfile gameProfile) {
        CompoundTag tag = new CompoundTag();
        NbtUtils.writeGameProfile(tag, gameProfile);
        this.entityData.set(GAME_PROFILE_TAG, tag);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(GAME_PROFILE_TAG, new CompoundTag());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        this.getGameProfile().ifPresent(gp -> {
            CompoundTag profileTag = new CompoundTag();
            NbtUtils.writeGameProfile(profileTag, gp);
            compound.put("IllusionProfile", profileTag);
        });
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("IllusionProfile", 10)) {
            this.setGameProfile(NbtUtils.readGameProfile(compound.getCompound("IllusionProfile")));
        }
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }


    /**
     * Equipa una espada de hierro si la ilusión no tiene arma
     */
    public void equipSwordIfNeeded() {
        if (this.getMainHandItem().isEmpty()) {
            this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        }
    }

    /**
     * Hace que la ilusión ataque a un objetivo
     */
    public void attackTarget(Entity target) {
        // Mover hacia el objetivo
        this.getNavigation().moveTo(target, 1.5);

        // Simular ataque (animar y mirar hacia el objetivo)
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Establecer como objetivo persistente
        if (this.getTarget() != target && target instanceof LivingEntity livingTarget) {
            this.setTarget(livingTarget);
        }
    }

    /**
     * Hace que la ilusión se mueva a una ubicación
     */
    public void moveToLocation(Vec3 position) {
        this.getNavigation().moveTo(position.x, position.y, position.z, 1.5);
        this.setTarget(null);
    }

    // --- Nueva clase interna para el movimiento ---
    static class PlayerLikeMovementGoal extends Goal {
        private final PlayerIllusionEntity illusion;
        private final double speedModifier;

        public PlayerLikeMovementGoal(PlayerIllusionEntity illusion, double speedModifier) {
            this.illusion = illusion;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            // Se activa si la ilusión tiene una ruta de navegación
            return !illusion.getNavigation().isDone();
        }

        @Override
        public void tick() {
            // Correr
            illusion.setSprinting(true);

            // Saltar tan pronto como toque el suelo
            if (illusion.onGround()) {
                illusion.getJumpControl().jump();
            }
        }

        @Override
        public void stop() {
            illusion.setSprinting(false);
        }
    }
}