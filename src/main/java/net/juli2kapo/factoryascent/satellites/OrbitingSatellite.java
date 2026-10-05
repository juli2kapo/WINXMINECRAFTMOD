package net.juli2kapo.factoryascent.satellites;

import java.util.UUID;
import net.juli2kapo.factoryascent.orbital.OrbitRegistry;
import net.juli2kapo.factoryascent.orbital.SatelliteType;
import net.juli2kapo.factoryascent.ships.AbstractShip;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The body of one of the Overworld's satellites ({@link OrbitRegistry}) flying in Earth orbit.
 *
 * <p>Never saved: {@link SatelliteBodies} spawns one while the orbit chunk under the satellite's
 * position is ticking and removes it when that chunk stops ticking, the satellite leaves the
 * registry (deorbited, shot down) or the feature is switched off. Both sides move it along its orbit
 * ({@link SatelliteOrbit}) from the synced orbit data and the game time, so it glides smoothly
 * without position packets and every player sees it at the same place.
 *
 * <p>It is solid (you bump into it in a jetpack); a ship that touches it crashes: both blow apart
 * ({@link SatelliteBodies#collide}).
 */
public class OrbitingSatellite extends Entity {
    private static final EntityDataAccessor<Long> DATA_ID_HI = SynchedEntityData.defineId(OrbitingSatellite.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_ID_LO = SynchedEntityData.defineId(OrbitingSatellite.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_LAUNCH = SynchedEntityData.defineId(OrbitingSatellite.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<BlockPos> DATA_CENTER = SynchedEntityData.defineId(OrbitingSatellite.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DATA_ALTITUDE = SynchedEntityData.defineId(OrbitingSatellite.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TYPE = SynchedEntityData.defineId(OrbitingSatellite.class, EntityDataSerializers.INT);

    public OrbitingSatellite(EntityType<? extends OrbitingSatellite> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ID_HI, 0L);
        builder.define(DATA_ID_LO, 0L);
        builder.define(DATA_LAUNCH, 0L);
        builder.define(DATA_CENTER, BlockPos.ZERO);
        builder.define(DATA_ALTITUDE, 320);
        builder.define(DATA_TYPE, 0);
    }

    /** Links this body to a satellite and puts it where the orbit has it now. */
    public void link(UUID satellite, SatelliteType type, long launchTime, BlockPos center, int altitude) {
        entityData.set(DATA_ID_HI, satellite.getMostSignificantBits());
        entityData.set(DATA_ID_LO, satellite.getLeastSignificantBits());
        entityData.set(DATA_LAUNCH, launchTime);
        entityData.set(DATA_CENTER, center.immutable());
        entityData.set(DATA_ALTITUDE, altitude);
        entityData.set(DATA_TYPE, type.ordinal());
        follow(level().getGameTime());
        setOldPosAndRot();
    }

    public UUID satelliteId() {
        return new UUID(entityData.get(DATA_ID_HI), entityData.get(DATA_ID_LO));
    }

    public SatelliteType satelliteType() {
        SatelliteType[] all = SatelliteType.values();
        return all[Math.floorMod(entityData.get(DATA_TYPE), all.length)];
    }

    public long launchTime() {
        return entityData.get(DATA_LAUNCH);
    }

    public BlockPos center() {
        return entityData.get(DATA_CENTER);
    }

    public int altitude() {
        return entityData.get(DATA_ALTITUDE);
    }

    /** Where the orbit has it at {@code time}. */
    public Vec3 orbitPosition(double time) {
        BlockPos c = center();
        return SatelliteOrbit.position(satelliteId(), launchTime(), c.getX() + 0.5, c.getZ() + 0.5, altitude(), time);
    }

    /** Moves along the orbit; only in Earth orbit (a body placed anywhere else, as in the game tests, holds still). */
    private void follow(double time) {
        if (!level().dimension().equals(SpaceRules.ORBIT)) return;
        Vec3 p = orbitPosition(time);
        setPos(p.x, p.y, p.z);
        setYRot(SatelliteOrbit.yaw(satelliteId(), launchTime(), time));
    }

    @Override
    public void tick() {
        super.tick();
        follow(level().getGameTime());
        if (level() instanceof ServerLevel server) {
            if (tickCount % 20 == 0 && (!SatelliteConfig.physical()
                    || OrbitRegistry.get(server.getServer()).find(satelliteId()).isEmpty())) {
                discard();
                return;
            }
            for (AbstractShip ship : server.getEntitiesOfClass(AbstractShip.class, getBoundingBox().inflate(0.75), s -> !s.isRemoved())) {
                SatelliteBodies.collide(server, this, ship);
                if (isRemoved()) return;
            }
        } else {
            SatelliteBodies.seenOnClient(satelliteId(), level().getGameTime());
        }
    }

    // ---------------------------------------------------------------- physical rules

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true; // solid: players in jetpacks bump into it, ships crash into it
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false; // only a collision (or a missile) takes it down
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 1024 * 1024; // seen from far away (drawn nearer and smaller, see the renderer)
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
