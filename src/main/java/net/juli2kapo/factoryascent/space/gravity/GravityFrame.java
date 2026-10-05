package net.juli2kapo.factoryascent.space.gravity;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * The maths of a turned "down": pure functions, no world access (unit-tested by {@link GravityGameTests}).
 *
 * <p>A player whose gravity points along {@code D} lives in a <i>local frame</i>: the same physics
 * as always (local -Y is down, yaw and pitch are measured in it, WASD moves in its XZ plane), turned
 * so that local down is {@code D} in the world. Because {@code D} is one of the six block faces the
 * turn is always a whole number of quarter turns, so every frame transform is a signed permutation
 * of the axes: exact (no rounding drift), and an axis-aligned box stays an axis-aligned box. That
 * is what lets the vanilla collision code (which only knows world-aligned boxes) keep working.
 */
public final class GravityFrame {
    private GravityFrame() {}

    /**
     * Local → world matrices (row-major, entries -1/0/1), the shortest turn from DOWN to each
     * direction (so for a wall, "towards the wall" in the old frame becomes local "forward up" the
     * wall, and walking into it carries you straight up it).
     */
    private static final int[][] MATRIX = new int[6][];

    static {
        MATRIX[Direction.DOWN.ordinal()] = new int[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
        // half turn about Z
        MATRIX[Direction.UP.ordinal()] = new int[] {-1, 0, 0, 0, -1, 0, 0, 0, 1};
        // quarter turn about Z: (x,y,z) -> (-y, x, z); local down -> +X
        MATRIX[Direction.EAST.ordinal()] = new int[] {0, -1, 0, 1, 0, 0, 0, 0, 1};
        // (x,y,z) -> (y, -x, z); local down -> -X
        MATRIX[Direction.WEST.ordinal()] = new int[] {0, 1, 0, -1, 0, 0, 0, 0, 1};
        // quarter turn about X: (x,y,z) -> (x, z, -y); local down -> +Z
        MATRIX[Direction.SOUTH.ordinal()] = new int[] {1, 0, 0, 0, 0, 1, 0, -1, 0};
        // (x,y,z) -> (x, -z, y); local down -> -Z
        MATRIX[Direction.NORTH.ordinal()] = new int[] {1, 0, 0, 0, 0, -1, 0, 1, 0};
    }

    private static int[] m(Direction gravity) {
        return MATRIX[(gravity == null ? Direction.DOWN : gravity).ordinal()];
    }

    /** A local-frame vector in world coordinates. */
    public static Vec3 toWorld(Direction gravity, Vec3 local) {
        return toWorld(gravity, local.x, local.y, local.z);
    }

    public static Vec3 toWorld(Direction gravity, double x, double y, double z) {
        if (gravity == null || gravity == Direction.DOWN) return new Vec3(x, y, z);
        int[] a = m(gravity);
        return new Vec3(a[0] * x + a[1] * y + a[2] * z, a[3] * x + a[4] * y + a[5] * z, a[6] * x + a[7] * y + a[8] * z);
    }

    /** A world vector in the local frame (the transpose: the matrices are rotations). */
    public static Vec3 toLocal(Direction gravity, Vec3 world) {
        if (gravity == null || gravity == Direction.DOWN) return world;
        int[] a = m(gravity);
        double x = world.x, y = world.y, z = world.z;
        return new Vec3(a[0] * x + a[3] * y + a[6] * z, a[1] * x + a[4] * y + a[7] * z, a[2] * x + a[5] * y + a[8] * z);
    }

    /** Local up (the way the head points) in the world. */
    public static Vec3 up(Direction gravity) {
        return toWorld(gravity, 0, 1, 0);
    }

    /** The world direction a local direction points to. */
    public static Direction toWorld(Direction gravity, Direction local) {
        Vec3 v = toWorld(gravity, local.getStepX(), local.getStepY(), local.getStepZ());
        return Direction.getApproximateNearest(v.x, v.y, v.z);
    }

    /** The local direction a world direction is in this frame. */
    public static Direction toLocal(Direction gravity, Direction world) {
        Vec3 v = toLocal(gravity, new Vec3(world.getStepX(), world.getStepY(), world.getStepZ()));
        return Direction.getApproximateNearest(v.x, v.y, v.z);
    }

    /**
     * The collision box of a body {@code width} wide and {@code height} tall standing with its feet
     * at {@code feet}: the usual box (feet at the bottom centre) turned into the world.
     */
    public static AABB box(Direction gravity, Vec3 feet, double width, double height) {
        double w = width / 2.0;
        Vec3 a = toWorld(gravity, -w, 0, -w), b = toWorld(gravity, w, height, w);
        return new AABB(feet.x + a.x, feet.y + a.y, feet.z + a.z, feet.x + b.x, feet.y + b.y, feet.z + b.z);
    }

    /** Where the eyes are: {@code eyeHeight} along local up from the feet. */
    public static Vec3 eye(Direction gravity, Vec3 feet, double eyeHeight) {
        return feet.add(toWorld(gravity, 0, eyeHeight, 0));
    }

    /** How far the box reaches out along a world direction from the given feet (the face coordinate). */
    public static double face(AABB box, Direction side) {
        return side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(side.getAxis()) : box.min(side.getAxis());
    }

    /** The rotation of the local frame (local → world) as a quaternion, for the camera and the model. */
    public static Quaternionf quaternion(Direction gravity) {
        int[] a = m(gravity);
        // JOML matrices are column-major: m(col,row)
        Matrix3f mat = new Matrix3f(a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]);
        return mat.getNormalizedRotation(new Quaternionf());
    }

