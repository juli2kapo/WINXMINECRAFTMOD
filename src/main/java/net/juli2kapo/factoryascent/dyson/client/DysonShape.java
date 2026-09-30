package net.juli2kapo.factoryascent.dyson.client;

/**
 * The geometry of a team's Dyson swarm, shared by the sky, the Dyson Monitor's hologram and its
 * screen preview. Units: the sun's visible half-size is 1 and the sun sits at the origin; in the
 * sky +Y points away from the viewer (so {@code y < 0} is in front of the sun).
 *
 * <p>How it grows with completion {@code c = collectors / target}:
 * <ul>
 *   <li>collectors orbit on up to eight inclined rings (a new ring opens as the swarm grows), one
 *       glinting dot per collector (thinned out past {@code maxDots});</li>
 *   <li>from 35% a lattice of struts fades in around the sun;</li>
 *   <li>from 60% shell panels fill the lattice cell by cell, with glowing seams between them and
 *       the star's light shining through the gaps; at 100% the shell is closed.</li>
 * </ul>
 * Everything is a pure function of the collector count, the target and the time, so every client
 * draws the same sphere.
 */
public final class DysonShape {
    /** Shell radius, in sun half-sizes. */
    public static final float SHELL = 1.6f;
    public static final int MAX_RINGS = 8;
    public static final int LAT = 12, LON = 24;
    private static final float[] INCLINATION = {18, 62, -38, 82, 31, -71, 47, -9};
    private static final float GOLDEN = 2.3999632f;

    /** What a consumer draws. */
    public interface Sink {
        /** One collector (or several, past {@code maxDots}); {@code phase} ∈ [0, 1) desynchronises its glint. */
        void collector(float x, float y, float z, float phase);

        /** A shell panel: four corners, {@code c[0..11]} = x0 y0 z0 … x3 y3 z3; {@code shade} ∈ [0, 1] varies its tint. */
        void panel(float[] c, float shade);

        /** A straight segment: {@code kind} {@link #TRACK} (a ring's orbit), {@link #STRUT} (lattice) or {@link #SEAM} (between panels). */
        void line(float ax, float ay, float az, float bx, float by, float bz, int kind, float alpha);
    }

    public static final int TRACK = 0, STRUT = 1, SEAM = 2;

    /** How far along the sphere is. */
    public record Stage(float completion, int rings, float lattice, float shell) {}

    private DysonShape() {}

    public static Stage stage(long collectors, int target) {
        float c = target <= 0 ? 0 : (float) Math.min(1.0, collectors / (double) target);
        int rings = collectors <= 0 ? 0 : ringsAt(collectors, target);
        float lattice = smooth((c - 0.35f) / 0.30f);
        float shell = clamp((c - 0.6f) / 0.4f);
        if (collectors >= target && target > 0) shell = 1f;
        return new Stage(c, rings, lattice, shell);
    }

    /** Rings open as the swarm grows: 1 at the start, 8 at the end. */
    private static int ringsAt(long collectors, int target) {
        double c = Math.min(1.0, collectors / (double) Math.max(1, target));
        return Math.min(MAX_RINGS, 1 + (int) Math.floor(7.0 * Math.sqrt(c) + 1e-6));
    }

    static float ringRadius(int ring) {
        return 2.0f + 0.2f * ring;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static float smooth(float v) {
        v = clamp(v);
        return v * v * (3 - 2 * v);
    }

    /** A stable pseudo-random value in [0, 1) for an integer. */
    static float hash(long n) {
        long h = n * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        h ^= (h >>> 31);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 29);
        return (h >>> 40) / (float) (1L << 24);
    }

