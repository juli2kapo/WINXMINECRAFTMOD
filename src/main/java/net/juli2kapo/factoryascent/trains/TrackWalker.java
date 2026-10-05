package net.juli2kapo.factoryascent.trains;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Track geometry over vanilla (and any {@link BaseRailBlock}) rails, the way minecarts see it:
 * each rail block is a straight segment between two ends on its block's edges (curves are the
 * diagonal chord, slopes rise one block). Two rails are connected when an end of one lies on an
 * end of the other, so straights, curves, slopes, powered/detector/activator rails and junctions
 * (whatever shape the junction is switched to right now) all work the same.
 *
 * <p>{@link #walk} moves a point along the track by a distance, rail by rail, so a train never
 * skips a corner however fast it goes; it stops at the end of the track (or at a junction set
 * against it) and says so.
 */
public final class TrackWalker {
    private TrackWalker() {}

    /** Height of a minecart's feet above the rail block's floor on flat track. */
    public static final double RIDE = 0.0625;

    /** A point on the track: a rail block, its current shape, and the progress t (0 = end A, 1 = end B). */
    public record Spot(BlockPos rail, RailShape shape, double t) {
        public Vec3 a() {
            return end(rail, shape, true);
        }

        public Vec3 b() {
            return end(rail, shape, false);
        }

        public Vec3 pos() {
            Vec3 a = a(), b = b();
            return a.add(b.subtract(a).scale(t)).add(0, RIDE, 0);
        }

        /** Unit vector from A to B (3D, so it climbs on slopes). */
        public Vec3 tangent() {
            return b().subtract(a()).normalize();
        }

        public double length() {
            return b().distanceTo(a());
        }

        public Spot withT(double nt) {
            return new Spot(rail, shape, Mth.clamp(nt, 0, 1));
        }
    }

    /** Where a walk ended: the spot, whether it travels toward B there, and how far it got. */
    public record Result(Spot spot, boolean towardB, double travelled, boolean blocked) {
        /** Unit travel direction at the end of the walk. */
        public Vec3 direction() {
            Vec3 t = spot.tangent();
            return towardB ? t : t.scale(-1);
        }
    }

    public static boolean isRail(BlockState state) {
        return state.getBlock() instanceof BaseRailBlock;
    }

    public static @Nullable RailShape shapeAt(BlockGetter level, BlockPos pos, @Nullable AbstractMinecart cart) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BaseRailBlock rail)) return null;
        return rail.getRailDirection(state, level, pos, cart);
    }

    /** One end of a rail segment (on the floor of the block; the high end of a slope one block up). */
    public static Vec3 end(BlockPos p, RailShape shape, boolean first) {
        Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
        Vec3i e = first ? exits.getFirst() : exits.getSecond();
        double y = p.getY() + (shape.isSlope() && e.getY() == 0 ? 1 : 0);
        return new Vec3(p.getX() + 0.5 + e.getX() * 0.5, y, p.getZ() + 0.5 + e.getZ() * 0.5);
    }

    /** The track spot nearest to a position (the rail at its feet or just below), or null off the rails. */
    public static @Nullable Spot locate(BlockGetter level, Vec3 pos, @Nullable AbstractMinecart cart) {
        BlockPos feet = BlockPos.containing(pos.x, pos.y + 1e-4, pos.z);
        for (BlockPos p : new BlockPos[] {feet, feet.below(), BlockPos.containing(pos.x, pos.y - 0.6, pos.z)}) {
            RailShape shape = shapeAt(level, p, cart);
            if (shape != null) return project(p, shape, pos);
        }
        return null;
    }

    /** The point of a rail segment nearest to pos (horizontally). */
    public static Spot project(BlockPos rail, RailShape shape, Vec3 pos) {
        Vec3 a = end(rail, shape, true), b = end(rail, shape, false);
        double dx = b.x - a.x, dz = b.z - a.z;
        double len2 = dx * dx + dz * dz;
        double t = len2 < 1e-9 ? 0.5 : ((pos.x - a.x) * dx + (pos.z - a.z) * dz) / len2;
        return new Spot(rail, shape, Mth.clamp(t, 0, 1));
    }

    /** The same spot after re-reading its rail (a junction may have switched): null if the rail is gone. */
    public static @Nullable Spot refresh(BlockGetter level, Spot spot, Vec3 pos, @Nullable AbstractMinecart cart) {
        RailShape shape = shapeAt(level, spot.rail(), cart);
        if (shape == null) return locate(level, pos, cart);
        if (shape == spot.shape()) return spot;
        return project(spot.rail(), shape, pos);
    }

    /** Whether, at this spot, a horizontal direction points more toward end B than toward end A. */
    public static boolean pointsTowardB(Spot spot, Vec3 dir) {
        Vec3 t = spot.tangent();
        return t.x * dir.x + t.z * dir.z >= 0;
    }

    /** The rail connected to this spot's end (A when !atB), and which of its ends touches ours. */
    private record Link(BlockPos rail, RailShape shape, boolean enteredAtA) {}

    private static @Nullable Link next(BlockGetter level, Spot spot, boolean atB, @Nullable AbstractMinecart cart) {
        Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(spot.shape());
        Vec3i e = atB ? exits.getSecond() : exits.getFirst();
        Vec3 point = atB ? spot.b() : spot.a();
        BlockPos side = spot.rail().offset(e.getX(), 0, e.getZ());
        for (BlockPos p : new BlockPos[] {side, side.above(), side.below()}) {
            RailShape shape = shapeAt(level, p, cart);
            if (shape == null) continue;
            if (end(p, shape, true).distanceToSqr(point) < 1e-4) return new Link(p, shape, true);
            if (end(p, shape, false).distanceToSqr(point) < 1e-4) return new Link(p, shape, false);
        }
        return null;
    }

    /**
     * Walks {@code distance} (>= 0) along the track from a spot, heading toward end B or end A, and
     * returns where it got. Stops early (blocked) at the end of the track.
     */
    public static Result walk(BlockGetter level, Spot start, boolean towardB, double distance, @Nullable AbstractMinecart cart) {
        Spot cur = start;
        boolean dirB = towardB;
        double left = Math.max(0, distance);
        for (int guard = 0; guard < 256; guard++) {
            double len = Math.max(1e-6, cur.length());
            double room = (dirB ? 1 - cur.t() : cur.t()) * len;
            if (left <= room + 1e-9) {
                double nt = cur.t() + (dirB ? 1 : -1) * left / len;
                return new Result(cur.withT(nt), dirB, distance, false);
            }
            left -= room;
            Link link = next(level, cur, dirB, cart);
            Spot end = cur.withT(dirB ? 1 : 0);
            if (link == null) return new Result(end, dirB, distance - left, true);
            cur = new Spot(link.rail(), link.shape(), link.enteredAtA() ? 0 : 1);
            dirB = link.enteredAtA();
        }
        return new Result(cur, dirB, distance - left, true);
    }

    /** Horizontal yaw (Minecraft convention: 0 = south, 90 = west) of a direction. */
    public static float yaw(Vec3 dir) {
        return (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
    }

    /** Pitch of a direction in degrees (positive = climbing). */
    public static float climb(Vec3 dir) {
        double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        return (float) (Mth.atan2(dir.y, h) * Mth.RAD_TO_DEG);
    }
}