    /**
     * The world-space turn that carries a body from one gravity to the next the short way: a
     * quarter turn about the axis through both for neighbouring faces, and for a flip to the
     * opposite face a half turn about {@code forward} (a world vector across the old gravity:
     * the player rolls over onto the ceiling instead of somersaulting).
     */
    public static Quaternionf turn(Direction from, Direction to, Vec3 forward) {
        if (from == to) return new Quaternionf();
        Vec3 f = new Vec3(from.getStepX(), from.getStepY(), from.getStepZ());
        Vec3 t = new Vec3(to.getStepX(), to.getStepY(), to.getStepZ());
        if (from == to.getOpposite()) {
            Direction axis = Direction.getApproximateNearest(forward.x, forward.y, forward.z);
            if (axis.getAxis() == from.getAxis()) axis = from.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
            return new Quaternionf().rotationAxis(Mth.PI, axis.getStepX(), axis.getStepY(), axis.getStepZ());
        }
        Vec3 axis = f.cross(t);
        return new Quaternionf().rotationAxis(Mth.HALF_PI, (float) axis.x, (float) axis.y, (float) axis.z);
    }

    /** A world vector turned by a quaternion. */
    public static Vec3 rotate(Quaternionf q, Vec3 v) {
        org.joml.Vector3f r = q.transform(new org.joml.Vector3f((float) v.x, (float) v.y, (float) v.z));
        return new Vec3(r.x, r.y, r.z);
    }

    /** The local horizontal forward vector of a yaw (degrees), as Minecraft defines it. */
    public static Vec3 forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    /** The yaw (degrees) of a local horizontal vector. */
    public static float yawOf(Vec3 local) {
        return (float) Math.toDegrees(Math.atan2(-local.x, local.z));
    }

    /**
     * How much the yaw changes (a multiple of 90°) when the frame turns from {@code from} to {@code to}, so the player
     * keeps facing the same way: the old forward is carried by {@link #turn} into the new frame.
     */
    public static float yawShift(Direction from, Direction to, float yaw) {
        if (from == to) return 0;
        Vec3 fwdWorld = toWorld(from, forward(yaw));
        Vec3 carried = rotate(turn(from, to, fwdWorld), fwdWorld);
        Vec3 local = toLocal(to, carried);
        // every frame and turn is a whole number of quarter turns, so is the shift: snap it (no drift)
        return Mth.wrapDegrees(Math.round(Mth.wrapDegrees(yawOf(local) - yaw) / 90.0F) * 90.0F);
    }
}
