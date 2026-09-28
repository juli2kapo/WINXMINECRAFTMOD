package net.juli2kapo.factoryascent.ships;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A ship that floats: buoyancy toward a draft below the water surface, momentum along the keel
 * (thrust against a small drag, so it takes a while to get going and coasts a long way), sideways
 * slip killed by the keel, a rudder that bites harder the faster you go, and beaching on land.
 * Waves are drawn by the renderer; the wake and bow spray are client particles.
 */
public abstract class SeaShip extends AbstractShip {
    public static final int STATE_AFLOAT = 0, STATE_AGROUND = 1;

    /** Last measured water surface (NaN when out of water). */
    private double waterLevel = Double.NaN;

    protected SeaShip(EntityType<? extends SeaShip> type, Level level) {
        super(type, level);
    }

    /** How far the entity's feet sit below the water surface, in blocks. */
    protected abstract double draft();

    /** Forward acceleration this tick (blocks/tick²), negative astern. May use up fuel. */
    protected abstract double thrust();

    /** Fraction of forward speed lost per tick in water. Top speed = thrust / drag. */
    protected abstract double drag();

    /** Rudder: degrees per tick² at full speed, and the most degrees per tick. */
    protected abstract float turnAcceleration();

    protected abstract float maxTurnRate();

    /** Nominal top speed (blocks/tick), for the rudder's bite. */
    protected abstract double cruiseSpeed();

    private float turnRate;

    public boolean afloat() {
        return state() == STATE_AFLOAT;
    }

    /** The highest water surface under the hull, or NaN. */
    protected double measureWater() {
        AABB bb = getBoundingBox();
        int x0 = Mth.floor(bb.minX), x1 = Mth.ceil(bb.maxX);
        int z0 = Mth.floor(bb.minZ), z1 = Mth.ceil(bb.maxZ);
        int y0 = Mth.floor(bb.minY - 1.2), y1 = Mth.ceil(bb.maxY + 0.5);
        double best = Double.NaN;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                for (int y = y0; y < y1; y++) {
                    pos.set(x, y, z);
                    FluidState fluid = level().getFluidState(pos);
                    if (fluid.is(FluidTags.WATER)) {
                        double surface = y + fluid.getHeight(level(), pos);
                        if (Double.isNaN(best) || surface > best) best = surface;
                    }
                }
            }
        }
        return best;
    }

    @Override
    protected void physics() {
        waterLevel = measureWater();
        boolean inWater = !Double.isNaN(waterLevel) && waterLevel > getY() - 0.35;
        setState(inWater ? STATE_AFLOAT : STATE_AGROUND);
        Vec3 v = getDeltaMovement();
        // vertical: spring toward the draft line, or fall
        double vy = v.y;
        if (inWater) {
            double target = waterLevel - draft();
            vy = vy * 0.7 + (target - getY()) * 0.12;
            vy = Mth.clamp(vy, -0.3, 0.3);
        } else {
            vy = (vy - 0.04) * 0.98;
        }
        // horizontal: split into keel and sideways components
        Vec3 fwd = forward();
        double along = v.x * fwd.x + v.z * fwd.z;
        Vec3 side = new Vec3(v.x - fwd.x * along, 0, v.z - fwd.z * along);
        double push = inWater ? thrust() : thrust() * 0.15;
        if (inWater) {
            along = along * (1 - drag()) + push;
            side = side.scale(0.82);
        } else {
            along = (along + push) * (onGround() ? 0.55 : 0.95);
            side = side.scale(onGround() ? 0.5 : 0.95);
        }
        // rudder
        int steer = (pressed(IN_RIGHT) ? 1 : 0) - (pressed(IN_LEFT) ? 1 : 0);
        double bite = 0.3 + 0.7 * Math.min(1, Math.abs(along) / cruiseSpeed());
        if (along < -0.005) steer = -steer; // going astern the rudder works backwards
        turnRate += (float) (steer * turnAcceleration() * bite);
        turnRate *= inWater ? 0.82f : 0.4f;
        turnRate = Mth.clamp(turnRate, -maxTurnRate(), maxTurnRate());
        setYRot(getYRot() + turnRate);
        setDeltaMovement(fwd.x * along + side.x, vy, fwd.z * along + side.z);
    }

    /** Degrees per tick the ship is turning (for heel and the rudder). */
    public float turnRate() {
        return turnRate;
    }

    @Override
    protected void clientEffects() {
        double speed = horizontalSpeed();
        if (!afloat() || speed < 0.04) return;
        Vec3 fwd = forward();
        Vec3 side = new Vec3(fwd.z, 0, -fwd.x);
        double half = hullLength() / 2;
        double beam = getBbWidth() / 2;
        double y = getY() + draft() + 0.05;
        // bow spray
        if (random.nextFloat() < speed * 4) {
            for (int s : new int[] {1, -1}) {
                Vec3 p = position().add(fwd.scale(half * 0.85)).add(side.scale(s * beam * 0.5));
                level().addParticle(ParticleTypes.SPLASH, p.x, y + 0.1, p.z, side.x * s * 0.2, 0.1, side.z * s * 0.2);
            }
        }
        // wake behind the stern and along the sides
        for (int i = 0; i < 2; i++) {
            Vec3 p = position().add(fwd.scale(-half * (0.8 + random.nextFloat() * 0.3)))
                    .add(side.scale((random.nextFloat() - 0.5) * beam * 1.2));
            level().addParticle(ParticleTypes.FISHING, p.x, y, p.z, 0, 0, 0);
        }
        if (random.nextFloat() < speed * 3) {
            int s = random.nextBoolean() ? 1 : -1;
            Vec3 p = position().add(fwd.scale((random.nextFloat() - 0.3) * half)).add(side.scale(s * (beam + 0.2)));
            level().addParticle(ParticleTypes.FISHING, p.x, y, p.z, 0, 0, 0);
        }
    }

    /** Length of the hull in blocks (the model is longer than the square hit box). */
    protected abstract double hullLength();
}
