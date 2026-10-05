package net.juli2kapo.factoryascent.trains;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
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
 * Everything that runs on rails in a train: locomotives and wagons. A rolling stock is a real
 * minecart ({@link AbstractMinecart}): detector rails see it, hoppers and comparators reach the
 * containers among them, activator rails trigger them, and a wagon on its own rolls, gets pushed
 * and rides powered rails exactly like a minecart (vanilla physics).
 *
 * <p>Once vehicles are coupled (or one is a locomotive) they form a {@link Consist} that
 * {@link TrainPhysics} drives along the track with {@link TrackWalker}: one member (the master,
 * a locomotive when there is one) moves, the others are placed at their coupling distance behind
 * and ahead of it along the rails. Spacing is exact on straights, curves and slopes, and nothing
 * can derail or tear apart; a coupling only breaks when the track under a wagon disappears, a
 * junction is switched under the train or one of the pair is destroyed.
 *
 * <p>Each vehicle has a front and a rear coupler ({@link #link(boolean)}) holding the partner's
 * UUID, a heading (synced, for the renderer and the seats) and its speed along its own front.
 */
public abstract class RollingStock extends AbstractMinecart implements MenuProvider, HasCustomInventoryScreen {
    protected static final EntityDataAccessor<Float> DATA_HEADING = SynchedEntityData.defineId(RollingStock.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> DATA_PITCH = SynchedEntityData.defineId(RollingStock.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(RollingStock.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Byte> DATA_LINKS = SynchedEntityData.defineId(RollingStock.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Integer> DATA_FILL = SynchedEntityData.defineId(RollingStock.class, EntityDataSerializers.INT);

    /** Gap between two coupled vehicles' buffers, in blocks. */
    public static final double GAP = 0.1;

    protected final NonNullList<ItemStack> items;
    private @Nullable UUID frontLink, rearLink;
    /** Server: where on the track this vehicle is (null when off the rails or not yet known). */
    TrackWalker.@Nullable Spot spot;
    /** Server: whether this vehicle's front faces end B of its spot's rail. */
    boolean frontTowardB = true;
    /** Speed along this vehicle's own front, blocks per tick (negative = rolling backwards). */
    double speed;
    /** A push (from a player walking into it) waiting to be added to the train's speed, along the front. */
    double pendingImpulse;
    /** Ticks a Train Station still holds this vehicle (and so its whole train). */
    int stationHold;
    /** Game time this vehicle was last moved by its train's master. */
    long drivenAt = -1;

    /** Client: wheel angle (radians) and the previous tick's, and a smoothed heading for rendering. */
    public float wheel, wheelO;
    public float headingO, headingR, pitchO, pitchR;
    private boolean headingInit;

    protected RollingStock(EntityType<? extends RollingStock> type, Level level) {
        super(type, level);
        this.items = NonNullList.withSize(inventorySize(), ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- per vehicle

    /** Length over the buffers, in blocks (sets the coupling distance). */
    public abstract double length();

    /** Mass in "minecarts" (loads add to it): heavier trains pull away slower. */
    public double mass() {
        return 1.0;
    }

    /** Slots of the vehicle's inventory (0 = none). */
    public int inventorySize() {
        return 0;
    }

    /** Seat positions in blocks: x across (+ = left), y up from the rails, z forward. */
    protected Vec3[] seats() {
        return new Vec3[0];
    }

    /** Whether right-clicking (or sneak-right-clicking when it has seats) opens a screen. */
    public boolean hasScreen() {
        return inventorySize() > 0;
    }

    public boolean isLocomotive() {
        return false;
    }

    /** Server tick work of the vehicle itself (fuel, suction...), after it moved. */
    protected void serverTick() {}

    /** Client tick work (particles, sounds, animation). */
    protected void clientTick() {}

    // ---------------------------------------------------------------- synced data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HEADING, 0f);
        builder.define(DATA_PITCH, 0f);
        builder.define(DATA_SPEED, 0f);
        builder.define(DATA_LINKS, (byte) 0);
        builder.define(DATA_FILL, 0);
    }

    /** Yaw of the vehicle's front (Minecraft convention: 0 = south). */
    public float heading() {
        return entityData.get(DATA_HEADING);
    }

    public float pitch() {
        return entityData.get(DATA_PITCH);
    }

    /** Speed along the front in blocks per tick (synced). */
    public float syncedSpeed() {
        return entityData.get(DATA_SPEED);
    }

    public double speed() {
        return speed;
    }

    public void setTrainSpeed(double v) {
        this.speed = v;
    }

    /** Couplers in use: bit 0 front, bit 1 rear (synced, for drawing the coupling bars). */
    public int linkBits() {
        return entityData.get(DATA_LINKS);
    }

    /** A 0..1000 load figure for the renderer (hopper heap, tank level). */
    public int fill() {
        return entityData.get(DATA_FILL);
    }

    protected void setFill(int value) {
        if (entityData.get(DATA_FILL) != value) entityData.set(DATA_FILL, value);
    }

    /** Unit vector of the front (horizontal). */
    public Vec3 front() {
        float r = heading() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    void setHeading(float yaw, float pitch) {
        entityData.set(DATA_HEADING, Mth.wrapDegrees(yaw));
        entityData.set(DATA_PITCH, pitch);
    }

    /** Points the vehicle (used when it is placed): its front toward this horizontal direction. */
    public void face(Vec3 dir) {
        TrackWalker.Spot s = TrackWalker.locate(level(), position(), this);
        if (s != null) {
            spot = s;
            frontTowardB = TrackWalker.pointsTowardB(s, dir);
            updateHeadingFromSpot();
        } else {
            setHeading(TrackWalker.yaw(dir), 0);
        }
    }

    void updateHeadingFromSpot() {
        if (spot == null) return;
        Vec3 t = spot.tangent();
        Vec3 f = frontTowardB ? t : t.scale(-1);
        setHeading(TrackWalker.yaw(f), TrackWalker.climb(f));
    }

    // ---------------------------------------------------------------- couplings

    public @Nullable UUID link(boolean front) {
        return front ? frontLink : rearLink;
    }

    void setLink(boolean front, @Nullable UUID id) {
        if (front) frontLink = id;
        else rearLink = id;
        entityData.set(DATA_LINKS, (byte) ((frontLink != null ? 1 : 0) | (rearLink != null ? 2 : 0)));
    }

    public boolean hasLinks() {
        return frontLink != null || rearLink != null;
    }

    /** Which coupler (true = front) holds the link to this vehicle, or null. */
    public @Nullable Boolean endLinkedTo(RollingStock other) {
        if (other.getUUID().equals(frontLink)) return true;
        if (other.getUUID().equals(rearLink)) return false;
        return null;
    }

    /** Drops the coupling to a vehicle on both sides. */
    public void uncouple(RollingStock other) {
        Boolean mine = endLinkedTo(other);
        if (mine != null) setLink(mine, null);
        Boolean theirs = other.endLinkedTo(this);
        if (theirs != null) other.setLink(theirs, null);
    }

    /** Server: the linked vehicle at one end, if loaded. */
    public @Nullable RollingStock linked(boolean front) {
        UUID id = link(front);
        if (id == null || !(level() instanceof ServerLevel server)) return null;
        return server.getEntity(id) instanceof RollingStock r && r.isAlive() ? r : null;
    }

    // ---------------------------------------------------------------- ticking

    /** Whether the train logic (rather than vanilla minecart physics) moves this vehicle. */
    boolean trainDriven() {
        return isLocomotive() || hasLinks();
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel server) {
            if (stationHold > 0) stationHold--;
            boolean onTrack = spot() != null;
            if (trainDriven() && onTrack) {
                if (getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
                if (getDamage() > 0) setDamage(getDamage() - 1);
                checkBelowWorld();
                Consist consist = Consist.of(this);
                if (consist.master() == this) {
                    TrainPhysics.step(server, consist);
                } else if (!consist.complete()) {
                    speed = 0;
                    setDeltaMovement(Vec3.ZERO);
                } else if (drivenAt < server.getGameTime() - 2) {
                    setDeltaMovement(Vec3.ZERO); // waiting for the master (not ticking this tick)
                }
                firstTick = false;
            } else {
                super.tick();
                freeRolling();
            }
            entityData.set(DATA_SPEED, (float) speed);
            serverTick();
        } else {
            super.tick();
            animate();
            clientTick();
        }
    }

    /** After vanilla physics moved a lone wagon: keep the track spot, heading and speed in step. */
    private void freeRolling() {
        TrackWalker.Spot s = TrackWalker.locate(level(), position(), this);
        Vec3 f = front();
        spot = s;
        if (s != null) {
            frontTowardB = TrackWalker.pointsTowardB(s, f);
            updateHeadingFromSpot();
        }
        Vec3 v = getDeltaMovement();
        speed = v.x * f.x + v.z * f.z;
    }

    private void animate() {
        float target = heading(), targetPitch = pitch();
        if (!headingInit) {
            headingR = headingO = target;
            pitchR = pitchO = targetPitch;
            headingInit = true;
        }
        headingO = headingR;
        pitchO = pitchR;
        headingR = headingR + Mth.wrapDegrees(target - headingR) * 0.5f;
        pitchR = pitchR + (targetPitch - pitchR) * 0.5f;
        wheelO = wheel;
        wheel += syncedSpeed() / wheelRadius();
    }

    /** Radius of the running wheels in blocks (drives the wheel animation). */
    public float wheelRadius() {
        return 3.5f / 16f;
    }

    /** Render yaw for a partial tick. */
    public float renderHeading(float partial) {
        return headingO + Mth.wrapDegrees(headingR - headingO) * partial;
    }

    public float renderPitch(float partial) {
        return Mth.lerp(partial, pitchO, pitchR);
    }

    // ---------------------------------------------------------------- pushes and collisions

    @Override
    public void push(Entity other) {
        if (other instanceof RollingStock r && Consist.sameTrain(this, r)) return;
        if (trainDriven()) {
            if (isLocomotive() || other instanceof AbstractMinecart) return; // heavy: carts bounce off a train
            if (!(other instanceof Player)) return;
            // a player leaning on a coupled wagon (no locomotive) nudges the whole train along
            double dx = getX() - other.getX(), dz = getZ() - other.getZ();
            double d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
            Vec3 f = front();
            pendingImpulse += (dx / d * f.x + dz / d * f.z) * 0.01;
            return;
        }
        super.push(other);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        if (entity instanceof RollingStock r && Consist.sameTrain(this, r)) return false;
        return super.canCollideWith(entity);
    }

    // ---------------------------------------------------------------- seats

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < seats().length;
    }

    @Override
    public boolean isRideable() {
        return false; // never scoop up mobs like a plain minecart
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        Vec3[] seats = seats();
        if (seats.length == 0) return super.getPassengerAttachmentPoint(passenger, dimensions, scale);
        int i = Math.max(0, getPassengers().indexOf(passenger));
        Vec3 seat = seats[Math.min(i, seats.length - 1)];
        return seat.yRot(-heading() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        passenger.setYRot(heading());
        passenger.setYHeadRot(heading());
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        super.positionRider(passenger, moveFunction);
        if (passenger instanceof LivingEntity living && !(passenger instanceof Player)) living.setYBodyRot(heading());
    }

    // ---------------------------------------------------------------- interaction

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof CouplerItem coupler) return coupler.useOn(this, player, hand, location);
        InteractionResult special = interactWith(player, hand, held);
        if (special != InteractionResult.PASS) return special;
        boolean seats = seats().length > 0;
        if (hasScreen() && (player.isSecondaryUseActive() || !seats)) {
            if (!level().isClientSide()) openCustomInventoryScreen(player);
            return InteractionResult.SUCCESS;
        }
        if (seats && !player.isSecondaryUseActive() && canAddPassenger(player)) {
            if (!level().isClientSide()) return player.startRiding(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Vehicle-specific right-click (buckets on tanks...); PASS to fall through. */
    protected InteractionResult interactWith(Player player, InteractionHand hand, ItemStack held) {
        return InteractionResult.PASS;
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (hasScreen() && player instanceof ServerPlayer sp) {
            sp.openMenu(this, buf -> buf.writeVarInt(getId()));
            gameEvent(GameEvent.CONTAINER_OPEN, player);
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TrainMenu(containerId, inventory, this);
    }

    // ---------------------------------------------------------------- damage, drops, removal

    @Override
    protected Item getDropItem() {
        return TrainContent.itemFor(getType());
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    protected void destroy(ServerLevel level, DamageSource source) {
        boolean drops = level.getGameRules().get(GameRules.ENTITY_DROPS);
        ItemStack stack = new ItemStack(getDropItem());
        if (drops && TrainConfig.cargoInItem()) {
            writeToItem(stack);
            clearCargo(); // packed into the item: nothing left for remove() to spill
        }
        kill(level);
        if (drops) spawnAtLocation(level, stack);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy()) {
            for (boolean end : new boolean[] {true, false}) {
                RollingStock other = linked(end);
                if (other != null) uncouple(other);
            }
            if (this instanceof Container c) Containers.dropContents(level(), this, c);
        }
        super.remove(reason);
    }

    /** Empties whatever {@link #writeToItem} packed (inventory, tanks, charge). */
    protected void clearCargo() {
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
    }

    /** Packs the inventory and the name into the vehicle's item (subclasses add tanks and charge). */
    public void writeToItem(ItemStack stack) {
        if (items.stream().anyMatch(s -> !s.isEmpty())) stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        if (getCustomName() != null) stack.set(DataComponents.CUSTOM_NAME, getCustomName());
    }

    /** Unpacks what {@link #writeToItem} stored. */
    public void readFromItem(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) contents.copyInto(items);
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name != null) setCustomName(name);
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (!items.isEmpty()) ContainerHelper.saveAllItems(output, items);
        output.storeNullable("FrontLink", UUIDUtil.CODEC, frontLink);
        output.storeNullable("RearLink", UUIDUtil.CODEC, rearLink);
        output.putFloat("Heading", heading());
        output.putDouble("TrainSpeed", speed);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        if (!items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(input, items);
        }
        Optional<UUID> f = input.read("FrontLink", UUIDUtil.CODEC), r = input.read("RearLink", UUIDUtil.CODEC);
        setLink(true, f.orElse(null));
        setLink(false, r.orElse(null));
        float h = input.getFloatOr("Heading", 0f);
        setHeading(h, 0);
        speed = input.getDoubleOr("TrainSpeed", 0);
        spot = null; // found again from the position (and the heading) on the first tick
        float rad = h * Mth.DEG_TO_RAD;
        pendingFront = new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
    }

    /** Heading read from the save, applied to the track spot once it is found. */
    private @Nullable Vec3 pendingFront;

    /** Server: the track spot (found from the position when needed), with the front kept from the heading. */
    TrackWalker.@Nullable Spot spot() {
        if (spot == null) {
            spot = TrackWalker.locate(level(), position(), this);
            if (spot != null) {
                frontTowardB = TrackWalker.pointsTowardB(spot, pendingFront != null ? pendingFront : front());
                pendingFront = null;
            }
        }
        return spot;
    }

    // ---------------------------------------------------------------- inventory (for the vehicles that are Containers)

    public NonNullList<ItemStack> items() {
        return items;
    }

    public int getContainerSize() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    public ItemStack removeItem(int slot, int count) {
        return ContainerHelper.removeItem(items, slot, count);
    }

    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) items.set(slot, stack);
    }

    /** Vehicles never go inside vehicles. */
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return !(stack.getItem() instanceof TrainItem);
    }

    public void setChanged() {}

    public boolean stillValid(Player player) {
        return isAlive() && player.distanceToSqr(this) < 10 * 10;
    }

    public void clearContent() {
        for (int i = 0; i < items.size(); i++) items.set(i, ItemStack.EMPTY);
    }
}
