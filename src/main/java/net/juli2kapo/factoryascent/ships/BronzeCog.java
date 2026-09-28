package net.juli2kapo.factoryascent.ships;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bronze age sailing ship: helm + one passenger, a 27-slot hold (the chest on deck), a stern
 * lantern (J). The wind ({@link ShipMath#windYaw}) drives it: W sets the sail, S backs it (slow
 * astern), and the speed depends on the angle to the wind and on the weather
 * ({@link ShipMath#sailSpeedFactor}). {@link #aux()} carries the yard angle to the renderer.
 */
public class BronzeCog extends SeaShip {
    /** Top speed at 100% sail factor, blocks per tick (about 6 m/s, a bit faster than a rowing boat). */
    public static final double BASE_SPEED = 0.30;
    private static final double DRAG = 0.035;
    private static final Vec3[] SEATS = {new Vec3(0, 11 / 16.0, -9 / 16.0), new Vec3(0, 11 / 16.0, 22 / 16.0)};

    private float sailFactor = 0.85f;
    private float yard;

    public BronzeCog(EntityType<? extends BronzeCog> type, Level level) {
        super(type, level);
    }

    @Override
    public int cargoSize() {
        return 27;
    }

    @Override
    protected Vec3[] seats() {
        return SEATS;
    }

    @Override
    protected double draft() {
        return 0.18;
    }

    @Override
    protected double hullLength() {
        return 5.8;
    }

    @Override
    protected double drag() {
        return DRAG;
    }

    @Override
    protected float turnAcceleration() {
        return 0.55f;
    }

    @Override
    protected float maxTurnRate() {
        return 2.4f;
    }

    @Override
    protected double cruiseSpeed() {
        return BASE_SPEED;
    }

    /** The current sail factor (wind, heading, weather). */
    public float sailFactor() {
        return sailFactor;
    }

    @Override
    protected double thrust() {
        long time = level().getGameTime();
        float wind = ShipMath.windYaw(time);
        float strength = ShipMath.windStrength(time, level().getRainLevel(1f), level().getThunderLevel(1f));
        sailFactor = ShipMath.sailSpeedFactor(getYRot(), wind, strength, ShipConfig.windInfluence());
        // yard: braced round to the wind while sailing, squared off (0) when idle
        float target = pressed(IN_FORWARD) ? ShipMath.yardAngle(getYRot(), wind) : 0f;
        yard += (target - yard) * 0.08f;
        setAux(yard);
        double top = BASE_SPEED * ShipConfig.seaSpeed();
        if (pressed(IN_FORWARD)) return top * sailFactor * DRAG;
        if (pressed(IN_BACK)) return -top * 0.3 * DRAG;
        return 0;
    }

    @Override
    protected @Nullable Holder<SoundEvent> hornSound() {
        return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BELL_BLOCK); // the ship's bell
    }

    @Override
    protected void afterMove() {
        if (pressed(IN_FORWARD) && afloat() && tickCount % 80 == 0 && random.nextFloat() < 0.5f) {
            level().playSound(null, getX(), getY() + 3, getZ(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 0.5f, 0.6f);
        }
    }
}
