package net.juli2kapo.factoryascent.ships;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Game test bodies for riding ships (the rider's view), listed in {@code ModGameTests.TESTS}. */
public final class ShipRiderGameTests {
    private ShipRiderGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String message) {
        if (!ok) h.fail(message);
    }

    /**
     * Boarding faces the bow (at most 20° down) once; after that the view is the rider's own, even
     * when the rider is re-mounted (what a client does every tick while a shuttle refuses a Shift
     * dismount in flight, since the server re-sends the passenger list each time).
     */
    public static void riderViewKept(GameTestHelper h) {
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) for (int y = 1; y < 6; y++) h.setBlock(x, y, z, Blocks.AIR);
        Shuttle ship = h.spawn(ShipContent.SHUTTLE.get(), new Vec3(4.5, 1, 4.5));
        ship.setYRot(30f);
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.snapTo(ship.getX() + 2, ship.getY(), ship.getZ(), 120f, 60f);
        ship.interact(p, InteractionHand.MAIN_HAND, ship.position());
        check(h, p.getVehicle() == ship, "using the shuttle must board it");
        check(h, Math.abs(Mth.wrapDegrees(p.getYRot() - 30f)) < 0.01f, "boarding must face the bow, yaw " + p.getYRot());
        check(h, Math.abs(p.getXRot() - 20f) < 0.01f, "boarding must look at most 20 degrees down, pitch " + p.getXRot());

        // in flight Shift is "descend": the dismount is refused and the rider stays aboard
        ship.setState(Shuttle.STATE_FLYING);
        p.setShiftKeyDown(true);
        p.stopRiding();
        check(h, p.getVehicle() == ship, "Shift in flight must not leave the shuttle");
        p.setShiftKeyDown(false);

        // looking around, then a re-mount (a re-sent passenger list): the view must stay the rider's
        p.setYRot(-100f);
        p.setYHeadRot(-100f);
        p.setXRot(75f);
        p.stopRiding();
        p.startRiding(ship, true, false);
        check(h, p.getVehicle() == ship, "re-mounting must seat the rider again");
        check(h, Math.abs(p.getYRot() + 100f) < 0.01f, "a re-mount must not turn the view to the bow, yaw " + p.getYRot());
        check(h, Math.abs(p.getXRot() - 75f) < 0.01f, "a re-mount must not clamp the pitch, pitch " + p.getXRot());
        p.stopRiding();
        ship.discard();
        h.succeed();
    }
}
