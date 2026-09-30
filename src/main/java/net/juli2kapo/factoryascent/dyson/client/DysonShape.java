package net.juli2kapo.factoryascent.dyson.client;

/**
 * The geometry of a team's Dyson <em>Cube</em> (it's Minecraft: the sun is square), shared by the
 * sky, the Dyson Monitor's hologram and its screen preview. Units: the sun's visible half-size is 1
 * and the sun sits at the origin; in the sky +Y points away from the viewer (so {@code y < 0} is in
 * front of the sun).
 *
 * <p>How it grows with completion {@code c = collectors / target}:
 * <ul>
 *   <li>collectors travel along up to eight square "orbits" around the square sun (the first one
 *       face-on and axis-aligned, later ones tilted and turned), one glinting dot per collector
 *       (thinned out past {@code maxDots});</li>
 *   <li>from 35% a cube lattice fades in: its twelve edges and a grid on every face;</li>
 *   <li>from 60% the cube's faces fill cell by cell with dark panels, glowing seams between them and
 *       the star's light shining through the gaps; at 100% the cube is closed.</li>
 * </ul>
 * Everything is a pure function of the collector count, the target and the time, so every client
 * draws the same cube.
 */
public final class DysonShape {
    /** Cube half-size, in sun half-sizes. */
    public static final float SHELL = 1.55f;
    public static final int MAX_RINGS = 8;
    /** Panels per cube edge (each face is GRID × GRID cells). */
    public static final int GRID = 8;
    private static final float[] INCLINATION = {0, 50, -35, 72, 22, -62, 40, -14};
    private static final float[] NODE = {0, 45, 100, 20, 150, 70, 125, 10};
    private static final float GOLDEN = 0.6180340f;
    /** The cube's fixed tilt, so it reads as a block (three faces) from the ground. */
    private static final float TILT_X = 0.42f, TILT_Z = 0.62f;

    /** What a consumer draws. */
    public interface Sink {
        /** One collector (or several, past {@code maxDots}); {@code phase} ∈ [0, 1) desynchronises its glint. */
        void collector(float x, float y, float z, float phase);

        /** A cube panel: four corners, {@code c[0..11]} = x0 y0 z0 … x3 y3 z3; {@code shade} ∈ [0, 1] varies its tint. */
        void panel(float[] c, float shade);

        /** A straight segment: {@code kind} {@link #TRACK} (a square orbit), {@link #STRUT} (lattice) or {@link #SEAM} (between panels). */
        void line(float ax, float ay, float az, float bx, float by, float bz, int kind, float alpha);
    }

    public static final int TRACK = 0, STRUT = 1, SEAM = 2;

    /** How far along the cube is. */
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

    /** Square orbits open as the swarm grows: 1 at the start, 8 at the end. */
    private static int ringsAt(long collectors, int target) {
        double c = Math.min(1.0, collectors / (double) Math.max(1, target));
        return Math.min(MAX_RINGS, 1 + (int) Math.floor(7.0 * Math.sqrt(c) + 1e-6));
    }

