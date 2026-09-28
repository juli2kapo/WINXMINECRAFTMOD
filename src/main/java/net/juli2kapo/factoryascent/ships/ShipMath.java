package net.juli2kapo.factoryascent.ships;

import net.minecraft.util.Mth;

/**
 * The pure rules of the ships, kept free of the world so game tests can check them directly:
 * wind and sail efficiency, engine and thruster consumption, and when a shuttle changes
 * dimension. Yaw follows Minecraft's convention (0 = south / +z, 90 = west / -x).
 */
public final class ShipMath {
    private ShipMath() {}

    // ---------------------------------------------------------------- wind

    /**
     * The direction the wind blows <em>toward</em> (a yaw in degrees), the same everywhere and
     * computable on both sides from the game time alone. It veers slowly over a Minecraft day and
     * wobbles a little on top.
     */
    public static float windYaw(long gameTime) {
        double t = gameTime;
        double a = 200 + 95 * Math.sin(t / 11000.0 * Math.PI * 2 / 2.3) + 30 * Math.sin(t / 1900.0 + 1.3)
                + 8 * Math.sin(t / 157.0);
        return Mth.wrapDegrees((float) a);
    }

    /** Wind strength: about 0.6 to 0.95 in fair weather, up to about 1.45 in a thunderstorm. */
    public static float windStrength(long gameTime, float rain, float thunder) {
        double t = gameTime;
        double s = 0.78 + 0.12 * Math.sin(t / 2300.0 + 0.7) + 0.05 * Math.sin(t / 311.0) + 0.22 * rain + 0.28 * thunder;
        return (float) Math.max(0.3, s);
    }

    /**
     * Angle between a heading and the wind in degrees: 0 = running before the wind (wind dead
     * astern), 90 = beam reach, 180 = sailing straight into the wind.
     */
    public static float angleOffWind(float headingYaw, float windYaw) {
        return Math.abs(Mth.wrapDegrees(headingYaw - windYaw));
    }

    /**
     * How well a square sail pulls at a given angle off the wind: best on a broad or beam reach,
     * a little less running dead downwind, poor close-hauled and almost nothing head to wind.
     */
    public static float sailEfficiency(float angleOff) {
        float a = Mth.clamp(angleOff, 0, 180);
        float[][] curve = {{0, 0.80f}, {45, 1.0f}, {100, 1.0f}, {130, 0.62f}, {155, 0.32f}, {180, 0.22f}};
        for (int i = 0; i < curve.length - 1; i++) {
            if (a <= curve[i + 1][0]) {
                float t = (a - curve[i][0]) / (curve[i + 1][0] - curve[i][0]);
                return Mth.lerp(t, curve[i][1], curve[i + 1][1]);
            }
        }
        return curve[curve.length - 1][1];
    }

    /**
     * Multiplier on a sailing ship's top speed for a heading, wind and weather. {@code influence}
     * (config) blends from a flat 0.85 (0) to the full effect (1). Clamped to 0.25..1.35 so the
     * cog is always sailable, just slower or quicker.
     */
    public static float sailSpeedFactor(float headingYaw, float windYaw, float windStrength, double influence) {
        float full = sailEfficiency(angleOffWind(headingYaw, windYaw)) * windStrength;
        return Mth.clamp((float) Mth.lerp(influence, 0.85, full), 0.25f, 1.35f);
    }

    /** Where the yard points relative to the hull (degrees): square when running, braced round on a reach. */
    public static float yardAngle(float headingYaw, float windYaw) {
        float diff = Mth.wrapDegrees(headingYaw - windYaw);
        float mag = Math.min(62f, Math.abs(diff) * 0.55f);
        return diff >= 0 ? mag : -mag;
    }

    // ---------------------------------------------------------------- engines

    /** Motor ship: FE per tick for a throttle of -1 (astern), 0 (stop) or 1 (ahead). */
    public static int motorEnergyPerTick(int throttle, int fullAhead) {
        return throttle > 0 ? fullAhead : throttle < 0 ? fullAhead / 2 : 0;
    }

