package net.juli2kapo.factoryascent.satellites;

import java.util.UUID;
import net.juli2kapo.factoryascent.orbital.Satellite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/**
 * Where an Overworld satellite really is in Earth orbit ({@code factoryascent:orbit}): a pure
 * function of its id, its launch time and the spot it circles over, so the server, every client
 * and every restart agree without saving anything.
 *
 * <p>Each satellite flies a circle around the launch pad it lifted off from (the world spawn for
 * satellites without one), {@link #MIN_RADIUS}..{@link #MAX_RADIUS} blocks out, so its track
 * crosses the sky over the base that launched it. Height: the configured altitude plus up to
 * {@link #ALTITUDE_SPREAD} blocks, rocking {@link #TILT} blocks up and down once per lap (an
 * inclined orbit). Speed {@link #MIN_SPEED}..{@link #MAX_SPEED} blocks per tick (a lap takes one to
 * five minutes), prograde or retrograde. The phase starts from the id and runs from the launch time.
 */
public final class SatelliteOrbit {
    public static final double MIN_RADIUS = 48, MAX_RADIUS = 256;
    public static final double MIN_SPEED = 0.25, MAX_SPEED = 0.45;
    public static final double ALTITUDE_SPREAD = 48, TILT = 12;

    private SatelliteOrbit() {}

    /** The shape of one satellite's orbit (all derived from the id). */
    public record Params(double radius, double speed, double phase, int direction, double heightOffset, double tiltPhase) {
        /** Ticks per lap. */
        public double period() {
            return 2 * Math.PI * radius / speed;
        }
    }

    private static double frac(long bits, int shift) {
        return ((bits >>> shift) & 0xFFFF) / 65536.0;
    }

    public static Params params(UUID id) {
        long a = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L, b = id.getLeastSignificantBits() * 0xC2B2AE3D27D4EB4FL;
        a ^= b >>> 29;
        b ^= a >>> 31;
        double radius = MIN_RADIUS + frac(a, 0) * (MAX_RADIUS - MIN_RADIUS);
        double speed = MIN_SPEED + frac(a, 16) * (MAX_SPEED - MIN_SPEED);
        double phase = frac(a, 32) * Math.PI * 2;
        int direction = ((b >>> 7) & 1) == 0 ? 1 : -1;
        double height = frac(b, 16) * ALTITUDE_SPREAD;
        double tilt = frac(b, 32) * Math.PI * 2;
        return new Params(radius, speed, phase, direction, height, tilt);
    }

    /** Angle around the centre (radians) at {@code time} ticks of overworld game time. */
    public static double angle(Params p, long launchTime, double time) {
        return p.phase() + p.direction() * p.speed() * (time - launchTime) / p.radius();
    }

    /** Position at {@code time} for a satellite circling over (cx, cz) at the base {@code altitude}. */
    public static Vec3 position(UUID id, long launchTime, double cx, double cz, double altitude, double time) {
        Params p = params(id);
        double a = angle(p, launchTime, time);
        double y = altitude + p.heightOffset() + TILT * Math.sin(a + p.tiltPhase());
        return new Vec3(cx + Math.cos(a) * p.radius(), y, cz + Math.sin(a) * p.radius());
    }

    /** Velocity (blocks per tick) at {@code time}. */
    public static Vec3 velocity(UUID id, long launchTime, double time) {
        Params p = params(id);
        double a = angle(p, launchTime, time);
        double w = p.direction() * p.speed() / p.radius(); // radians per tick
        return new Vec3(-Math.sin(a) * p.radius() * w, TILT * Math.cos(a + p.tiltPhase()) * w, Math.cos(a) * p.radius() * w);
    }

    /** Yaw (Minecraft degrees) the satellite faces along its track. */
    public static float yaw(UUID id, long launchTime, double time) {
        Vec3 v = velocity(id, launchTime, time);
        return (float) Math.toDegrees(Math.atan2(-v.x, v.z));
    }

    /** The spot a satellite circles over: its launch pad, or the world spawn. */
    public static BlockPos center(MinecraftServer server, Satellite satellite) {
        BlockPos site = satellite.site();
        return site != null ? site : server.overworld().getRespawnData().pos();
    }

    /** Position of a registered satellite now (server side). */
    public static Vec3 position(MinecraftServer server, Satellite satellite, double time) {
        BlockPos c = center(server, satellite);
        return position(satellite.id(), satellite.launchTime(), c.getX() + 0.5, c.getZ() + 0.5, SatelliteConfig.altitude(), time);
    }
}