    /**
     * Builds the swarm into {@code sink}.
     *
     * @param time     seconds (animation)
     * @param maxDots  most collectors drawn one by one
     * @param tracks   whether to draw the rings' orbits
     * @param segments segments per circle for lattice and tracks (detail)
     */
    public static void build(long collectors, int target, float time, Sink sink, int maxDots, boolean tracks, int segments) {
        if (collectors <= 0) return;
        Stage stage = stage(collectors, target);
        // ---- rings of collectors
        float[] u = new float[3], v = new float[3];
        if (tracks) {
            for (int r = 0; r < stage.rings(); r++) {
                ringBasis(r, u, v);
                float rad = ringRadius(r);
                for (int s = 0; s < segments; s++) {
                    float a0 = (float) (2 * Math.PI * s / segments), a1 = (float) (2 * Math.PI * (s + 1) / segments);
                    float c0 = (float) Math.cos(a0) * rad, s0 = (float) Math.sin(a0) * rad;
                    float c1 = (float) Math.cos(a1) * rad, s1 = (float) Math.sin(a1) * rad;
                    sink.line(u[0] * c0 + v[0] * s0, u[1] * c0 + v[1] * s0, u[2] * c0 + v[2] * s0,
                            u[0] * c1 + v[0] * s1, u[1] * c1 + v[1] * s1, u[2] * c1 + v[2] * s1, TRACK, 1f);
                }
            }
        }
        long dots = Math.min(collectors, Math.max(1, maxDots));
        int lastRing = -1;
        for (long d = 0; d < dots; d++) {
            long j = dots == collectors ? d : d * collectors / dots;
            int k = ringsAt(j + 1, target);
            int ring = (int) (hash(j * 31 + 7) * k);
            if (ring != lastRing) {
                ringBasis(ring, u, v);
                lastRing = ring;
            }
            float rad = ringRadius(ring) * (1f + 0.05f * (hash(j * 17 + 3) - 0.5f));
            float speed = 0.35f / (float) Math.pow(ringRadius(ring), 1.5);
            float a = d * GOLDEN + ring * 1.3f + time * speed;
            float ca = (float) Math.cos(a) * rad, sa = (float) Math.sin(a) * rad;
            // A little out-of-plane scatter makes a swarm rather than a wire.
            float off = 0.07f * (hash(j * 13 + 11) - 0.5f);
            float nx = u[1] * v[2] - u[2] * v[1], ny = u[2] * v[0] - u[0] * v[2], nz = u[0] * v[1] - u[1] * v[0];
            sink.collector(u[0] * ca + v[0] * sa + nx * off, u[1] * ca + v[1] * sa + ny * off, u[2] * ca + v[2] * sa + nz * off,
                    hash(j * 7 + 1));
        }
        // ---- the shell: a slowly turning sphere frame
        if (stage.lattice() <= 0 && stage.shell() <= 0) return;
        float spin = time * 0.015f;
        float tilt = 0.45f;
        float[] p = new float[3];
        // lattice struts: meridians every 30°, parallels every 30°
        if (stage.lattice() > 0) {
            for (int m = 0; m < 12; m++) {
                float lon = (float) Math.toRadians(m * 30);
                for (int s = 0; s < segments / 2; s++) {
                    float lat0 = (float) (-Math.PI / 2 + Math.PI * s / (segments / 2)), lat1 = (float) (-Math.PI / 2 + Math.PI * (s + 1) / (segments / 2));
                    line(sink, lat0, lon, lat1, lon, spin, tilt, STRUT, stage.lattice(), p);
                }
            }
            for (int pl = -2; pl <= 2; pl++) {
                float lat = (float) Math.toRadians(pl * 30);
                for (int s = 0; s < segments; s++) {
                    float lon0 = (float) (2 * Math.PI * s / segments), lon1 = (float) (2 * Math.PI * (s + 1) / segments);
                    line(sink, lat, lon0, lat, lon1, spin, tilt, STRUT, stage.lattice(), p);
                }
            }
        }
        if (stage.shell() <= 0) return;
        int cells = LAT * LON;
        int filled = Math.round(stage.shell() * cells);
        float[] c = new float[12];
        float inset = 0.07f;
        for (int i = 0; i < cells; i++) {
            if (order(i) >= filled) continue;
            int a = i / LON, b = i % LON;
            float lat0 = (float) (-Math.PI / 2 + Math.PI * (a + inset) / LAT), lat1 = (float) (-Math.PI / 2 + Math.PI * (a + 1 - inset) / LAT);
            float lon0 = (float) (2 * Math.PI * (b + inset) / LON), lon1 = (float) (2 * Math.PI * (b + 1 - inset) / LON);
            point(lat0, lon0, spin, tilt, p);
            c[0] = p[0]; c[1] = p[1]; c[2] = p[2];
            point(lat0, lon1, spin, tilt, p);
            c[3] = p[0]; c[4] = p[1]; c[5] = p[2];
            point(lat1, lon1, spin, tilt, p);
            c[6] = p[0]; c[7] = p[1]; c[8] = p[2];
            point(lat1, lon0, spin, tilt, p);
            c[9] = p[0]; c[10] = p[1]; c[11] = p[2];
            sink.panel(c, hash(i * 5L + 2));
        }
        // glowing seams along the edges of filled cells
        for (int i = 0; i < cells; i++) {
            if (order(i) >= filled) continue;
            int a = i / LON, b = i % LON;
            float lat0 = (float) (-Math.PI / 2 + Math.PI * a / LAT), lat1 = (float) (-Math.PI / 2 + Math.PI * (a + 1) / LAT);
            float lon0 = (float) (2 * Math.PI * b / LON), lon1 = (float) (2 * Math.PI * (b + 1) / LON);
            line(sink, lat0, lon0, lat0, lon1, spin, tilt, SEAM, stage.shell(), p);
            line(sink, lat0, lon0, lat1, lon0, spin, tilt, SEAM, stage.shell(), p);
        }
    }

