package net.juli2kapo.factoryascent.space;

import net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The crew seat inside a Crew Capsule on the Launch Pad: an invisible entity the astronaut rides.
 * Both sides place it every tick at the capsule's height, from the controller's synced launch
 * start and the same climb curve the rocket renderer draws, so the rider goes up exactly with the
 * rocket. Not saved: a seat only lives for one launch.
 *
 * <p>The astronaut can climb out (sneak) before liftoff, which scrubs the launch and gives the
 * fuel back; after liftoff they are strapped in until the rocket reaches orbit.
 */
public class RocketSeatEntity extends Entity implements SealedCabin {
    private static final EntityDataAccessor<BlockPos> PAD = SynchedEntityData.defineId(RocketSeatEntity.class, EntityDataSerializers.BLOCK_POS);
    /** Seat height above the controller block's bottom: pad plate (4 px) + capsule floor (95 px) + half a block. */
    public static final double SEAT_Y = 0.25 + 95 / 16.0 + 0.5;

    /** Set while the mod itself takes the rider off (arrival, aborts): lets the dismount through. */
    private boolean releasing;

    public RocketSeatEntity(EntityType<? extends RocketSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static RocketSeatEntity create(ServerLevel level, BlockPos pad) {
        RocketSeatEntity seat = new RocketSeatEntity(SpaceContent.ROCKET_SEAT.get(), level);
        seat.entityData.set(PAD, pad.immutable());
        seat.place();
        return seat;
    }

    public BlockPos pad() {
        return entityData.get(PAD);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PAD, BlockPos.ZERO);
    }

    private @Nullable LaunchControllerBlockEntity controller() {
        return level().getBlockEntity(pad()) instanceof LaunchControllerBlockEntity c ? c : null;
    }

    /** Height of the rocket above the pad now (the renderer's curve). */
    public static double climb(LaunchControllerBlockEntity c, float partialTicks) {
        if (c.launchStart() < 0 || c.getLevel() == null) return 0;
        double t = c.getLevel().getGameTime() - c.launchStart() + partialTicks - LaunchControllerBlockEntity.LIFTOFF;
        return t > 0 ? LaunchControllerBlockEntity.ACCEL * t * t : 0;
    }

    /** Past liftoff: no getting out any more. */
    public boolean strappedIn() {
        LaunchControllerBlockEntity c = controller();
        return c != null && c.launching() && c.launchTick() >= LaunchControllerBlockEntity.LIFTOFF - 1;
    }

    public boolean releasing() {
        return releasing;
    }

    /** Takes every rider off (the mod's own dismounts). */
    public void release() {
        releasing = true;
        ejectPassengers();
    }

    private void place() {
        LaunchControllerBlockEntity c = controller();
        BlockPos p = pad();
        double h = c == null ? 0 : climb(c, 0);
        setPos(p.getX() + 0.5, p.getY() + SEAT_Y + h, p.getZ() + 0.5);
    }

    @Override
    public void tick() {
        super.tick();
        place();
        if (level().isClientSide()) return;
        LaunchControllerBlockEntity c = controller();
        if (c == null) {
            release();
            discard();
            return;
        }
        if (getPassengers().isEmpty()) {
            // Climbed out before liftoff: scrub the launch. (Riders can't leave after liftoff.)
            if (c.launching() && c.launchTick() < LaunchControllerBlockEntity.LIFTOFF) c.abortLaunch();
            discard();
            return;
        }
        if (!c.launching() && tickCount > 40) { // the launch never started or was cancelled
            release();
            discard();
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return Vec3.ZERO;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
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
