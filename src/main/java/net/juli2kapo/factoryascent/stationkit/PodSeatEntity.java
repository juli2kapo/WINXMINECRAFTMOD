package net.juli2kapo.factoryascent.stationkit;

import net.juli2kapo.factoryascent.space.ReturnPodBlock;
import net.juli2kapo.factoryascent.space.SealedCabin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The seat inside a crew pod (a Return Pod floating in orbit): an invisible entity the astronaut
 * rides. Riding it is being inside the capsule, with cabin air ({@link SealedCabin}); sneak to
 * climb out onto the pod, use the pod to climb back in. Not saved: it lives while someone sits in it.
 */
public class PodSeatEntity extends Entity implements SealedCabin {
    private static final EntityDataAccessor<BlockPos> POD = SynchedEntityData.defineId(PodSeatEntity.class, EntityDataSerializers.BLOCK_POS);
    /** Seat height above the pod block's bottom (the astronaut sits low in the cone, helmet in the porthole). */
    public static final double SEAT_Y = 0.05;

    public PodSeatEntity(EntityType<? extends PodSeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static PodSeatEntity create(ServerLevel level, BlockPos pod) {
        PodSeatEntity seat = new PodSeatEntity(StationKitContent.POD_SEAT.get(), level);
        seat.entityData.set(POD, pod.immutable());
        seat.place();
        return seat;
    }

    public BlockPos pod() {
        return entityData.get(POD);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(POD, BlockPos.ZERO);
    }

    private void place() {
        BlockPos p = pod();
        setPos(p.getX() + 0.5, p.getY() + SEAT_Y, p.getZ() + 0.5);
    }

    @Override
    public void tick() {
        super.tick();
        place();
        if (level().isClientSide()) return;
        if (!(level().getBlockState(pod()).getBlock() instanceof ReturnPodBlock)) {
            ejectPassengers(); // the pod was broken: out you go
            discard();
            return;
        }
        if (getPassengers().isEmpty() && tickCount > 5) discard();
    }

    /** Climbing out: stand on top of the pod. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos p = pod();
        return new Vec3(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5);
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
