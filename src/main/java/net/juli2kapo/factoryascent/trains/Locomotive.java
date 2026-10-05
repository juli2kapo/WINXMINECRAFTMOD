package net.juli2kapo.factoryascent.trains;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A locomotive: the driver sits in its cab (first seat) and works a throttle lever with W/S (it
 * moves gradually and stays where it is left, through a notch at zero; past zero is reverse),
 * Space holds the brake, H blows the whistle/horn, J switches the lights. The throttle sets the
 * target speed; how fast the train gets there depends on the locomotives' power, their fuel and
 * the train's weight ({@link TrainPhysics}). With nobody aboard it keeps its throttle (config
 * {@code driverless}), so it can run an automated line between stations.
 */
public abstract class Locomotive extends RollingStock {
    public static final int IN_FORWARD = 1, IN_BACK = 2, IN_BRAKE = 4;
    public static final int STATUS_OK = 0, STATUS_STATION = 1, STATUS_NO_FUEL = 2, STATUS_END = 3, STATUS_BLOCKED = 4,
            STATUS_WAITING = 5;
    public static final int ACTION_HORN = 0, ACTION_LIGHTS = 1, ACTION_STOP = 2;
    /** Throttle change per tick while W or S is held. */
    public static final double THROTTLE_RATE = 0.025;