    /** Half-size of square orbit {@code ring}. */
    static float ringRadius(int ring) {
        return 1.95f + 0.2f * ring;
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

    /** A point on the perimeter of a square of half-size 1, {@code s} ∈ [0, 1) once around: (a, b). */
    private static void onSquare(float s, float[] out) {
        s = s - (float) Math.floor(s);
        float t = s * 4f;
        int side = Math.min(3, (int) t);
        float f = (t - side) * 2f - 1f; // -1..1 along the side
        switch (side) {
            case 0 -> { out[0] = f; out[1] = -1f; }
            case 1 -> { out[0] = 1f; out[1] = f; }
            case 2 -> { out[0] = -f; out[1] = 1f; }
            default -> { out[0] = -1f; out[1] = -f; }
        }
    }

    /**
     * Builds the cube into {@code sink}.
     *
     * @param time     seconds (animation)
     * @param maxDots  most collectors drawn one by one
     * @param tracks   whether to draw the square orbits
     * @param segments unused detail hint (kept for callers; the cube's lines are straight)
     */
    public static void build(long collectors, int target, float time, Sink sink, int maxDots, boolean tracks, int segments) {
        if (collectors <= 0) return;
        Stage stage = stage(collectors, target);
        float[] u = new float[3], v = new float[3], ab = new float[2];
        // ---- square orbits of collectors
        if (tracks) {
            for (int r = 0; r < stage.rings(); r++) {
                ringBasis(r, u, v);
                float h = ringRadius(r);
                float[][] sq = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (int k = 0; k < 4; k++) {
                    float a0 = sq[k][0] * h, b0 = sq[k][1] * h, a1 = sq[(k + 1) % 4][0] * h, b1 = sq[(k + 1) % 4][1] * h;
                    sink.line(u[0] * a0 + v[0] * b0, u[1] * a0 + v[1] * b0, u[2] * a0 + v[2] * b0,
                            u[0] * a1 + v[0] * b1, u[1] * a1 + v[1] * b1, u[2] * a1 + v[2] * b1, TRACK, 1f);
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
            float h = ringRadius(ring) * (1f + 0.04f * (hash(j * 17 + 3) - 0.5f));
            float speed = 0.02f / ringRadius(ring);
            onSquare(d * GOLDEN + ring * 0.13f + time * speed, ab);
            float a = ab[0] * h, b = ab[1] * h;
            float off = 0.06f * (hash(j * 13 + 11) - 0.5f);
            float nx = u[1] * v[2] - u[2] * v[1], ny = u[2] * v[0] - u[0] * v[2], nz = u[0] * v[1] - u[1] * v[0];
            sink.collector(u[0] * a + v[0] * b + nx * off, u[1] * a + v[1] * b + ny * off, u[2] * a + v[2] * b + nz * off,
                    hash(j * 7 + 1));
        }
        if (stage.lattice() <= 0 && stage.shell() <= 0) return;
        // ---- the cube: turning very slowly about its own vertical axis
        float spin = time * 0.01f;
        float[] p = new float[3], q = new float[3];
        if (stage.lattice() > 0) {
            // twelve edges (bright struts) and a face grid every two cells
            for (int f = 0; f < 6; f++) {
                for (int g = 0; g <= GRID; g += 2) {
                    float t = -1f + 2f * g / GRID;
                    boolean edge = g == 0 || g == GRID;
                    float alpha = stage.lattice() * (edge ? 1f : 0.6f);
                    facePoint(f, t, -1f, spin, p);
                    facePoint(f, t, 1f, spin, q);
                    sink.line(p[0], p[1], p[2], q[0], q[1], q[2], STRUT, alpha);
                    facePoint(f, -1f, t, spin, p);
                    facePoint(f, 1f, t, spin, q);
                    sink.line(p[0], p[1], p[2], q[0], q[1], q[2], STRUT, alpha);
                }
            }
        }
        if (stage.shell() <= 0) return;
        int cells = 6 * GRID * GRID;
        int filled = Math.round(stage.shell() * cells);
        float[] c = new float[12];
        float inset = 0.09f;
        for (int i = 0; i < cells; i++) {
            if (ORDER[i] >= filled) continue;
            int f = i / (GRID * GRID), a = (i / GRID) % GRID, b = i % GRID;
            float a0 = -1f + 2f * (a + inset) / GRID, a1 = -1f + 2f * (a + 1 - inset) / GRID;
            float b0 = -1f + 2f * (b + inset) / GRID, b1 = -1f + 2f * (b + 1 - inset) / GRID;
            corner(f, a0, b0, spin, c, 0, p);
            corner(f, a1, b0, spin, c, 3, p);
            corner(f, a1, b1, spin, c, 6, p);
            corner(f, a0, b1, spin, c, 9, p);
            sink.panel(c, hash(i * 5L + 2));
        }
        // glowing seams along the edges of filled cells
        for (int i = 0; i < cells; i++) {
            if (ORDER[i] >= filled) continue;
            int f = i / (GRID * GRID), a = (i / GRID) % GRID, b = i % GRID;
            float a0 = -1f + 2f * a / GRID, a1 = -1f + 2f * (a + 1) / GRID;
            float b0 = -1f + 2f * b / GRID, b1 = -1f + 2f * (b + 1) / GRID;
            facePoint(f, a0, b0, spin, p);
            facePoint(f, a1, b0, spin, q);
            sink.line(p[0], p[1], p[2], q[0], q[1], q[2], SEAM, stage.shell());
            facePoint(f, a0, b1, spin, q);
            sink.line(p[0], p[1], p[2], q[0], q[1], q[2], SEAM, stage.shell());
        }
    }

    private static void corner(int f, float a, float b, float spin, float[] c, int at, float[] p) {
        facePoint(f, a, b, spin, p);
        c[at] = p[0];
        c[at + 1] = p[1];
        c[at + 2] = p[2];
    }

    /** The order in which cube cells get filled: a fixed shuffle. */
    private static final int[] ORDER = new int[6 * GRID * GRID];

    static {
        Integer[] idx = new Integer[ORDER.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (x, y) -> Float.compare(hash(x * 97L + 5), hash(y * 97L + 5)));
        for (int rank = 0; rank < idx.length; rank++) ORDER[idx[rank]] = rank;
    }

    /** The plane of square orbit {@code r}: two unit vectors u, v (the square's sides). */
    static void ringBasis(int r, float[] u, float[] v) {
        double node = Math.toRadians(NODE[r % NODE.length]);
        double inc = Math.toRadians(INCLINATION[r % INCLINATION.length]);
        u[0] = (float) Math.cos(node);
        u[1] = 0;
        u[2] = (float) Math.sin(node);
        float cx = (float) -Math.sin(node), cz = (float) Math.cos(node);
        v[0] = (float) (cx * Math.cos(inc));
        v[1] = (float) Math.sin(inc);
        v[2] = (float) (cz * Math.cos(inc));
    }

    /**
     * A point on face {@code f} of the cube at face coordinates (a, b) ∈ [-1, 1]², turned by the
     * cube's tilt and spin. Faces: 0/1 = ±x, 2/3 = ±y (depth), 4/5 = ±z.
     */
    private static void facePoint(int f, float a, float b, float spin, float[] out) {
        float s = (f & 1) == 0 ? 1f : -1f;
        float x, y, z;
        switch (f >> 1) {
            case 0 -> { x = s; y = a; z = b; }
            case 1 -> { x = a; y = s; z = b; }
            default -> { x = a; y = b; z = s; }
        }
        // spin about the cube's own vertical (z, the sky's "up" in the sun frame)
        double cs = Math.cos(spin), ss = Math.sin(spin);
        double x1 = x * cs - y * ss, y1 = x * ss + y * cs, z1 = z;
        // tilt: about x (lean the top towards the viewer), then about the view's vertical axis
        double cx = Math.cos(TILT_X), sx = Math.sin(TILT_X);
        double y2 = y1 * cx - z1 * sx, z2 = y1 * sx + z1 * cx;
        double cz = Math.cos(TILT_Z), sz = Math.sin(TILT_Z);
        double x3 = x1 * cz - y2 * sz, y3 = x1 * sz + y2 * cz;
        out[0] = (float) x3 * SHELL;
        out[1] = (float) y3 * SHELL;
        out[2] = (float) z2 * SHELL;
    }
}
