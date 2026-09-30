package net.juli2kapo.factoryascent.orbital;

import java.util.UUID;

/**
 * Where a satellite appears in the sky, as a pure function of its id and the time, the same on
 * every client: each satellite follows its own straight ground track (heading, sideways offset,
 * height and period all derived from its id) and passes over the observer once per period, rising
 * over one horizon and setting behind the other. Positions are relative to the observer, so a
 * satellite can be seen but never reached.
 */
public final class SatelliteTrack {
    /** Half the length of a pass, in blocks: from here to either horizon. */
    public static final double HALF_PASS = 420;

    private SatelliteTrack() {}

    /** Heading of the track in radians. */
    public static double heading(UUID id) {
        return ((id.getMostSignificantBits() >>> 8) & 0xFFFF) / 65536.0 * Math.PI * 2;
    }

    /** Ticks per pass: two to four minutes. */
    public static int period(UUID id) {
        return 2400 + (int) Math.floorMod(id.getMostSignificantBits() >>> 24, 2400L);
    }

    /**
     * Offset (x, y, z) of the satellite from the observer at {@code time} ticks, with the track
     * {@code minHeight} to {@code minHeight + heightSpread} blocks up.
     */
    public static double[] offset(UUID id, double time, double minHeight, double heightSpread) {
        long b = id.getLeastSignificantBits();
        double phase = (b & 0xFFFF) / 65536.0;
        double cross = (((b >>> 16) & 0xFFFF) / 65536.0 - 0.5) * 2 * 140;
        double height = minHeight + ((b >>> 32) & 0xFF) / 255.0 * heightSpread;
        double t = time / period(id) + phase;
        double along = ((t - Math.floor(t)) * 2 - 1) * HALF_PASS;
        double h = heading(id);
        double sin = Math.sin(h), cos = Math.cos(h);
        return new double[] {along * cos - cross * sin, height, along * sin + cross * cos};
    }

    /** Slow tumble of the satellite's body (radians) so passes don't all look the same. */
    public static double spin(UUID id, double time) {
        return time * 0.002 * (1 + (id.getLeastSignificantBits() >>> 40 & 0x7)) + heading(id);
    }
}