    protected static final EntityDataAccessor<Float> DATA_THROTTLE = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Byte> DATA_INPUT = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Byte> DATA_STATUS = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BYTE);
    protected static final EntityDataAccessor<Boolean> DATA_LIGHTS = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Integer> DATA_GAUGE_A = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_GAUGE_B = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_GAUGE_C = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Byte> DATA_CARS = SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BYTE);

    private double throttle;
    private int input;
    private boolean scripted;
    /** Set when the lever passed through zero during one press: it rests there until the key is released. */
    private boolean notch;
    private int hornCooldown;
    private int warnCooldown;

    protected Locomotive(EntityType<? extends Locomotive> type, Level level) {
        super(type, level);
    }

    // ---------------------------------------------------------------- per locomotive

    /** Top speed at full throttle, blocks per tick. */
    public abstract double topSpeed();

    /** Pulling power: the acceleration it gives itself alone is power / mass (blocks per tick²). */
    public abstract double power();

    /** 0..1: how much of its power it can give right now (steam pressure, battery...). */
    public abstract double traction();

    /** Whether it has what it needs to run (fuel, water, charge); false shows "no fuel". */
    public boolean fuelled() {
        return traction() > 0;
    }

    /** Uses fuel for a tick of work at this effort (0..1). */
    public abstract void burn(double effort);

    /** The whistle or horn. */
    protected abstract Holder<SoundEvent> hornSound();

    protected float hornPitch() {
        return 1f;
    }

    /** Fuel figures shown by the HUD and the cab (meaning per locomotive). */
    public int gaugeA() {
        return entityData.get(DATA_GAUGE_A);
    }

    public int gaugeB() {
        return entityData.get(DATA_GAUGE_B);
    }

    public int gaugeC() {
        return entityData.get(DATA_GAUGE_C);
    }

    protected void setGauges(int a, int b, int c) {
        entityData.set(DATA_GAUGE_A, a);
        entityData.set(DATA_GAUGE_B, b);
        entityData.set(DATA_GAUGE_C, c);
    }

    @Override
    public boolean isLocomotive() {
        return true;
    }

    @Override
    public boolean hasScreen() {
        return true;
    }

    // ---------------------------------------------------------------- data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_THROTTLE, 0f);
        builder.define(DATA_INPUT, (byte) 0);
        builder.define(DATA_STATUS, (byte) 0);
        builder.define(DATA_LIGHTS, false);
        builder.define(DATA_GAUGE_A, 0);
        builder.define(DATA_GAUGE_B, 0);
        builder.define(DATA_GAUGE_C, 0);
        builder.define(DATA_CARS, (byte) 1);
    }

    /** The throttle lever, -1 (full reverse) .. 1 (full ahead). */
    public double throttle() {
        return level().isClientSide() ? entityData.get(DATA_THROTTLE) : throttle;
    }

    public void setThrottle(double value) {
        throttle = Math.max(-1, Math.min(1, value));
        entityData.set(DATA_THROTTLE, (float) throttle);
    }

    public int syncedInput() {
        return entityData.get(DATA_INPUT);
    }

    public boolean braking() {
        return (input & IN_BRAKE) != 0;
    }

    public int status() {
        return entityData.get(DATA_STATUS);
    }

    void setStatus(int status) {
        if (entityData.get(DATA_STATUS) != status) entityData.set(DATA_STATUS, (byte) status);
    }

    public int cars() {
        return entityData.get(DATA_CARS);
    }

    void setCars(int n) {
        entityData.set(DATA_CARS, (byte) Math.min(127, n));
    }

    public boolean lightsOn() {
        return entityData.get(DATA_LIGHTS);
    }

    public void setLights(boolean on) {
        entityData.set(DATA_LIGHTS, on);
    }

    /** The driver (first passenger, a player), if any. */
    public @Nullable Player driver() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    /** Key bits from the driver's client (validated by {@link TrainPayloads}). */
    public void setInput(int bits) {
        input = bits & 7;
        scripted = false;
    }

    /** Game tests: work the controls without a driver. */
    public void setScriptedInput(int bits) {
        input = bits & 7;
        scripted = true;
    }

    // ---------------------------------------------------------------- ticking

    @Override
    protected void serverTick() {
        if (hornCooldown > 0) hornCooldown--;
        if (warnCooldown > 0) warnCooldown--;
        if (driver() == null && !scripted) {
            input = 0;
            if (!TrainConfig.driverless()) setThrottle(0);
        }
        boolean fwd = (input & IN_FORWARD) != 0, back = (input & IN_BACK) != 0;
        if (fwd == back) {
            notch = false;
        } else if (!notch) {
            double before = throttle;
            double after = before + (fwd ? THROTTLE_RATE : -THROTTLE_RATE);
            if (before != 0 && Math.signum(after) != Math.signum(before) || Math.abs(after) < 1e-6) {
                after = 0; // rest in the notch until the key is let go
                notch = true;
            }
            setThrottle(after);
        }
        entityData.set(DATA_INPUT, (byte) input);
        if (!trainDriven() || spot == null) setStatus(STATUS_WAITING);
        Player d = driver();
        if (d != null && warnCooldown == 0 && status() == STATUS_NO_FUEL) {
            d.sendOverlayMessage(noFuelMessage());
            warnCooldown = 60;
        }
    }

    protected Component noFuelMessage() {
        return Component.translatable("message.factoryascent.train.no_fuel");
    }

    /** Horn / lights / stop from the driver (or the cab screen). */
    public void action(Player player, int action) {
        switch (action) {
            case ACTION_HORN -> {
                if (hornCooldown == 0) {
                    hornCooldown = 30;
                    blowHorn();
                    gameEvent(GameEvent.INSTRUMENT_PLAY, player);
                }
            }
            case ACTION_LIGHTS -> setLights(!lightsOn());
            case ACTION_STOP -> setThrottle(0);
            default -> {}
        }
    }

    protected void blowHorn() {
        level().playSound(null, getX(), getY() + 2, getZ(), hornSound(), SoundSource.NEUTRAL, 4f, hornPitch());
    }

    /** Fuel only goes into the fuel slots, and hoppers may only fill them (never pull fuel out). */
    public boolean canTakeItem(net.minecraft.world.Container into, int slot, ItemStack stack) {
        return false;
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putDouble("Throttle", throttle);
        output.putBoolean("Lights", lightsOn());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setThrottle(input.getDoubleOr("Throttle", 0));
        setLights(input.getBooleanOr("Lights", false));
    }

    @Override
    protected net.minecraft.world.phys.Vec3[] seats() {
        return new net.minecraft.world.phys.Vec3[] {cab()};
    }

    /** Where the driver sits. */
    protected abstract net.minecraft.world.phys.Vec3 cab();
}
