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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public class PlayerIllusionEntity extends Zombie {

    private static final EntityDataAccessor<CompoundTag> GAME_PROFILE_TAG =
            SynchedEntityData.defineId(PlayerIllusionEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private int stage = 1;
    private int hp = 1;
    public PlayerIllusionEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        // --- FIXES ---
        // 1. Prevent it from being a baby
        this.setBaby(false);

        // 2. Prevent it from picking up loot
        this.setCanPickUpLoot(false);

        // 3. Clear any default equipment the Zombie class might add
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);

        // Original settings
        this.setInvulnerable(true);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        if (source.getEntity() instanceof Player) {
            if (this.hp <= 1) {
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
            } else {
                this.hp -= 1;
                return super.hurt(source, amount);
            }
        }
        return false;
    }
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(2, new PlayerLikeMovementGoal(this, 1.5D));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
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
     * Equips an iron sword.
     * Renamed from equipSwordIfNeeded for clarity.
     */
    public void configureByStage(int stage) {
        this.stage = stage;
        this.hp = stage;
        switch (stage) {
            case 1:
                // Parcial armadura de hierro, espada de hierro, 1 golpe
                this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
                this.setHealth(2.0F);
                break;
            case 2:
                // Mitad hierro, mitad diamante, espada de diamante, 2 golpes
                this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
                this.setHealth(6.0F);
                break;
            case 3:
                // Full diamante con encantamientos, espada de diamante con encantamientos, 3 golpes
                ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
                helmet.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 2);
                ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
                chest.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 2);
                ItemStack legs = new ItemStack(Items.DIAMOND_LEGGINGS);
                legs.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 2);
                ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
                boots.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 2);
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                sword.enchant(Enchantments.SHARPNESS, 2);

                this.setItemSlot(EquipmentSlot.HEAD, helmet);
                this.setItemSlot(EquipmentSlot.CHEST, chest);
                this.setItemSlot(EquipmentSlot.LEGS, legs);
                this.setItemSlot(EquipmentSlot.FEET, boots);
                this.setItemInHand(InteractionHand.MAIN_HAND, sword);
                this.setHealth(12.0F);
                break;
            default:
                // Sin equipo, 1 golpe
                this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                this.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                this.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                this.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                this.setHealth(2.0F);
        }
        this.setPersistenceRequired();
    }

    /**
     * Makes the illusion attack a target.
     */
    public void attackTarget(Entity target) {
        this.getNavigation().moveTo(target, 2);
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.getTarget() != target && target instanceof LivingEntity livingTarget) {
            this.setTarget(livingTarget);
        }
    }

    /**
     * Makes the illusion move to a location.
     */
    public void moveToLocation(Vec3 position) {
        this.getNavigation().moveTo(position.x, position.y, position.z, 1.5);
        this.setTarget(null);
    }

    static class PlayerLikeMovementGoal extends Goal {
        private final PlayerIllusionEntity illusion;
        private final double speedModifier;

        public PlayerLikeMovementGoal(PlayerIllusionEntity illusion, double speedModifier) {
            this.illusion = illusion;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !illusion.getNavigation().isDone() && illusion.getTarget() == null;
        }

        @Override
        public void start() {
            illusion.setSprinting(true);
        }

        @Override
        public void tick() {
            if (illusion.getNavigation().getPath() != null) {
                var targetPos = illusion.getNavigation().getTargetPos();
                if (targetPos != null) {
                    illusion.getLookControl().setLookAt(targetPos.getX(), targetPos.getY(), targetPos.getZ());
                }
            }
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