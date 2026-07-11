package net.juli2kapo.minewinx.entity.plants;

import net.juli2kapo.minewinx.entity.ModEntities;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Planta PvZ de Flora: torreta/aliada estacionaria. Un solo tipo de entidad
 * para todas las plantas; el comportamiento se elige por PlantType sincronizado.
 */
public class PlantEntity extends Mob {

    private static final EntityDataAccessor<String> DATA_TYPE = SynchedEntityData.defineId(PlantEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_RELOADING = SynchedEntityData.defineId(PlantEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable
    private UUID ownerUUID;
    private int ownerStage = 1;
    private long plantedAt;
    private int attackCooldown;
    private int burstRemaining;
    private int fuseTicks = -1;

    public PlantEntity(EntityType<? extends PlantEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    public void init(PlantType type, Player owner, int stage) {
        this.entityData.set(DATA_TYPE, type.name());
        this.ownerUUID = owner.getUUID();
        this.ownerStage = stage;
        this.plantedAt = this.level().getGameTime();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(type.maxHealth);
        this.setHealth(type.maxHealth);
        if (type.isBomb()) {
            this.fuseTicks = type == PlantType.CHERRY_BOMB ? 30 : 40;
        }
        this.addTag("Plant");
    }

    public PlantType getPlantType() {
        return PlantType.byName(this.entityData.get(DATA_TYPE));
    }

    public boolean isReloading() {
        return this.entityData.get(DATA_RELOADING);
    }

    public long getPlantedAt() {
        return plantedAt;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_TYPE, PlantType.PEASHOOTER.name());
        this.entityData.define(DATA_RELOADING, false);
    }

    @Override
    protected void registerGoals() {
        // Sin goals: las plantas no caminan; el comportamiento vive en tick()
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) this.level();
        PlantType type = getPlantType();

        if (type.isBomb()) {
            tickBomb(serverLevel, type);
            return;
        }
        if (type.isShooter()) {
            tickShooter(serverLevel, type);
        } else if (type == PlantType.CHOMPER) {
            tickChomper(serverLevel);
        } else if (type == PlantType.COB_CANNON) {
            tickCobCannon(serverLevel);
        }
        // TORCHWOOD: pasivo — los proyectiles la detectan solos
    }

    // ------------------------------------------------------------ shooters

    private void tickShooter(ServerLevel level, PlantType type) {
        if (attackCooldown > 0) attackCooldown--;

        LivingEntity target = findNearestHostile(level, 16.0);
        if (target == null) return;
        faceTarget(target);

        if (burstRemaining > 0 && this.tickCount % 2 == 0) {
            burstRemaining--;
            shootPea(level, target, type == PlantType.SNOW_PEA ? PeaProjectileEntity.Variant.FROZEN : PeaProjectileEntity.Variant.NORMAL);
            return;
        }

        if (attackCooldown == 0) {
            switch (type) {
                case PEASHOOTER, SNOW_PEA -> {
                    shootPea(level, target, type == PlantType.SNOW_PEA ? PeaProjectileEntity.Variant.FROZEN : PeaProjectileEntity.Variant.NORMAL);
                    attackCooldown = 30;
                }
                case REPEATER -> {
                    shootPea(level, target, PeaProjectileEntity.Variant.NORMAL);
                    burstRemaining = 1;
                    attackCooldown = 30;
                }
                case GATLING_PEA -> {
                    shootPea(level, target, PeaProjectileEntity.Variant.NORMAL);
                    burstRemaining = 3;
                    attackCooldown = 20;
                }
                default -> {}
            }
        }
    }

    private void shootPea(ServerLevel level, LivingEntity target, PeaProjectileEntity.Variant variant) {
        PeaProjectileEntity pea = new PeaProjectileEntity(ModEntities.PEA_PROJECTILE.get(), level);
        pea.setVariant(variant);
        pea.setOwnerPlayer(ownerUUID);
        Vec3 from = this.position().add(0, this.getBbHeight() * 0.7, 0);
        pea.setPos(from.x, from.y, from.z);
        Vec3 dir = target.getEyePosition().subtract(from).normalize();
        pea.shoot(dir.x, dir.y, dir.z, 1.2F, 0.5F);
        level.addFreshEntity(pea);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                variant == PeaProjectileEntity.Variant.FROZEN
                        ? net.juli2kapo.minewinx.sound.ModSounds.SNOWPEA_SHOOT.get()
                        : net.juli2kapo.minewinx.sound.ModSounds.PEA_SHOOT.get(),
                SoundSource.NEUTRAL, 0.8F, 1.0F);
    }

