package net.juli2kapo.factoryascent.ships;

import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What every ship shares: seats, a cargo hold (a {@link Container}, so hoppers and pipes work, and
 * the {@link ShipMenu} opened with sneak + use or the inventory key while aboard), an optional
 * fuel/power slot after the hold, lights, input from the pilot, save/load, and breaking into its
 * item with the hold and fuel packed inside it (see {@link ShipConfig#CARGO_IN_ITEM}).
 *
 * <p>Ships are simulated on the server only: the pilot's client sends key states
 * ({@link ShipPayloads.Input}) and everyone, the pilot included, sees the server's positions
 * smoothed by the entity's {@link InterpolationHandler}. That is why {@link #getControllingPassenger()}
 * is null: vanilla would otherwise hand the physics to the pilot's client.
 */
public abstract class AbstractShip extends VehicleEntity implements HasCustomInventoryScreen, Container, MenuProvider {
    public static final int IN_FORWARD = 1, IN_BACK = 2, IN_LEFT = 4, IN_RIGHT = 8, IN_UP = 16, IN_DOWN = 32, IN_SPRINT = 64;

    protected static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(AbstractShip.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Byte> DATA_INPUT = SynchedEntityData.defineId(AbstractShip.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Boolean> DATA_LIGHTS = SynchedEntityData.defineId(AbstractShip.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(AbstractShip.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Float> DATA_AUX = SynchedEntityData.defineId(AbstractShip.class, EntityDataSerializers.FLOAT);

    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    protected final NonNullList<ItemStack> items;
    /** Key bits from the pilot (server), see IN_*. */
    protected int input;
    /** Degrees turned this tick (both sides; passengers turn with the ship). */
    protected float deltaRotation;
    /** Inputs set by a game test: kept without a pilot. */
    private boolean scriptedInput;
    /** Set while this ship itself moves its passengers (dimension change, hatch): dismounts are allowed. */
    boolean allowDismount;
    private int hornCooldown;

    /** Client-side animation, read by the renderer (propeller angle, main thrust, lift thrust, leg retraction). */
    public float spin, spinO, thrust, thrustO, lift, liftO, legs, legsO;
    /** Client: speeds measured from the interpolated positions, smoothed. */
    private double clientSpeed, clientClimb, lastX = Double.NaN, lastY, lastZ;

    protected AbstractShip(EntityType<? extends AbstractShip> type, Level level) {
        super(type, level);
        this.items = NonNullList.withSize(cargoSize() + (fuelSlot() >= 0 ? 1 : 0), ItemStack.EMPTY);
        this.blocksBuilding = true;
    }

    // ---------------------------------------------------------------- per ship

    /** Hold slots (27 or 54...). */
    public abstract int cargoSize();

    /** Index of the fuel / power slot after the hold, or -1. */
    public int fuelSlot() {
        return -1;
    }

    /** Seat positions in blocks: x across (+ = port), y up from the entity's feet, z forward. The first is the helm. */
    protected abstract Vec3[] seats();

    /** Fuel shown by the HUD and kept in the item (FE for the motor ship, tank units for the shuttle). */
    public int fuel() {
        return 0;
    }

    public int fuelCapacity() {
        return 0;
    }

    protected void setFuel(int value) {}

    /** Server-side movement for one tick: set yaw and delta movement; {@link #move} is applied after. */
    protected abstract void physics();

    /** Client-side effects for one tick (particles). */
    protected void clientEffects() {}

    /** Client-side: advance the animation fields. */
    protected void animate() {}

    /** Degrees the ship turned this tick (client: from the interpolated yaw). */
    public float turnThisTick() {
        return deltaRotation;
    }

    /** Called on the server after moving. */
    protected void afterMove() {}

    /** What the horn key does (null: nothing). */
    protected net.minecraft.core.@Nullable Holder<net.minecraft.sounds.SoundEvent> hornSound() {
        return null;
    }

    /** Whether the helm screen is called the cockpit. */
    public boolean isSpacecraft() {
        return false;
    }

    // ---------------------------------------------------------------- data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_INPUT, (byte) 0);
        builder.define(DATA_LIGHTS, false);
        builder.define(DATA_STATE, (byte) 0);
        builder.define(DATA_AUX, 0f);
    }

    public int syncedFuel() {
        return entityData.get(DATA_FUEL);
    }

    public int syncedInput() {
        return entityData.get(DATA_INPUT);
    }

    public boolean lightsOn() {
        return entityData.get(DATA_LIGHTS);
    }

    public void setLights(boolean on) {
        entityData.set(DATA_LIGHTS, on);
    }

    public int state() {
        return entityData.get(DATA_STATE);
    }

    protected void setState(int state) {
        entityData.set(DATA_STATE, (byte) state);
    }

    /** Ship-specific synced number (the cog's yard angle, the shuttle's vertical speed). */
    public float aux() {
        return entityData.get(DATA_AUX);
    }

    protected void setAux(float value) {
        entityData.set(DATA_AUX, value);
    }

    public int input() {
        return input;
    }

    public boolean pressed(int bit) {
        return (input & bit) != 0;
    }

    /** From the pilot's client (validated by {@link ShipPayloads}). */
    public void setInput(int bits) {
        this.input = bits & 0x7F;
        this.scriptedInput = false;
    }

    /** Game tests: drive the ship without a pilot. */
    public void setScriptedInput(int bits) {
        this.input = bits & 0x7F;
        this.scriptedInput = true;
    }

    public @Nullable Player pilot() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    /** Horizontal speed in blocks per tick. */
    public double horizontalSpeed() {
        if (level().isClientSide()) return clientSpeed;
        Vec3 v = getDeltaMovement();
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }

    /** Vertical speed in blocks per tick (the client measures it from the smoothed positions). */
    public double verticalSpeed() {
        return level().isClientSide() ? clientClimb : getDeltaMovement().y;
    }

    /** Unit vector of the bow. */
    public Vec3 forward() {
        float r = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Override
    public void tick() {
        if (getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
        if (getDamage() > 0) setDamage(getDamage() - 1);
        if (hornCooldown > 0) hornCooldown--;
        float yawBefore = getYRot();
        super.tick();
        interpolation.interpolate();
        if (!level().isClientSide()) {
            if (pilot() == null && !scriptedInput) input = 0;
            physics();
            move(MoverType.SELF, getDeltaMovement());
            afterMove();
            entityData.set(DATA_INPUT, (byte) input);
            entityData.set(DATA_FUEL, fuel());
            pushEntities();
        } else {
            spinO = spin;
            thrustO = thrust;
            liftO = lift;
            legsO = legs;
            if (!Double.isNaN(lastX)) {
                double dx = getX() - lastX, dz = getZ() - lastZ;
                clientSpeed = clientSpeed * 0.6 + Math.sqrt(dx * dx + dz * dz) * 0.4;
                clientClimb = clientClimb * 0.6 + (getY() - lastY) * 0.4;
            }
            lastX = getX();
            lastY = getY();
            lastZ = getZ();
            animate();
            clientEffects();
        }
        deltaRotation = Mth.wrapDegrees(getYRot() - (level().isClientSide() ? yRotO : yawBefore));
        applyEffectsFromBlocks();
    }

    /** Shoves creatures and items out of the way (ships are heavy: they are not pushed back). */
    private void pushEntities() {
        List<Entity> list = level().getEntities(this, getBoundingBox().inflate(0.25, -0.01, 0.25), EntitySelector.pushableBy(this));
        Vec3 v = getDeltaMovement();
        for (Entity e : list) {
            if (e.hasPassenger(this) || isPassengerOfSameVehicle(e) || e instanceof AbstractShip) continue;
            double dx = e.getX() - getX(), dz = e.getZ() - getZ();
            double d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
            e.push(dx / d * 0.08 + v.x * 0.6, 0, dz / d * 0.08 + v.z * 0.6);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return (entity.canBeCollidedWith(this) || entity.isPushable()) && !isPassengerOfSameVehicle(entity);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null; // server-authoritative, see the class comment
    }

    // ---------------------------------------------------------------- seats

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < seats().length;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int i = Math.max(0, getPassengers().indexOf(passenger));
        Vec3[] seats = seats();
        Vec3 seat = seats[Math.min(i, seats.length - 1)];
        return seat.yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    /** Boarding faces you the way the ship points. */
    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        passenger.setYRot(getYRot());
        passenger.setYHeadRot(getYRot());
        passenger.setXRot(Math.min(passenger.getXRot(), 20f));
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (deltaRotation != 0) {
            passenger.setYRot(passenger.getYRot() + deltaRotation);
            passenger.setYHeadRot(passenger.getYHeadRot() + deltaRotation);
        }
        if (passenger instanceof LivingEntity living && !(passenger instanceof Player)) living.setYBodyRot(getYRot());
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        if (passenger instanceof LivingEntity living && !(passenger instanceof Player)) living.setYBodyRot(getYRot());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        double reach = getBbWidth() / 2 + passenger.getBbWidth() + 0.3;
        Vec3 fwd = forward();
        Vec3 side = new Vec3(fwd.z, 0, -fwd.x);
        for (Vec3 dir : new Vec3[] {side, side.scale(-1), fwd, fwd.scale(-1)}) {
            Vec3 at = position().add(dir.scale(reach));
            // from just above the hull down to below the keel: the first floor a passenger fits on
            net.minecraft.core.BlockPos top = net.minecraft.core.BlockPos.containing(at.x, getBoundingBox().maxY + 1, at.z);
            int depth = (int) Math.ceil(getBoundingBox().getYsize()) + 4;
            for (int dy = 0; dy <= depth; dy++) {
                net.minecraft.core.BlockPos p = top.below(dy);
                if (level().isWaterAt(p.below()) && level().isWaterAt(p)) continue;
                double floor = level().getBlockFloorHeight(p);
                if (!DismountHelper.isBlockFloorValid(floor)) continue;
                Vec3 target = new Vec3(at.x, p.getY() + floor, at.z);
                for (Pose pose : passenger.getDismountPoses()) {
                    if (DismountHelper.canDismountTo(level(), target, passenger, pose)) {
                        passenger.setPose(pose);
                        return target;
                    }
                }
            }
        }
        // at sea: stand on deck
        return new Vec3(getX(), getBoundingBox().maxY, getZ());
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        InteractionResult base = super.interact(player, hand, location);
        if (base != InteractionResult.PASS) return base;
        if (player.isSecondaryUseActive()) {
            if (!level().isClientSide()) openCustomInventoryScreen(player);
            return InteractionResult.SUCCESS;
        }
        if (!canAddPassenger(player)) return InteractionResult.PASS;
        if (!level().isClientSide()) return player.startRiding(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return InteractionResult.SUCCESS;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.openMenu(this, buf -> buf.writeVarInt(getId()));
            gameEvent(GameEvent.CONTAINER_OPEN, player);
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ShipMenu(containerId, inventory, this);
    }

    /** Horn / lights / hatch keys from the pilot (or any passenger for lights). */
    public void action(Player player, int action) {
        switch (action) {
            case ShipPayloads.ACTION_HORN -> {
                var sound = hornSound();
                if (sound != null && hornCooldown == 0 && player == pilot()) {
                    hornCooldown = 50;
                    level().playSound(null, getX(), getY() + 2, getZ(), sound, net.minecraft.sounds.SoundSource.NEUTRAL, 4f, 0.75f);
                    gameEvent(GameEvent.INSTRUMENT_PLAY, player);
                }
            }
            case ShipPayloads.ACTION_LIGHTS -> setLights(!lightsOn());
            case ShipPayloads.ACTION_HATCH -> hatch(player);
            default -> {}
        }
    }

    /** The shuttle's "leave" key; ships just let you off. */
    protected void hatch(Player player) {
        releasePassenger(player);
    }

    /** Dismounts a passenger even when a ship would normally refuse (see ShipContent's mount event). */
    protected void releasePassenger(Entity passenger) {
        allowDismount = true;
        try {
            passenger.stopRiding();
        } finally {
            allowDismount = false;
        }
    }

    /** Whether a passenger may leave by sneaking right now (the shuttle refuses in flight). */
    public boolean canDismountBySneaking() {
        return true;
    }

    // ---------------------------------------------------------------- damage and drops

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return super.hurtServer(level, source, damage * 0.5f); // ships take twice the hits of a boat
    }

    @Override
    protected Item getDropItem() {
        return ShipContent.itemFor(getType());
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        boolean drops = level.getGameRules().get(GameRules.ENTITY_DROPS);
        ItemStack stack = new ItemStack(getDropItem());
        if (drops && ShipConfig.cargoInItem()) {
            writeToItem(stack);
            clearContent(); // packed into the item: nothing left for remove() to spill
        }
        kill(level); // remove() spills whatever is still aboard
        if (drops) spawnAtLocation(level, stack);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy()) {
            Containers.dropContents(level(), this, this);
        }
        super.remove(reason);
    }

    /** Packs the hold, the fuel and the name into a ship item. */
    public void writeToItem(ItemStack stack) {
        boolean any = items.stream().anyMatch(s -> !s.isEmpty());
        if (any) stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        if (fuel() > 0) stack.set(ShipContent.SHIP_FUEL.get(), fuel());
        if (getCustomName() != null) stack.set(DataComponents.CUSTOM_NAME, getCustomName());
    }

    /** Unpacks what {@link #writeToItem} stored. */
    public void readFromItem(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) contents.copyInto(items);
        Integer fuel = stack.get(ShipContent.SHIP_FUEL.get());
        if (fuel != null) setFuel(fuel);
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) setCustomName(name);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        ContainerHelper.saveAllItems(output, items);
        output.putInt("fuel", fuel());
        output.putBoolean("lights", lightsOn());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        setFuel(input.getIntOr("fuel", 0));
        setLights(input.getBooleanOr("lights", false));
    }

    // ---------------------------------------------------------------- container (hold + fuel slot)

    public NonNullList<ItemStack> items() {
        return items;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return ContainerHelper.removeItem(items, slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) items.set(slot, stack);
    }

    /** What a slot accepts: ships never go inside ships; the fuel slot takes only fuel. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (stack.getItem() instanceof ShipItem) return false;
        return slot != fuelSlot() || acceptsFuel(stack);
    }

    /** Whether the fuel/power slot takes this stack. */
    public boolean acceptsFuel(ItemStack stack) {
        return false;
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return isAlive() && player.distanceToSqr(this) < 10 * 10;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
    }
}