    /** FE a burnable item gives the motor ship's boiler-generator (the Combustion Generator's 40 FE per burn tick). */
    public static int energyFromBurnTime(int burnTicks) {
        return burnTicks * 40;
    }

    /**
     * Shuttle fuel units per tick. In orbit each firing axis costs 1; in an atmosphere, climbing
     * costs 6, hovering 2 (holding altitude against gravity), a controlled descent 1, and
     * horizontal thrust 3 more. Nothing is burnt while sitting on the ground.
     */
    public static int shuttleFuelPerTick(boolean orbit, boolean airborne, boolean up, boolean down, boolean horizontal,
                                         double multiplier) {
        int units;
        if (orbit) {
            units = (up || down ? 1 : 0) + (horizontal ? 1 : 0);
        } else if (!airborne && !up) {
            units = 0;
        } else {
            units = up ? 6 : down ? 1 : 2;
            if (horizontal) units += 3;
        }
        return (int) Math.ceil(units * multiplier);
    }

    // ---------------------------------------------------------------- orbit transfer

    /** Where a shuttle is, as far as orbit transfers care. */
    public enum Realm { OVERWORLD, ORBIT, OTHER }

    public enum Transfer { NONE, TO_ORBIT, TO_OVERWORLD }

    /**
     * Transfer heights.
     *
     * @param orbitMargin   blocks above the Overworld's build limit where a climbing shuttle leaves for orbit
     * @param orbitArrivalY height the shuttle arrives at in orbit
     * @param reentryY      descending below this height in orbit re-enters the Overworld
     * @param reentryDrop   blocks below the orbit threshold where a re-entering shuttle appears
     */
    public record Thresholds(int orbitMargin, int orbitArrivalY, int reentryY, int reentryDrop) {
        public static final Thresholds DEFAULT = new Thresholds(100, 160, 16, 40);

        /** Height (inclusive) above which a climbing shuttle leaves the Overworld. */
        public int orbitY(int overworldTop) {
            return overworldTop + orbitMargin;
        }

        /** Arrival height in orbit, always safely above the re-entry line (no ping-pong). */
        public int arrivalInOrbit() {
            return Math.max(orbitArrivalY, reentryY + 32);
        }

        /** Arrival height back in the Overworld, below the orbit line (no ping-pong). */
        public int arrivalInOverworld(int overworldTop) {
            return orbitY(overworldTop) - Math.max(5, reentryDrop);
        }
    }

    /**
     * Whether a shuttle at height {@code y} moving vertically at {@code vy} blocks/tick should change
     * dimension: climbing (not falling) through the orbit line in the Overworld, or descending (not
     * climbing) below the re-entry line in orbit. Other dimensions never transfer.
     */
    public static Transfer decide(Realm realm, double y, double vy, int overworldTop, Thresholds t) {
        return switch (realm) {
            case OVERWORLD -> y >= t.orbitY(overworldTop) && vy >= -0.01 ? Transfer.TO_ORBIT : Transfer.NONE;
            case ORBIT -> y < t.reentryY() && vy <= 0.01 ? Transfer.TO_OVERWORLD : Transfer.NONE;
            case OTHER -> Transfer.NONE;
        };
    }

    /** Arrival height for a transfer. */
    public static double arrivalY(Transfer transfer, int overworldTop, Thresholds t) {
        return switch (transfer) {
            case TO_ORBIT -> t.arrivalInOrbit();
            case TO_OVERWORLD -> t.arrivalInOverworld(overworldTop);
            case NONE -> Double.NaN;
        };
    }

    // ---------------------------------------------------------------- display helpers

    /** Compass heading in degrees (0 = north, 90 = east) for a yaw. */
    public static int compassHeading(float yaw) {
        return Math.floorMod(Math.round(yaw + 180f), 360);
    }

    /** Index 0..7 of the compass point (n, ne, e, se, s, sw, w, nw) nearest a heading. */
    public static int compassPoint(int heading) {
        return Math.floorMod(Math.round(heading / 45f), 8);
    }
}