    // ------------------------------------------------------------ chomper

    private void tickChomper(ServerLevel level) {
        if (attackCooldown > 0) {
            attackCooldown--;
            if (attackCooldown == 40) { // termina de masticar
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.juli2kapo.minewinx.sound.ModSounds.GULP.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
            return;
        }
        LivingEntity target = findNearestHostile(level, 3.0);
        if (target == null) return;
        faceTarget(target);

        float damage = 8.0F + 4.0F * ownerStage; // 12 / 16 / 20
        target.hurt(level.damageSources().mobAttack(this), damage);
        attackCooldown = 100; // "masticando"
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                net.juli2kapo.minewinx.sound.ModSounds.CHOMP.get(), SoundSource.NEUTRAL, 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                8, 0.2, 0.2, 0.2, 0.1);
    }

    // ------------------------------------------------------------ cob cannon

    private void tickCobCannon(ServerLevel level) {
        if (attackCooldown > 0) {
            attackCooldown--;
            if (attackCooldown == 20) {
                this.entityData.set(DATA_RELOADING, false); // vuelve la textura con cob
            }
            return;
        }
        LivingEntity target = findFarthestHostile(level, 24.0);
        if (target == null) return;
        faceTarget(target);

        CobProjectileEntity cob = new CobProjectileEntity(ModEntities.COB_PROJECTILE.get(), level);
        cob.setOwnerPlayer(ownerUUID);
        Vec3 from = this.position().add(0, this.getBbHeight(), 0);
        cob.setPos(from.x, from.y, from.z);
        cob.setDeltaMovement(ballisticVelocity(from, target.position(), 1.4F));
        level.addFreshEntity(cob);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                net.juli2kapo.minewinx.sound.ModSounds.COBLAUNCH.get(), SoundSource.NEUTRAL, 1.5F, 1.0F);

