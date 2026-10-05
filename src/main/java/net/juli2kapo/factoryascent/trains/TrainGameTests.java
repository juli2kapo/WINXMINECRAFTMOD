package net.juli2kapo.factoryascent.trains;

import java.util.List;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Game test bodies for the trains, listed in {@code ModGameTests.TESTS}. */
public final class TrainGameTests {
    private TrainGameTests() {}

    private static void check(GameTestHelper h, boolean ok, String message) {
        if (!ok) h.fail(message);
    }

    // ---------------------------------------------------------------- track building

    /** Places a rail with an exact shape (placed twice so the neighbours' auto-shaping can't change it). */
    private static void rail(GameTestHelper h, int x, int y, int z, RailShape shape) {
        var state = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
        h.setBlock(x, y, z, state);
    }

    private static void fixRail(GameTestHelper h, int x, int y, int z, RailShape shape) {
        var state = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
        h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), state, Block.UPDATE_CLIENTS);
    }

    /** A rail loop around the arena: x and z from 1 to 7 at y 1, four curves. */
    private static void loop(GameTestHelper h) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 2; i <= 6; i++) {
                place(h, pass, i, 1, 1, RailShape.EAST_WEST);
                place(h, pass, i, 1, 7, RailShape.EAST_WEST);
                place(h, pass, 1, 1, i, RailShape.NORTH_SOUTH);
                place(h, pass, 7, 1, i, RailShape.NORTH_SOUTH);
            }
            place(h, pass, 1, 1, 1, RailShape.SOUTH_EAST);
            place(h, pass, 7, 1, 1, RailShape.SOUTH_WEST);
            place(h, pass, 7, 1, 7, RailShape.NORTH_WEST);
            place(h, pass, 1, 1, 7, RailShape.NORTH_EAST);
        }
    }

    private static void place(GameTestHelper h, int pass, int x, int y, int z, RailShape shape) {
        if (pass == 0) rail(h, x, y, z, shape);
        else fixRail(h, x, y, z, shape);
    }

    /** A straight east-west line along z at y 1, x 0..8. */
    private static void straight(GameTestHelper h, int z) {
        for (int x = 0; x <= 8; x++) rail(h, x, 1, z, RailShape.EAST_WEST);
        for (int x = 0; x <= 8; x++) fixRail(h, x, 1, z, RailShape.EAST_WEST);
    }

    private static <T extends RollingStock> T put(GameTestHelper h, EntityType<T> type, double x, double z, Vec3 facing) {
        T stock = type.create(h.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
        if (stock == null) throw new IllegalStateException("could not create " + type);
        Vec3 at = h.absoluteVec(new Vec3(x, 1 + TrackWalker.RIDE, z));
        stock.setInitialPos(at.x, at.y, at.z);
        stock.face(facing);
        h.getLevel().addFreshEntity(stock);
        return stock;
    }

    private static final Vec3 EAST = new Vec3(1, 0, 0), WEST = new Vec3(-1, 0, 0), NORTH = new Vec3(0, 0, -1);

    private static void fuelSteam(SteamLocomotive loco, int water, int coal) {
        loco.water().setContents(Fluids.WATER, water);
        if (coal > 0) loco.setItem(0, new ItemStack(Items.COAL, coal));
    }

    // ---------------------------------------------------------------- tests

    /** The track geometry: straights, a slope, the end of the line, and a curve. */
    public static void trackWalker(GameTestHelper h) {
        rail(h, 1, 1, 4, RailShape.EAST_WEST);
        rail(h, 2, 1, 4, RailShape.ASCENDING_EAST);
        rail(h, 3, 2, 4, RailShape.EAST_WEST);
        rail(h, 4, 2, 4, RailShape.EAST_WEST);
        fixRail(h, 1, 1, 4, RailShape.EAST_WEST);
        fixRail(h, 2, 1, 4, RailShape.ASCENDING_EAST);
        fixRail(h, 3, 2, 4, RailShape.EAST_WEST);
        fixRail(h, 4, 2, 4, RailShape.EAST_WEST);
        var level = h.getLevel();
        BlockPos start = h.absolutePos(new BlockPos(1, 1, 4));
        TrackWalker.Spot s = new TrackWalker.Spot(start, RailShape.EAST_WEST, 0.5);
        boolean east = TrackWalker.pointsTowardB(s, EAST);
        TrackWalker.Result up = TrackWalker.walk(level, s, east, 1.0, null);
        check(h, !up.blocked(), "walking onto the slope must not be blocked");
        double climbed = up.spot().pos().y - (start.getY() + TrackWalker.RIDE);
        check(h, Math.abs(climbed - 0.5 / Math.sqrt(2)) < 0.02, "half a block up the slope rises 0.354, rose " + climbed);
        TrackWalker.Result far = TrackWalker.walk(level, s, east, 10, null);
        double expect = 0.5 + Math.sqrt(2) + 2;
        check(h, far.blocked(), "the end of the line must block");
        check(h, Math.abs(far.travelled() - expect) < 0.01, "walk to the buffer should cover " + expect + ", got " + far.travelled());
        check(h, Math.abs(far.spot().pos().y - (start.getY() + 1 + TrackWalker.RIDE)) < 0.01, "the upper level is one block up");
        // back down again from the top
        TrackWalker.Result back = TrackWalker.walk(level, far.spot(), !far.towardB(), expect, null);
        check(h, back.spot().pos().distanceTo(s.pos()) < 0.01, "walking back the same distance returns to the start");
        // a curve: the chord of a curved rail is sqrt(0.5) long
        rail(h, 6, 1, 6, RailShape.SOUTH_EAST);
        fixRail(h, 6, 1, 6, RailShape.SOUTH_EAST);
        TrackWalker.Spot c = TrackWalker.locate(level, h.absoluteVec(new Vec3(6.5, 1.07, 6.5)), null);
        check(h, c != null && Math.abs(c.length() - Math.sqrt(0.5)) < 1e-6, "a curve is the diagonal chord");
        h.succeed();
    }

    /**
     * A steam locomotive pulls two coupled cargo wagons round a loop with four curves: they keep their
     * coupling distance, stay on the rails, and the locomotive burns fuel and water while it pulls.
     */
    public static void trainRunsLoop(GameTestHelper h) {
        loop(h);
        SteamLocomotive loco = put(h, TrainContent.STEAM_LOCOMOTIVE.get(), 5.5, 1.5, EAST);
        CargoWagon a = put(h, TrainContent.CARGO_WAGON.get(), 3.5, 1.5, EAST);
        CargoWagon b = put(h, TrainContent.CARGO_WAGON.get(), 1.5, 2.5, NORTH);
        fuelSteam(loco, 10_000, 8);
        loco.setPressure(1);
        check(h, CouplerItem.couple(loco, a) == null, "the locomotive must couple to the wagon behind it");
        check(h, CouplerItem.couple(a, b) == null, "the two wagons must couple");
        loco.setThrottle(1);
        double[] travelled = {0};
        double[] zRange = {Double.MAX_VALUE, -Double.MAX_VALUE};
        Vec3[] last = {null};
        List<RollingStock> train = List.of(loco, a, b);
        h.onEachTick(() -> {
            if (last[0] != null) travelled[0] += loco.position().distanceTo(last[0]);
            last[0] = loco.position();
            zRange[0] = Math.min(zRange[0], loco.getZ());
            zRange[1] = Math.max(zRange[1], loco.getZ());
            if (loco.tickCount < 3) return; // the first tick snaps the wagons to their coupling distance
            for (int i = 0; i + 1 < train.size(); i++) {
                RollingStock p = train.get(i), q = train.get(i + 1);
                double d = p.position().distanceTo(q.position());
                double s = TrainPhysics.spacing(p, q);
                check(h, d <= s + 0.05 && d >= s * 0.7, "coupling distance " + d + " should be about " + s);
            }
            for (RollingStock r : train) {
                check(h, TrackWalker.locate(h.getLevel(), r.position(), r) != null, r.getName().getString() + " left the rails at " + r.position());
                check(h, r.isAlive(), "nothing may break");
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(travelled[0] > 14, "the train should run round the loop, travelled " + travelled[0]);
            h.assertTrue(zRange[1] - zRange[0] > 4, "the locomotive should have rounded a corner");
            check(h, loco.water().amount() < 10_000, "pulling must boil water");
            check(h, loco.getItem(0).getCount() < 8, "pulling must burn coal");
            check(h, Consist.of(loco).size() == 3, "the train must still be in one piece");
        });
    }

    /** Fuel use: a working steam engine burns coal and water, an idle one nothing; the diesel's generator burns diesel. */
    public static void fuelUse(GameTestHelper h) {
        straight(h, 2);
        straight(h, 4);
        straight(h, 6);
        SteamLocomotive working = put(h, TrainContent.STEAM_LOCOMOTIVE.get(), 1.5, 2.5, EAST);
        SteamLocomotive idle = put(h, TrainContent.STEAM_LOCOMOTIVE.get(), 1.5, 4.5, EAST);
        DieselLocomotive diesel = put(h, TrainContent.DIESEL_LOCOMOTIVE.get(), 1.8, 6.5, EAST);
        fuelSteam(working, 1000, 2);
        fuelSteam(idle, 1000, 2);
        working.setThrottle(1);
        diesel.diesel().setContents(ModFluids.DIESEL.source(), 1000);
        diesel.setEnergy(50_000);
        diesel.setThrottle(0.5);
        double x0 = working.getX(), dx0 = diesel.getX();
        h.runAfterDelay(80, () -> {
            check(h, working.getItem(0).getCount() == 1, "the working engine must have fired one coal, has " + working.getItem(0).getCount());
            check(h, working.water().amount() < 1000, "the working engine must boil water");
            check(h, working.pressure() > 0.5, "steam pressure must build, " + working.pressure());
            check(h, working.getX() - x0 > 0.5, "the working engine must move, moved " + (working.getX() - x0));
            check(h, idle.getItem(0).getCount() == 2 && idle.water().amount() == 1000, "an idle engine burns nothing");
            check(h, Math.abs(idle.getX() - x0) < 0.01, "an idle engine stays put");
            check(h, diesel.diesel().amount() < 1000 - 40, "the diesel generator must burn diesel, tank " + diesel.diesel().amount());
            check(h, diesel.getX() - dx0 > 0.5, "the diesel must move");
            h.succeed();
        });
    }

    /** A cargo wagon's hold survives saving and loading, and breaking it packs the hold into the dropped item. */
    public static void cargoPersists(GameTestHelper h) {
        straight(h, 4);
        CargoWagon wagon = put(h, TrainContent.CARGO_WAGON.get(), 4.5, 4.5, EAST);
        wagon.setItem(0, new ItemStack(Items.DIAMOND, 7));
        wagon.setItem(53, new ItemStack(Items.OAK_LOG, 64));
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        wagon.saveWithoutId(out);
        CargoWagon copy = TrainContent.CARGO_WAGON.get().create(h.getLevel(), EntitySpawnReason.LOAD);
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), out.buildResult()));
        check(h, copy.getItem(0).is(Items.DIAMOND) && copy.getItem(0).getCount() == 7, "diamonds must survive saving");
        check(h, copy.getItem(53).getCount() == 64, "the last slot must survive saving");
        // a tank wagon keeps its fluid in the item too
        TankWagon tank = put(h, TrainContent.TANK_WAGON.get(), 1.5, 4.5, EAST);
        tank.tank().setContents(ModFluids.DIESEL.source(), 12_345);
        ItemStack tankItem = new ItemStack(TrainContent.TANK_WAGON_ITEM.get());
        tank.writeToItem(tankItem);
        var contents = tankItem.get(FluidContent.TANK_CONTENTS.get());
        check(h, contents != null && contents.copy().getAmount() == 12_345, "the tank wagon item must carry its diesel");
        wagon.destroy(h.getLevel(), h.getLevel().damageSources().generic());
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(wagon.blockPosition()).inflate(3));
        check(h, drops.size() == 1, "breaking must drop just the wagon item, got " + drops.size());
        ItemStack dropped = drops.getFirst().getItem();
        check(h, dropped.is(TrainContent.CARGO_WAGON_ITEM.get()), "the drop must be the cargo wagon, got " + dropped);
        ItemContainerContents held = dropped.get(DataComponents.CONTAINER);
        check(h, held != null, "the item must carry the hold");
        CargoWagon placed = TrainContent.CARGO_WAGON.get().create(h.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
        placed.readFromItem(dropped);
        check(h, placed.getItem(0).getCount() == 7 && placed.getItem(53).getCount() == 64, "re-placing must unpack the hold");
        drops.forEach(ItemEntity::discard);
        h.succeed();
    }

    /** The tank wagon's fluid capability: fills, drains, holds one fluid at a time. */
    public static void tankHandler(GameTestHelper h) {
        straight(h, 4);
        TankWagon tank = put(h, TrainContent.TANK_WAGON.get(), 4.5, 4.5, EAST);
        var handler = tank.getCapability(Capabilities.Fluid.ENTITY, null);
        check(h, handler != null, "the tank wagon must expose a fluid handler");
        try (Transaction tx = Transaction.openRoot()) {
            int in = handler.insert(FluidResource.of(Fluids.WATER), 5000, tx);
            tx.commit();
            check(h, in == 5000, "5000 mB of water must go in, got " + in);
        }
        try (Transaction tx = Transaction.openRoot()) {
            int lava = handler.insert(FluidResource.of(Fluids.LAVA), 1000, tx);
            tx.commit();
            check(h, lava == 0, "a tank with water takes no lava, took " + lava);
        }
        try (Transaction tx = Transaction.openRoot()) {
            int out = handler.extract(FluidResource.of(Fluids.WATER), 2000, tx);
            tx.commit();
            check(h, out == 2000, "2000 mB must come out, got " + out);
        }
        check(h, tank.tank().amount() == 3000, "3000 mB must be left, " + tank.tank().amount());
        try (Transaction tx = Transaction.openRoot()) {
            int over = handler.insert(FluidResource.of(Fluids.WATER), 100_000, tx);
            tx.commit();
            check(h, over == TankWagon.CAPACITY - 3000, "it fills up to its capacity, took " + over);
        }
        h.succeed();
    }

    /**
     * A diesel locomotive pushes a cargo wagon into a Train Station: the station stops the train, loads the
     * wagon from the chest beside it, then lets it go after the waiting time.
     */
    public static void stationStopsAndLoads(GameTestHelper h) {
        straight(h, 4);
        h.setBlock(4, 1, 5, TrainContent.TRAIN_STATION.get());
        h.setBlock(4, 1, 6, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4, 1, 6)));
        chest.setItem(0, new ItemStack(Items.COAL, 64));
        chest.setItem(1, new ItemStack(Items.IRON_INGOT, 20));
        StationBlockEntity station = (StationBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4, 1, 5)));
        station.configure(StationBlockEntity.STOP_ALWAYS, 3, StationBlockEntity.MODE_LOAD);
        CargoWagon wagon = put(h, TrainContent.CARGO_WAGON.get(), 3.3, 4.5, EAST);
        DieselLocomotive loco = put(h, TrainContent.DIESEL_LOCOMOTIVE.get(), 0.4, 4.5, EAST);
        loco.setEnergy(DieselLocomotive.BATTERY);
        check(h, CouplerItem.couple(loco, wagon) == null, "the locomotive must couple to the wagon ahead of it");
        loco.setThrottle(0.4);
        boolean[] stopped = {false};
        int[] stoppedAt = {0};
        h.succeedWhen(() -> {
            if (!stopped[0]) {
                int coal = 0;
                for (ItemStack s : wagon.items()) if (s.is(Items.COAL)) coal += s.getCount();
                h.assertTrue(coal == 64 && Math.abs(wagon.speed()) < 0.01, "waiting for the station to stop and load the wagon (coal " + coal + ")");
                check(h, chest.getItem(0).isEmpty(), "the chest must have been emptied of coal");
                check(h, loco.status() == Locomotive.STATUS_STATION, "the driver must see the station hold, status " + loco.status());
                stopped[0] = true;
                stoppedAt[0] = loco.tickCount;
            }
            h.assertTrue(loco.speed() > 0.05, "waiting for the train to leave");
            check(h, loco.tickCount - stoppedAt[0] >= 20, "the train must wait its 3 s at the station, left after " + (loco.tickCount - stoppedAt[0]));
        });
    }

    /** Coupling rules: distance, ends in use, one train only once, uncoupling and losing a vehicle. */
    public static void couplingRules(GameTestHelper h) {
        straight(h, 4);
        straight(h, 7);
        CargoWagon w1 = put(h, TrainContent.CARGO_WAGON.get(), 1.5, 4.5, EAST);
        CargoWagon w2 = put(h, TrainContent.CARGO_WAGON.get(), 3.9, 4.5, EAST);
        TankWagon w3 = put(h, TrainContent.TANK_WAGON.get(), 6.3, 4.5, EAST);
        HopperWagon far = put(h, TrainContent.HOPPER_WAGON.get(), 7.5, 7.5, EAST);
        check(h, CouplerItem.couple(w1, far) != null, "wagons on different tracks far apart must not couple");
        check(h, CouplerItem.couple(w1, w2) == null, "neighbours must couple");
        check(h, Boolean.TRUE.equals(w1.endLinkedTo(w2)) && Boolean.FALSE.equals(w2.endLinkedTo(w1)), "w1's front holds w2's rear");
        check(h, CouplerItem.couple(w2, w1) != null, "a pair in one train can't be coupled again");
        check(h, CouplerItem.couple(w2, w3) == null, "the tank wagon must couple to the second wagon");
        check(h, Consist.of(w3).size() == 3, "three vehicles make one train");
        check(h, CouplerItem.couple(w1, w3) != null, "no loops: w1 and w3 are already in one train");
        h.runAfterDelay(3, () -> {
            double d = w1.position().distanceTo(w2.position());
            check(h, Math.abs(d - TrainPhysics.spacing(w1, w2)) < 0.02, "coupled wagons sit at their coupling distance, " + d);
            w2.uncouple(w3);
            check(h, !w3.hasLinks() && w2.link(true) == null, "uncoupling clears both ends");
            check(h, CouplerItem.couple(w2, w3) == null, "they can couple again");
            w2.discard();
            check(h, !w1.hasLinks() && !w3.hasLinks(), "losing a vehicle frees its neighbours");
            h.succeed();
        });
    }

    /** A loaded hopper wagon on a powered activator rail dumps its load into the hopper under the rail. */
    public static void hopperDumps(GameTestHelper h) {
        h.setBlock(4, 1, 4, Blocks.HOPPER);
        h.setBlock(4, 2, 5, Blocks.REDSTONE_BLOCK);
        h.setBlock(4, 2, 4, Blocks.ACTIVATOR_RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.PoweredRailBlock.SHAPE,
                RailShape.EAST_WEST));
        HopperWagon wagon = TrainContent.HOPPER_WAGON.get().create(h.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
        Vec3 at = h.absoluteVec(new Vec3(4.5, 2 + TrackWalker.RIDE, 4.5));
        wagon.setInitialPos(at.x, at.y, at.z);
        wagon.face(EAST);
        wagon.setItem(0, new ItemStack(Items.RAW_IRON, 32));
        wagon.setItem(5, new ItemStack(Items.COAL, 10));
        h.getLevel().addFreshEntity(wagon);
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(4, 1, 4)));
        h.succeedWhen(() -> {
            h.assertTrue(wagon.isEmpty(), "waiting for the wagon to dump");
            int iron = 0, coal = 0;
            for (int i = 0; i < hopper.getContainerSize(); i++) {
                if (hopper.getItem(i).is(Items.RAW_IRON)) iron += hopper.getItem(i).getCount();
                if (hopper.getItem(i).is(Items.COAL)) coal += hopper.getItem(i).getCount();
            }
            check(h, iron == 32 && coal == 10, "the load must end up in the hopper under the rail, iron " + iron + " coal " + coal);
        });
    }
}