    /** The order in which shell cells get filled: a fixed shuffle. */
    private static final int[] ORDER = new int[LAT * LON];

    static {
        Integer[] idx = new Integer[LAT * LON];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (x, y) -> Float.compare(hash(x * 97L + 5), hash(y * 97L + 5)));
        for (int rank = 0; rank < idx.length; rank++) ORDER[idx[rank]] = rank;
    }

    private static int order(int cell) {
        return ORDER[cell];
    }

    /** The plane of ring {@code r}: two unit vectors u, v. */
    static void ringBasis(int r, float[] u, float[] v) {
        double node = Math.toRadians(r * 47 + 10);
        double inc = Math.toRadians(INCLINATION[r % INCLINATION.length]);
        u[0] = (float) Math.cos(node);
        u[1] = 0;
        u[2] = (float) Math.sin(node);
        float cx = (float) -Math.sin(node), cz = (float) Math.cos(node);
        v[0] = (float) (cx * Math.cos(inc));
        v[1] = (float) Math.sin(inc);
        v[2] = (float) (cz * Math.cos(inc));
    }

    /** A point of the shell at latitude/longitude, turned by the frame's spin and tilt. */
    private static void point(float lat, float lon, float spin, float tilt, float[] out) {
        double cl = Math.cos(lat);
        double x = cl * Math.cos(lon + spin), y = cl * Math.sin(lon + spin), z = Math.sin(lat);
        // tilt the pole (z) towards the viewer's depth axis (y) so the latitude lines read as ellipses
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        double y2 = y * ct - z * st, z2 = y * st + z * ct;
        out[0] = (float) x * SHELL;
        out[1] = (float) y2 * SHELL;
        out[2] = (float) z2 * SHELL;
    }

    private static void line(Sink sink, float lat0, float lon0, float lat1, float lon1, float spin, float tilt, int kind,
                             float alpha, float[] p) {
        point(lat0, lon0, spin, tilt, p);
        float ax = p[0], ay = p[1], az = p[2];
        point(lat1, lon1, spin, tilt, p);
        sink.line(ax, ay, az, p[0], p[1], p[2], kind, alpha);
    }
}