        this.entityData.set(DATA_RELOADING, true);
        attackCooldown = 160;
    }

    private static Vec3 ballisticVelocity(Vec3 origin, Vec3 target, float velocity) {
        double gravity = 0.05D;
        Vec3 diff = target.subtract(origin);
        double hDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        if (hDist < 0.01) return new Vec3(0, velocity, 0);
        double v2 = velocity * velocity;
        double term = v2 * v2 - gravity * (gravity * hDist * hDist + 2 * diff.y * v2);
        if (term < 0) {
            return diff.normalize().scale(velocity).add(0, 0.4, 0);
        }
        double pitch = Math.atan2(v2 - Math.sqrt(term), gravity * hDist);
        double vy = velocity * Math.sin(pitch);
        double vh = velocity * Math.cos(pitch);
        double angle = Math.atan2(diff.z, diff.x);
        return new Vec3(vh * Math.cos(angle), vy, vh * Math.sin(angle));
    }

    // ------------------------------------------------------------ bombs

    private void tickBomb(ServerLevel level, PlantType type) {
        if (fuseTicks < 0) fuseTicks = type == PlantType.CHERRY_BOMB ? 30 : 40;
        fuseTicks--;
        if (fuseTicks % 8 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + this.getBbHeight(), this.getZ(),
                    3, 0.1, 0.1, 0.1, 0.02);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.CREEPER_PRIMED, SoundSource.NEUTRAL, 0.5F, 1.5F);
        }
        if (fuseTicks > 0) return;

        boolean doom = type == PlantType.DOOM_SHROOM;
        float radius = doom ? 8.0F : 4.0F;
        float damage = doom ? (ownerStage >= 3 ? 40.0F : 28.0F) : 20.0F;

        // Daño manual (así la dueña y sus otras plantas nunca se lastiman)
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(radius),
                e -> e != this && e.isAlive() && !isFriendly(e));
        for (LivingEntity victim : victims) {
            double dist = victim.distanceTo(this);
            if (dist <= radius) {
                victim.hurt(level.damageSources().explosion(this, this), damage * (1.0F - (float) (dist / (radius + 1))));
            }
        }

        // Solo el doomshroom rompe bloques
        if (doom) {
            BlockPos center = this.blockPosition();
            int r = (int) radius;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r / 2, -r), center.offset(r, r / 2, r))) {
                if (pos.distSqr(center) > radius * radius) continue;
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || !state.getFluidState().isEmpty()) continue;
                if (level.getBlockEntity(pos) != null) continue;
                level.destroyBlock(pos, false);
            }
        }

        // "POWIE!" clásico para la cereza, nube oscura para el doomshroom
        level.sendParticles(doom ? net.juli2kapo.minewinx.particles.ModParticles.DOOM_PARTICLE.get()
                        : net.juli2kapo.minewinx.particles.ModParticles.POWIE_PARTICLE.get(),
                this.getX(), this.getY() + 1.0, this.getZ(), doom ? 30 : 12,
                doom ? 3.0 : 1.2, doom ? 2.0 : 1.0, doom ? 3.0 : 1.2, 0.05);
        level.sendParticles(doom ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
                this.getX(), this.getY() + 0.5, this.getZ(), doom ? 2 : 4, 0.5, 0.5, 0.5, 0.2);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                doom ? net.juli2kapo.minewinx.sound.ModSounds.DOOMSHROOM.get()
                        : net.juli2kapo.minewinx.sound.ModSounds.CHERRYBOMB.get(),
                SoundSource.NEUTRAL, doom ? 2.0F : 1.5F, 1.0F);
        this.discard();
    }

    // ------------------------------------------------------------ helpers

    private boolean isFriendly(LivingEntity entity) {
        if (ownerUUID != null && ownerUUID.equals(entity.getUUID())) return true;
        if (entity instanceof PlantEntity plant) {
            return ownerUUID != null && ownerUUID.equals(plant.getOwnerUUID());
        }
        return entity instanceof Player; // las bombas no matan jugadores aliados del regalo
    }

    @Nullable
    private LivingEntity findNearestHostile(ServerLevel level, double range) {
        return level.getEntitiesOfClass(Monster.class, this.getBoundingBox().inflate(range),
                        m -> m.isAlive() && !m.getTags().contains("Illusion") && this.hasLineOfSight(m))
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    @Nullable
    private LivingEntity findFarthestHostile(ServerLevel level, double range) {
        return level.getEntitiesOfClass(Monster.class, this.getBoundingBox().inflate(range),
                        m -> m.isAlive() && !m.getTags().contains("Illusion"))
                .stream()
                .max(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /** Marchitarse (para el límite de plantas): puf y desaparece. */
    public void wither() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.COMPOSTER,
                    this.getX(), this.getY() + 0.5, this.getZ(), 15, 0.3, 0.4, 0.3, 0.02);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GRASS_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
        }
        this.discard();
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        // Sin animación de muerte vanilla ni loot: se marchita
        this.wither();
    }

    @Override
    protected void dropAllDeathLoot(net.minecraft.world.damagesource.DamageSource source) {
        // sin loot
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("PlantType", this.entityData.get(DATA_TYPE));
        if (ownerUUID != null) tag.putUUID("PlantOwner", ownerUUID);
        tag.putInt("OwnerStage", ownerStage);
        tag.putLong("PlantedAt", plantedAt);
        tag.putInt("Fuse", fuseTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("PlantType")) this.entityData.set(DATA_TYPE, tag.getString("PlantType"));
        if (tag.hasUUID("PlantOwner")) this.ownerUUID = tag.getUUID("PlantOwner");
        this.ownerStage = Math.max(1, tag.getInt("OwnerStage"));
        this.plantedAt = tag.getLong("PlantedAt");
        this.fuseTicks = tag.contains("Fuse") ? tag.getInt("Fuse") : -1;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(getPlantType().maxHealth);
    }

    /** El bloque de pasto/tierra no debería romperse al pararse encima. */
    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // las plantas no reciben daño de caída
    }
}
