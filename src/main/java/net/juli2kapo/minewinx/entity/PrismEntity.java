package net.juli2kapo.minewinx.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Prisma de Luz (Stella, slot 3): cristal flotante. Cuando un Rayo de Sol o un
 * rayo de la Lluvia de Luz impacta cerca, el prisma refracta el golpe en varios
 * haces que buscan a los enemigos alrededor. La cantidad de haces escala con el
 * stage de la dueña.
 */
public class PrismEntity extends Entity {

    private static final double REFRACT_RADIUS = 8.0;   // qué tan cerca debe impactar un rayo
    private static final double BEAM_RANGE = 14.0;      // alcance de los haces refractados
    private static final int REFRACT_COOLDOWN = 8;      // ticks entre refracciones (la lluvia no lo satura)
    private static final int LIFETIME = 20 * 20;        // 20 segundos

    @Nullable
    private UUID ownerUUID;
    private int splits = 3;
    private int refractCooldown;

    public PrismEntity(EntityType<? extends PrismEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Apuntable: así el Rayo de Sol que mira AL prisma impacta justo ahí y refracta. */
    @Override
    public boolean isPickable() {
        return true;
    }

    public void init(Player owner, int stage) {
        this.ownerUUID = owner.getUUID();
        this.splits = 2 + Math.max(1, stage); // 3 / 4 / 5
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        // Aura de luz real: el prisma ilumina su entorno mientras vive
        if (!this.level().isClientSide() && this.tickCount % 20 == 1) {
            net.juli2kapo.minewinx.util.TransientLights.place((ServerLevel) this.level(),
                    this.blockPosition().above(), 14, 40);
        }
        if (this.level().isClientSide()) {
            // Destello suave alrededor del cristal
            if (this.tickCount % 4 == 0) {
                this.level().addParticle(ParticleTypes.END_ROD,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
                        this.getY() + 0.4 + (this.random.nextDouble() - 0.5) * 0.8,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.8,
                        0, 0.01, 0);
            }
            return;
        }
        if (refractCooldown > 0) refractCooldown--;
        if (this.tickCount >= LIFETIME) {
            ServerLevel serverLevel = (ServerLevel) this.level();
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    this.getX(), this.getY() + 0.5, this.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 1.4F);
            this.discard();
        }
    }

    /**
     * Llamar desde el impacto de un rayo de luz: busca el prisma más cercano al
     * punto de impacto y, si está listo, refracta en haces buscadores.
     */
    public static void refractAt(ServerLevel level, Vec3 impactPos, @Nullable Entity owner, float beamDamage) {
        List<PrismEntity> prisms = level.getEntitiesOfClass(PrismEntity.class,
                new net.minecraft.world.phys.AABB(impactPos, impactPos).inflate(REFRACT_RADIUS),
                p -> p.refractCooldown == 0);
        prisms.stream()
                .min(Comparator.comparingDouble(p -> p.position().distanceToSqr(impactPos)))
                .ifPresent(prism -> prism.refract(level, owner, beamDamage));
    }

    private void refract(ServerLevel level, @Nullable Entity owner, float beamDamage) {
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(BEAM_RANGE),
                e -> net.juli2kapo.minewinx.util.Targeting.isValidTarget(e, ownerUUID) && this.hasLineOfSight(e));
        if (targets.isEmpty()) return;

        targets.sort(Comparator.comparingDouble(this::distanceToSqr));
        int fired = 0;
        Vec3 from = this.position().add(0, 0.5, 0);
        for (LivingEntity target : targets) {
            if (fired >= splits) break;
            LightRayEntity beam = new LightRayEntity(ModEntities.LIGHT_RAY.get(), level);
            if (owner instanceof LivingEntity living) beam.setOwner(living);
            beam.setDamage(Math.max(2.0F, beamDamage));
            beam.setFromPrism(true);
            Vec3 dir = target.getEyePosition().subtract(from).normalize();
            // Nace fuera del cristal para no chocar consigo mismo
            Vec3 spawn = from.add(dir.scale(1.2));
            beam.setPos(spawn.x, spawn.y, spawn.z);
            beam.shoot(dir.x, dir.y, dir.z, 1.5F, 0.0F);
            level.addFreshEntity(beam);
            fired++;
        }

        if (fired > 0) {
            refractCooldown = REFRACT_COOLDOWN;
            level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 1.0F);
        }
    }

    private boolean hasLineOfSight(Entity target) {
        Vec3 from = this.position().add(0, 0.5, 0);
        Vec3 to = target.getEyePosition();
        return this.level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType()
                == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) this.ownerUUID = tag.getUUID("Owner");
        this.splits = Math.max(3, tag.getInt("Splits"));
        this.tickCount = tag.getInt("TickCount");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putInt("Splits", splits);
        tag.putInt("TickCount", this.tickCount);
    }
}
