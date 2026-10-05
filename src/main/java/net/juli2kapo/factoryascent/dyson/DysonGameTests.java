package net.juli2kapo.factoryascent.dyson;

import net.juli2kapo.factoryascent.orbital.LaunchControllerBlockEntity;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Game test bodies for the Dyson Cube, listed in {@code ModGameTests.TESTS}. */
public final class DysonGameTests {
    private DysonGameTests() {}

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h) {
        return h.makeMockServerPlayerInLevel();
    }

    private static String team(GameTestHelper h, ServerPlayer player) {
        return DysonService.teamOf(h.getLevel().getServer(), player.getUUID());
    }

    /** A breech with {@code rails} rails on top, owned by the player. */
    private static MassDriverBlockEntity driver(GameTestHelper h, BlockPos pos, int rails, ServerPlayer owner) {
        h.setBlock(pos, DysonContent.MASS_DRIVER.get());
        for (int i = 1; i <= rails; i++) h.setBlock(pos.above(i), DysonContent.MASS_DRIVER_RAIL.get());
        MassDriverBlockEntity driver = h.getBlockEntity(pos, MassDriverBlockEntity.class);
        driver.setOwner(owner.getUUID());
        return driver;
    }

    /** A receiver with its eight arrays, owned by the player. */
    private static DysonReceiverBlockEntity receiver(GameTestHelper h, BlockPos pos, ServerPlayer owner) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.setBlock(pos.offset(dx, 0, dz), dx == 0 && dz == 0 ? DysonContent.DYSON_RECEIVER.get() : DysonContent.DYSON_RECEIVER_ARRAY.get());
            }
        }
        DysonReceiverBlockEntity receiver = h.getBlockEntity(pos, DysonReceiverBlockEntity.class);
        receiver.setOwner(owner.getUUID());
        return receiver;
    }

    /** Receivers on a planet only work in daylight: make it noon if it is night. */
    private static void daytime(GameTestHelper h) {
        if (h.getLevel().isDarkOutside()) {
            MinecraftServer server = h.getLevel().getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
        }
    }

    private static long expected(GameTestHelper h, String team, DysonReceiverBlockEntity receiver) {
        MinecraftServer server = h.getLevel().getServer();
        int exposure = Math.round(DysonReceiverBlockEntity.exposure(h.getLevel(), receiver.getBlockPos()) * 100);
        long budget = DysonSwarm.get(server).swarmPower(team) * exposure / 100;
        return Math.min(DysonConfig.RECEIVER_MAX_OUTPUT.get(), budget);
    }

    // ---------------------------------------------------------------- tests

    /** A charged, complete Mass Driver under open sky fires its collectors into the owner's team swarm, one per shot. */
    public static void massDriverLaunches(GameTestHelper h) {
        ServerPlayer owner = player(h);
        String team = team(h, owner);
        MinecraftServer server = h.getLevel().getServer();
        MassDriverBlockEntity driver = driver(h, new BlockPos(4, 1, 4), MassDriverBlockEntity.RAILS, owner);
        h.assertTrue(driver.isFormed(), "breech + rails must form the Mass Driver");
        h.assertTrue(driver.insert(new ItemStack(DysonContent.DYSON_COLLECTOR.get(), 3)) == 3, "it must take 3 collectors");
        h.assertTrue(driver.insert(new ItemStack(OrbitalContent.ROCKET_FUEL.get(), 3)) == 0, "it must refuse other items");
        driver.fill();
        int before = driver.energyStored();
        h.succeedWhen(() -> {
            h.assertTrue(DysonSwarm.get(server).collectors(team) == 1, "one collector must reach the swarm, got "
                    + DysonSwarm.get(server).collectors(team) + " (status " + driver.status() + ")");
            h.assertTrue(driver.stored() == 2, "the shot must use one collector, " + driver.stored() + " left");
            h.assertTrue(driver.energyStored() == before - DysonConfig.LAUNCH_ENERGY.get(), "the shot must use the launch energy");
            h.assertTrue(driver.lastShot() >= 0, "the shot must be recorded for the clients");
        });
    }

    /** No shot without all the rails, nor with the sky above the muzzle blocked; it fires once both are fixed. */
    public static void massDriverNeedsRailsAndSky(GameTestHelper h) {
        ServerPlayer owner = player(h);
        String team = team(h, owner);
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pos = new BlockPos(4, 1, 4);
        MassDriverBlockEntity driver = driver(h, pos, MassDriverBlockEntity.RAILS - 1, owner);
        driver.insert(new ItemStack(DysonContent.DYSON_COLLECTOR.get(), 2));
        driver.fill();
        BlockPos muzzle = pos.above(MassDriverBlockEntity.RAILS + 1);
        h.startSequence()
                .thenExecuteAfter(10, () -> {
                    h.assertTrue(driver.status() == MassDriverBlockEntity.STATUS_INCOMPLETE, "missing a rail: incomplete");
                    h.assertTrue(DysonSwarm.get(server).collectors(team) == 0, "an incomplete driver must not fire");
                    h.setBlock(pos.above(MassDriverBlockEntity.RAILS), DysonContent.MASS_DRIVER_RAIL.get());
                    h.setBlock(muzzle, Blocks.STONE);
                })
                .thenExecuteAfter(10, () -> {
                    h.assertTrue(driver.status() == MassDriverBlockEntity.STATUS_BLOCKED, "sky blocked: status must say so, got " + driver.status());
                    h.assertTrue(DysonSwarm.get(server).collectors(team) == 0, "a blocked driver must not fire");
                    h.setBlock(muzzle, Blocks.AIR);
                })
                .thenWaitUntil(() -> h.assertTrue(DysonSwarm.get(server).collectors(team) == 1, "once fixed it must fire"))
                .thenSucceed();
    }

    /** Receiver output = collectors × FE per collector × sun exposure (capped); none under a roof. */
    public static void receiverScalesWithSwarmAndSky(GameTestHelper h) {
        ServerPlayer owner = player(h);
        String team = team(h, owner);
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pos = new BlockPos(4, 1, 4);
        DysonReceiverBlockEntity receiver = receiver(h, pos, owner);
        h.assertTrue(receiver.isFormed(), "centre + 8 arrays must form the receiver");
        daytime(h);
        long[] first = new long[1];
        h.startSequence()
                .thenExecuteAfter(3, () -> {
                    h.assertTrue(receiver.lastIn() == 0, "no swarm, no power, got " + receiver.lastIn());
                    DysonService.setCollectors(server, team, 40);
                })
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(DysonReceiverBlockEntity.exposure(h.getLevel(), receiver.getBlockPos()) > 0, "open sky at noon must see the sun");
                    first[0] = receiver.lastIn();
                    h.assertTrue(first[0] > 0 && first[0] == expected(h, team, receiver),
                            "40 collectors must give " + expected(h, team, receiver) + " FE/t, got " + first[0]);
                    DysonService.setCollectors(server, team, 80);
                })
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(receiver.lastIn() == Math.min(DysonConfig.RECEIVER_MAX_OUTPUT.get(), first[0] * 2),
                            "twice the collectors must give twice the power, got " + receiver.lastIn() + " after " + first[0]);
                    DysonService.setCollectors(server, team, DysonSwarm.target());
                })
                .thenExecuteAfter(2, () -> {
                    h.assertTrue(receiver.lastIn() <= DysonConfig.RECEIVER_MAX_OUTPUT.get(), "one receiver is capped");
                    h.setBlock(pos.above(3), Blocks.STONE);
                })
                .thenExecuteAfter(22, () -> {
                    h.assertTrue(DysonReceiverBlockEntity.exposure(h.getLevel(), receiver.getBlockPos()) == 0, "a roof hides the sun");
                    h.assertTrue(receiver.lastIn() == 0, "under a roof there must be no power, got " + receiver.lastIn());
                })
                .thenSucceed();
    }

    /** Milestones fire in order as the swarm grows; the swarm stops at the target. */
    public static void milestonesFire(GameTestHelper h) {
        ServerPlayer owner = player(h);
        String team = team(h, owner);
        MinecraftServer server = h.getLevel().getServer();
        DysonSwarm swarm = DysonSwarm.get(server);
        int target = DysonSwarm.target();
        h.assertTrue(swarm.project(team).milestones() == 0, "a new team has no milestones");
        DysonService.addCollectors(server, team, 1);
        h.assertTrue(swarm.project(team).milestones() == 0b1, "the first collector is milestone 0, got " + swarm.project(team).milestones());
        DysonService.setCollectors(server, team, (long) Math.ceil(target * 0.25));
        h.assertTrue(swarm.project(team).milestones() == 0b111, "25% must reach first, 10% and 25%, got " + swarm.project(team).milestones());
        long added = DysonService.addCollectors(server, team, target * 2L);
        h.assertTrue(added == target - (long) Math.ceil(target * 0.25), "the swarm must stop at the target, added " + added);
        h.assertTrue(swarm.isComplete(team) && swarm.completion(team) == 1.0, "the cube must be complete");
        h.assertTrue(swarm.project(team).milestones() == 0b11111, "100% must reach every milestone, got " + swarm.project(team).milestones());
        h.assertTrue(DysonService.addCollectors(server, team, 1) == 0, "a complete cube takes no more collectors");
        var holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath("factoryascent", "dyson_100"));
        h.assertTrue(holder != null, "the Type II Civilization advancement must exist");
        if (server.getPlayerList().getPlayer(owner.getUUID()) != null) {
            h.assertTrue(owner.getAdvancements().getOrStartProgress(holder).isDone(), "the member must get Type II Civilization");
        }
        h.succeed();
    }

    /** One team's swarm is not another's: collectors, receivers and power stay with their team. */
    public static void swarmIsPerTeam(GameTestHelper h) {
        ServerPlayer alice = player(h), bob = player(h);
        String a = team(h, alice), b = team(h, bob);
        MinecraftServer server = h.getLevel().getServer();
        h.assertTrue(!a.equals(b), "two new players are on different teams");
        DysonReceiverBlockEntity ra = receiver(h, new BlockPos(2, 1, 2), alice);
        DysonReceiverBlockEntity rb = receiver(h, new BlockPos(6, 1, 6), bob);
        daytime(h);
        DysonService.addCollectors(server, a, 25);
        h.assertTrue(DysonSwarm.get(server).collectors(a) == 25 && DysonSwarm.get(server).collectors(b) == 0,
                "collectors must go to one team only");
        h.succeedWhen(() -> {
            h.assertTrue(ra.lastIn() > 0, "Alice's receiver must get her swarm's power");
            h.assertTrue(rb.lastIn() == 0, "Bob's receiver must get nothing from Alice's swarm, got " + rb.lastIn());
        });
    }

    /** The project and a loaded Mass Driver survive a save and load. */
    public static void savesAndLoads(GameTestHelper h) {
        ServerPlayer owner = player(h);
        String team = team(h, owner);
        MinecraftServer server = h.getLevel().getServer();
        DysonService.setCollectors(server, team, 123);
        DysonSwarm swarm = DysonSwarm.get(server);
        var saved = DysonSwarm.CODEC.encodeStart(NbtOps.INSTANCE, swarm).getOrThrow();
        DysonSwarm loaded = DysonSwarm.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        h.assertTrue(loaded.collectors(team) == 123, "collectors must be saved, got " + loaded.collectors(team));
        h.assertTrue(loaded.project(team).milestones() == swarm.project(team).milestones(), "milestones must be saved");
        h.assertTrue(loaded.project(team).launched() == swarm.project(team).launched(), "the launch count must be saved");

        BlockPos pos = new BlockPos(4, 1, 4);
        MassDriverBlockEntity driver = driver(h, pos, 1, owner);
        driver.insert(new ItemStack(DysonContent.DYSON_COLLECTOR.get(), 5));
        driver.fill();
        var tag = driver.saveWithFullMetadata(h.getLevel().registryAccess());
        BlockEntity copy = BlockEntity.loadStatic(h.absolutePos(pos), driver.getBlockState(), tag, h.getLevel().registryAccess());
        h.assertTrue(copy instanceof MassDriverBlockEntity, "the driver must load");
        MassDriverBlockEntity d2 = (MassDriverBlockEntity) copy;
        h.assertTrue(d2.stored() == 5, "loaded collectors must be kept, got " + d2.stored());
        h.assertTrue(owner.getUUID().equals(d2.owner()), "the owner must be kept");
        h.assertTrue(d2.energyStored() == driver.energyStored(), "the charge must be kept");
        h.succeed();
    }

    /** Early bootstrap: a Solar Collector can ride a rocket from the Launch Pad into the team's swarm. */
    public static void launchPadCarriesCollector(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 1, 4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.setBlock(c.offset(dx, 0, dz), dx == 0 && dz == 0 ? OrbitalContent.LAUNCH_CONTROLLER.get() : OrbitalContent.LAUNCH_PAD.get());
            }
        }
        var pad = h.getBlockEntity(c, LaunchControllerBlockEntity.class);
        ServerPlayer player = player(h);
        String team = team(h, player);
        MinecraftServer server = h.getLevel().getServer();
        h.assertTrue(LaunchControllerBlockEntity.isPayload(new ItemStack(DysonContent.DYSON_COLLECTOR.get())), "a collector is a payload");
        h.assertTrue(pad.mount(new ItemStack(DysonContent.DYSON_COLLECTOR.get()), player.getUUID()) == null, "mounting must work");
        h.assertTrue(pad.addFuel(LaunchControllerBlockEntity.fuelValue(new ItemStack(OrbitalContent.ROCKET_FUEL.get()))), "fuel");
        h.assertTrue(pad.tryLaunch() == null, "a fuelled pad must launch");
        h.succeedWhen(() -> {
            h.assertTrue(DysonSwarm.get(server).collectors(team) == 1, "the collector must join the launcher's swarm");
            h.assertTrue(pad.satellite().isEmpty() && !pad.launching(), "the pad must be empty after the launch");
        });
    }

    /** A creature standing in the beam between the dish and the sun burns, and shades the receiver. */
    public static void beamBurnsWhatStandsInIt(GameTestHelper h) {
        ServerPlayer owner = player(h);
        String team = team(h, owner);
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pos = new BlockPos(4, 1, 4);
        DysonReceiverBlockEntity receiver = receiver(h, pos, owner);
        daytime(h);
        DysonService.setCollectors(server, team, 400);
        net.minecraft.world.phys.Vec3 origin = h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 1 + 10 / 16.0, 4.5));
        net.minecraft.world.phys.Vec3 spot = origin.add(DysonReceiverBlockEntity.beamDirection(h.getLevel(), origin).scale(4));
        var cow = net.minecraft.world.entity.EntityTypes.COW.create(h.getLevel(),
                net.minecraft.world.entity.EntitySpawnReason.EVENT);
        h.assertTrue(cow != null, "a cow");
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.setPos(spot.x, spot.y - cow.getBbHeight() / 2, spot.z);
        h.getLevel().addFreshEntity(cow);
        float full = cow.getHealth();
        h.succeedWhen(() -> {
            h.assertTrue(receiver.active(), "the receiver must be beaming");
            h.assertTrue(cow.getHealth() < full || cow.isDeadOrDying(), "the cow in the beam must be hurt");
            h.assertTrue(receiver.lastIn() < expected(h, team, receiver), "a body in the beam must shade the receiver, got " + receiver.lastIn());
        });
    }
}
