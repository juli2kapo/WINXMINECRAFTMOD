package net.juli2kapo.factoryascent.space.gravity;

import java.util.EnumSet;
import java.util.Set;
import net.juli2kapo.factoryascent.space.station.StationContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Turned gravity (Magnetic Boots): the frame maths, the rules, and the turned body in a real world. */
public final class GravityGameTests {
    private GravityGameTests() {}

    private static boolean near(Vec3 a, Vec3 b) {
        return a.distanceToSqr(b) < 1.0E-8;
    }

    private static Vec3 normal(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /** Frame transforms: exact round trips, local down is the gravity, boxes and eyes turn with it, the yaw carries over. */
    public static void frameMath(GameTestHelper h) {
        Vec3 v = new Vec3(0.3, -1.7, 2.9);
        for (Direction g : Direction.values()) {
            h.assertTrue(near(GravityFrame.toLocal(g, GravityFrame.toWorld(g, v)), v), g + ": local → world → local is exact");
            h.assertTrue(near(GravityFrame.toWorld(g, 0, -1, 0), normal(g)), g + ": local down is the gravity");
            h.assertTrue(near(GravityFrame.up(g), normal(g.getOpposite())), g + ": local up is against it");
            h.assertTrue(GravityFrame.toWorld(g, Direction.DOWN) == g && GravityFrame.toLocal(g, g) == Direction.DOWN,
                    g + ": directions map like vectors");
            Vector3f q = GravityFrame.quaternion(g).transform(new Vector3f(0, -1, 0));
            h.assertTrue(near(new Vec3(q.x, q.y, q.z), normal(g)), g + ": the camera/model quaternion agrees with the matrix");
            Vec3 feet = new Vec3(10, 64, -5);
            AABB box = GravityFrame.box(g, feet, 0.6, 1.8);
            h.assertTrue(Math.abs(box.max(g.getAxis()) - box.min(g.getAxis()) - 1.8) < 1.0E-9
                    && Math.abs(box.getXsize() + box.getYsize() + box.getZsize() - 3.0) < 1.0E-9, g + ": the box is 1.8 long along gravity");
            h.assertTrue(Math.abs(GravityFrame.face(box, g) - feet.get(g.getAxis())) < 1.0E-9, g + ": the soles lie in the feet's plane");
            h.assertTrue(near(GravityFrame.eye(g, feet, 1.62), feet.add(normal(g.getOpposite()).scale(1.62))), g + ": eyes along local up");
            for (Direction to : Direction.values()) {
                for (float yaw : new float[] {0, 37, -120, 179}) {
                    float shift = GravityFrame.yawShift(g, to, yaw);
                    Vec3 fwdOld = GravityFrame.toWorld(g, GravityFrame.forward(yaw));
                    Vec3 carried = GravityFrame.rotate(GravityFrame.turn(g, to, fwdOld), fwdOld);
                    Vec3 fwdNew = GravityFrame.toWorld(to, GravityFrame.forward(yaw + shift));
                    h.assertTrue(carried.distanceToSqr(fwdNew) < 1.0E-4, g + "→" + to + " yaw " + yaw + ": you keep facing the way you faced");
                }
            }
        }
        h.assertTrue(Math.abs(GravityFrame.yawShift(Direction.DOWN, Direction.EAST, 20)) < 1.0E-3, "floor → wall: the shortest turn needs no yaw change");
        // walking into a wall turns "towards the wall" into "up the wall"
        Vec3 fwd = GravityFrame.toWorld(Direction.EAST, GravityFrame.forward(-90));
        h.assertTrue(near(fwd, new Vec3(0, 1, 0)), "facing the east wall you face up it once standing on it, got " + fwd);
        h.succeed();
    }

    /** Which surface the boots take, and when they let go. */
    public static void rules(GameTestHelper h) {
        Set<Direction> east = EnumSet.of(Direction.EAST);
        Vec3 intoEast = new Vec3(1, 0, 0), along = new Vec3(0, 0, 1);
        h.assertTrue(Gravity.pick(Direction.DOWN, east, false, true, false, intoEast, false) == Direction.EAST, "walk into a wall: it becomes the floor");
        h.assertTrue(Gravity.pick(Direction.DOWN, east, false, true, false, along, false) == Direction.DOWN, "walking along a wall doesn't climb it");
        h.assertTrue(Gravity.pick(Direction.DOWN, east, false, true, false, Vec3.ZERO, false) == Direction.DOWN, "standing at a wall doesn't climb it");
        h.assertTrue(Gravity.pick(Direction.DOWN, east, false, true, false, intoEast, true) == Direction.DOWN, "sneaking: the magnets stay off");
        h.assertTrue(Gravity.pick(Direction.DOWN, Set.of(), true, false, true, Vec3.ZERO, false) == Direction.UP, "jump into the ceiling: hang from it");
        h.assertTrue(Gravity.pick(Direction.DOWN, Set.of(), true, true, true, Vec3.ZERO, false) == Direction.DOWN, "on the ground nothing flips");
        h.assertTrue(Gravity.pick(Direction.EAST, EnumSet.of(Direction.UP), false, true, false, new Vec3(0, 1, 0), false) == Direction.UP,
                "walk up a wall into the ceiling: the ceiling becomes the floor");
        h.assertTrue(Gravity.pick(Direction.EAST, EnumSet.of(Direction.DOWN), false, true, false, new Vec3(0, -1, 0), false) == Direction.DOWN,
                "walk down a wall onto the floor: back to normal");
        h.assertTrue(Gravity.pick(Direction.EAST, EnumSet.of(Direction.WEST), false, true, false, new Vec3(-1, 0, 0), false) == Direction.EAST,
                "the surface you stand on (or its opposite) is never a wall");
        h.assertTrue(!Gravity.mustReset(true, true, 0.25, false, false, false, false, false, false, false, false), "boots in orbit: allowed");
        h.assertTrue(Gravity.mustReset(true, false, 0.25, false, false, false, false, false, false, false, false), "boots off: reset");
        h.assertTrue(Gravity.mustReset(true, true, 1.0, false, false, false, false, false, false, false, false), "normal gravity: reset");
        h.assertTrue(Gravity.mustReset(false, true, 0.25, false, false, false, false, false, false, false, false), "config off: reset");
        h.assertTrue(Gravity.mustReset(true, true, 0.25, true, false, false, false, false, false, false, false), "flying: reset");
        h.assertTrue(Gravity.mustReset(true, true, 0.25, false, true, false, false, false, false, false, false), "riding: reset");
        h.assertTrue(Gravity.mustReset(true, true, 0.25, false, false, true, false, false, false, false, false), "swimming: reset");
        h.assertTrue(Gravity.mustReset(true, true, 0.25, false, false, false, true, false, false, false, false), "gliding: reset");
        h.assertTrue(Gravity.mustReset(true, true, 0.25, false, false, false, false, true, false, false, false), "sleeping: reset");
        h.assertTrue(Gravity.mustReset(true, true, 0.25, false, false, false, false, false, true, false, false), "in water: reset");
        h.assertTrue(Gravity.mustReset(true, true, 0.25, false, false, false, false, false, false, true, false), "spectating: reset");
        h.assertTrue(Gravity.detach(Direction.EAST, true, 0, Gravity.LOST_TICKS), "sneaking lets go");
        h.assertTrue(Gravity.detach(Direction.EAST, false, Gravity.LOST_TICKS + 1, Gravity.LOST_TICKS), "no surface for a while: let go");
        h.assertTrue(!Gravity.detach(Direction.EAST, false, 3, Gravity.LOST_TICKS), "a short hop off the wall holds on");
        h.assertTrue(!Gravity.detach(Direction.DOWN, true, 99, Gravity.LOST_TICKS), "nothing to let go of on the floor");
        h.succeed();
    }

    /**
     * A real (mock) player against a real wall: stepping onto it, the turned box, collisions and
     * on-ground along the wall, walking up it, the eyes and the look vector, and letting go.
     */
    public static void turnedBody(GameTestHelper h) {
        // a wall 4 high and 5 wide at x = 6 (relative), on a floor
        for (int x = 0; x <= 6; x++) for (int z = 1; z <= 7; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        for (int y = 1; y <= 5; y++) for (int z = 1; z <= 7; z++) h.setBlock(new BlockPos(6, y, z), Blocks.STONE);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(StationContent.MAGNETIC_BOOTS.get()));
        double wallX = h.absolutePos(new BlockPos(6, 1, 4)).getX();
        BlockPos floorCell = h.absolutePos(new BlockPos(5, 1, 4));
        // standing on the floor, pressed against the wall
        p.snapTo(wallX - 0.3, floorCell.getY(), floorCell.getZ() + 0.5);
        p.setOnGround(true);
        h.assertTrue(Gravity.wallAt(h.getLevel(), p, Direction.EAST), "the wall is felt at the feet");
        h.assertTrue(!Gravity.wallAt(h.getLevel(), p, Direction.WEST), "no wall behind");
        h.assertTrue(!Gravity.allowedOnServer(p), "at normal gravity the server never lets the boots turn you");

        Gravity.Change c = Gravity.change(p, Direction.EAST, true);
        h.assertTrue(c != null && Gravity.of(p) == Direction.EAST, "stepping onto the wall turns gravity east");
        AABB box = p.getBoundingBox();
        h.assertTrue(Math.abs(box.maxX - wallX) < 1.0E-6 && Math.abs(box.getXsize() - 1.8) < 1.0E-6 && Math.abs(box.getYsize() - 0.6) < 1.0E-6,
                "the turned body lies along the wall's normal, soles on it: " + box);
        h.assertTrue(Math.abs(p.getEyePosition().y - c.oldEye().y) < 1.0E-6, "the eyes stay at the same height while turning");
        h.assertTrue(h.getLevel().noCollision(p, box.deflate(1.0E-7)), "the turned body is in free space");

        // gravity pulls into the wall: a fall "east" lands on it
        p.snapTo(wallX - 0.5, p.getY(), p.getZ());
        p.move(MoverType.SELF, new Vec3(1, 0, 0));
        h.assertTrue(Math.abs(p.getX() - wallX) < 1.0E-6, "falling east stops on the wall face, x=" + p.getX());
        h.assertTrue(p.onGround() && p.verticalCollisionBelow && !p.horizontalCollision, "and the wall is the ground");
        // walking up the wall (world up is a sideways direction for the boots)
        double y0 = p.getY();
        p.move(MoverType.SELF, new Vec3(0, 0.5, 0));
        h.assertTrue(Math.abs(p.getY() - y0 - 0.5) < 1.0E-6 && Math.abs(p.getX() - wallX) < 1.0E-6, "walk up the wall");
        h.assertTrue(!p.horizontalCollision, "nothing in the way along the wall");
        // the eyes and the look follow the frame
        h.assertTrue(near(p.getEyePosition(), p.position().add(-p.getEyeHeight(), 0, 0)), "eyes stick out from the wall");
        h.assertTrue(Math.abs(p.getEyeY() - p.getY()) < 1.0E-9, "eye height is sideways now");
        p.setXRot(90);
        h.assertTrue(p.getViewVector(1).distanceToSqr(new Vec3(1, 0, 0)) < 1.0E-6, "looking at your feet is looking at the wall");
        p.setXRot(0);
        // local fit tests: standing fits here, an upright body wouldn't
        EntityDimensions stand = p.getDimensions(Pose.STANDING);
        h.assertTrue(Gravity.fits(p, Direction.EAST, p.position(), stand), "the turned standing body fits");
        h.assertTrue(!Gravity.fits(p, Direction.DOWN, p.position(), stand), "an upright body would be inside the wall");
        h.assertTrue(Gravity.surfaceNear(h.getLevel(), p, 1.25), "the wall is under the feet");

        // letting go (boots off mid-wall): DOWN, somewhere free
        Gravity.Change back = Gravity.change(p, Direction.DOWN, false);
        h.assertTrue(back != null && Gravity.of(p) == Direction.DOWN, "letting go turns gravity back down");
        h.assertTrue(h.getLevel().noCollision(p, p.getBoundingBox().deflate(1.0E-7)) && Math.abs(p.getBoundingBox().getYsize() - 1.8) < 1.0E-6,
                "and the upright body is free");
        h.assertTrue(p.fallDistance == 0, "falls are forgiven when turning");
        h.succeed();
    }
}
