package net.juli2kapo.factoryascent.satellites;

import java.util.UUID;
import net.juli2kapo.factoryascent.gear.GearContent;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.orbital.OrbitRegistry;
import net.juli2kapo.factoryascent.orbital.Satellite;
import net.juli2kapo.factoryascent.orbital.SatelliteType;
import net.juli2kapo.factoryascent.ships.ShipContent;
import net.juli2kapo.factoryascent.ships.Shuttle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Game test bodies for the satellites flying in Earth orbit, listed in {@code ModGameTests.TESTS}. */
public final class SatelliteGameTests {
    private SatelliteGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String message) {
        if (!ok) h.fail(message);
    }

    /**
     * The orbit math: the same for everyone at the same time, a circle of the satellite's radius
     * around its launch site, at the configured height band, moving at its speed, back where it
     * started after one period, and the velocity matches the motion.
     */
    public static void orbitMath(GameTestHelper h) {
        double cx = 1000.5, cz = -250.5, alt = 320;
        for (int i = 0; i < 64; i++) {
            UUID id = new UUID(0x1234_5678_9ABCL * (i + 1), 0x0FED_CBA9_8765L * (i + 7));
            long launch = 1000L * i;
            SatelliteOrbit.Params p = SatelliteOrbit.params(id);
            check(h, p.radius() >= SatelliteOrbit.MIN_RADIUS && p.radius() <= SatelliteOrbit.MAX_RADIUS, "radius out of range: " + p.radius());
            check(h, p.speed() >= SatelliteOrbit.MIN_SPEED && p.speed() <= SatelliteOrbit.MAX_SPEED, "speed out of range: " + p.speed());
            for (double t : new double[] {launch, launch + 137, launch + 98765.25}) {
                Vec3 a = SatelliteOrbit.position(id, launch, cx, cz, alt, t);
                check(h, a.equals(SatelliteOrbit.position(id, launch, cx, cz, alt, t)), "the orbit must be deterministic");
                double r = Math.hypot(a.x - cx, a.z - cz);
                check(h, Math.abs(r - p.radius()) < 1e-6, "must circle the launch site at its radius: " + r + " vs " + p.radius());
                check(h, a.y >= alt - SatelliteOrbit.TILT - 1e-6 && a.y <= alt + SatelliteOrbit.ALTITUDE_SPREAD + SatelliteOrbit.TILT + 1e-6,
                        "height out of band: " + a.y);
                check(h, a.y > 200, "satellites fly far above the stations (y=100) and shuttle arrivals (y=112)");
                Vec3 lap = SatelliteOrbit.position(id, launch, cx, cz, alt, t + p.period());
                check(h, lap.distanceTo(a) < 1e-6, "one period later it must be back: " + lap.distanceTo(a));
                Vec3 next = SatelliteOrbit.position(id, launch, cx, cz, alt, t + 1);
                double moved = Math.hypot(next.x - a.x, next.z - a.z);
                check(h, Math.abs(moved - p.speed()) < 0.01, "must cover its speed each tick: " + moved + " vs " + p.speed());
                Vec3 v = SatelliteOrbit.velocity(id, launch, t + 0.5);
                check(h, v.distanceTo(next.subtract(a)) < 0.01, "velocity must match the motion: " + v + " vs " + next.subtract(a));
            }
        }
        // different satellites, different orbits; the launch time sets where it starts
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        check(h, SatelliteOrbit.params(a).equals(SatelliteOrbit.params(a)), "params must be stable");
        check(h, !SatelliteOrbit.position(a, 0, 0, 0, alt, 500).equals(SatelliteOrbit.position(b, 0, 0, 0, alt, 500)),
                "two satellites must not share an orbit");
        check(h, SatelliteOrbit.position(a, 0, 0, 0, alt, 0).distanceTo(SatelliteOrbit.position(a, 400, 0, 0, alt, 400)) < 1e-9,
                "a satellite starts its orbit at its launch");
        h.succeed();
    }

    /**
     * A shuttle that touches a satellite's body: both are destroyed. The satellite leaves the
     * registry (sky sync bumped), the shuttle is gone with its pilot thrown clear and alive, its
     * cargo and some Scrap are left behind, and no block is broken.
     */
    public static void collisionDestroysBoth(GameTestHelper h) {
        ServerPlayer pilot = h.makeMockServerPlayerInLevel();
        var server = h.getLevel().getServer();
        String team = FactoryTeams.get(server).teamOf(pilot.getUUID());
        Satellite sat = new Satellite(SatelliteType.UPLINK, Level.OVERWORLD, 0L, "Target", pilot.getUUID());
        OrbitRegistry.get(server).add(team, sat);
        int changes = OrbitRegistry.changes();
        h.setBlock(new BlockPos(0, 1, 0), net.minecraft.world.level.block.Blocks.STONE);

        Shuttle ship = h.spawn(ShipContent.SHUTTLE.get(), new Vec3(4.5, 2, 1.5));
        ship.setItem(0, new ItemStack(Items.DIAMOND, 3));
        pilot.setPos(h.absoluteVec(new Vec3(4.5, 2, 1.5)));
        check(h, pilot.startRiding(ship, true, false), "the pilot must board");
        OrbitingSatellite body = SatellitesContent.ORBITING_SATELLITE.get().create(h.getLevel(), EntitySpawnReason.EVENT);
        check(h, body != null, "the body must be created");
        body.link(sat.id(), sat.type(), sat.launchTime(), BlockPos.ZERO, 320);
        Vec3 at = h.absoluteVec(new Vec3(4.5, 2, 7.5));
        body.setPos(at.x, at.y, at.z); // outside Earth orbit a body holds still
        h.getLevel().addFreshEntity(body);
        h.runAfterDelay(2, () -> check(h, !ship.isRemoved() && OrbitRegistry.get(server).find(sat.id()).isPresent(),
                "nothing may happen before they touch"));
        h.runAfterDelay(3, () -> ship.setPos(h.absoluteVec(new Vec3(4.5, 2, 4.9)))); // drifts into it
        h.succeedWhen(() -> {
            check(h, OrbitRegistry.get(server).find(sat.id()).isEmpty(), "the satellite must leave the registry");
            check(h, OrbitRegistry.changes() > changes, "the sky sync must be bumped");
            check(h, ship.isRemoved(), "the shuttle must be destroyed");
            check(h, body.isRemoved(), "the satellite's body must be destroyed");
            check(h, pilot.getVehicle() == null, "the pilot must be thrown clear");
            check(h, pilot.isAlive(), "the crash hurts but must not kill");
            AABB area = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(16);
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, area);
            check(h, items.stream().anyMatch(i -> i.getItem().is(Items.DIAMOND) && i.getItem().getCount() == 3), "the cargo must survive the wreck");
            check(h, items.stream().anyMatch(i -> i.getItem().is(GearContent.SCRAP.get())), "the wreck must leave Scrap");
            check(h, items.stream().noneMatch(i -> i.getItem().is(ShipContent.SHUTTLE_ITEM.get())), "the hull is lost by default");
            check(h, h.getBlockState(new BlockPos(0, 1, 0)).is(net.minecraft.world.level.block.Blocks.STONE), "the crash must not break blocks");
        });
    }
}
