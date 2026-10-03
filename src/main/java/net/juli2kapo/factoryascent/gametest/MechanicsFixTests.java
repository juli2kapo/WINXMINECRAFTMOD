package net.juli2kapo.factoryascent.gametest;

import net.juli2kapo.factoryascent.automation.TreeFarmBlockEntity;
import net.juli2kapo.factoryascent.guide.GuideMultiblocks;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.mobs.MobCapsuleItem;
import net.juli2kapo.factoryascent.mobs.MobContent;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.juli2kapo.factoryascent.space.station.MagneticBoots;
import net.juli2kapo.factoryascent.space.station.MagneticBoots.Hold;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** GameTests of the mechanics fixes: capsule charge, tree farm replanting, hologram facings, boots on walls and ceilings. */
public final class MechanicsFixTests {
    private MechanicsFixTests() {}

    /** A full charge is one capture plus one release; an empty capsule refuses; creative players don't pay. */
    public static void capsuleChargeCosts(GameTestHelper h) {
        int full = MobCapsuleItem.CAPACITY;
        h.assertTrue(MobCapsuleItem.CAPTURE_COST + MobCapsuleItem.RELEASE_COST == full, "capture + release = one full charge");
        h.assertTrue(MobCapsuleItem.canPay(full, MobCapsuleItem.CAPTURE_COST, false), "a full capsule can capture");
        int afterCapture = MobCapsuleItem.afterPaying(full, MobCapsuleItem.CAPTURE_COST, false);
        h.assertTrue(afterCapture == full / 2, "capturing takes half, left " + afterCapture);
        h.assertTrue(MobCapsuleItem.canPay(afterCapture, MobCapsuleItem.RELEASE_COST, false), "...and can still release");
        int afterRelease = MobCapsuleItem.afterPaying(afterCapture, MobCapsuleItem.RELEASE_COST, false);
        h.assertTrue(afterRelease == 0, "release takes the other half");
        h.assertTrue(!MobCapsuleItem.canPay(afterRelease, MobCapsuleItem.CAPTURE_COST, false), "empty: no second capture");
        h.assertTrue(!MobCapsuleItem.canPay(MobCapsuleItem.CAPTURE_COST - 1, MobCapsuleItem.CAPTURE_COST, false), "one FE short refuses");
        h.assertTrue(MobCapsuleItem.canPay(0, MobCapsuleItem.CAPTURE_COST, true) && MobCapsuleItem.afterPaying(7, 99, true) == 7,
                "creative players don't pay");
        // the charge travels with a captured mob, and the capsule is a one-per-slot FE item
        ItemStack capsule = new ItemStack(MobContent.MOB_CAPSULE.get());
        h.assertTrue(capsule.getMaxStackSize() == 1, "capsules don't stack (the charge belongs to one capsule)");
        MobCapsuleItem.setEnergy(capsule, full + 5);
        h.assertTrue(MobCapsuleItem.energy(capsule) == full, "the charge is capped at the capacity");
        Pig pig = h.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(2, 1, 2));
        ItemStack stored = MobCapsuleItem.store(pig);
        MobCapsuleItem.setEnergy(stored, afterCapture);
        h.assertTrue(MobCapsuleItem.isFull(stored) && stored.getOrDefault(ModComponents.ENERGY.get(), 0) == afterCapture,
                "a full capsule keeps the charge left after the capture");
        // the Charger fills an empty capsule
        h.setBlock(new BlockPos(5, 1, 5), ModBlocks.machine(MachineType.CHARGER).get());
        var charger = h.getBlockEntity(new BlockPos(5, 1, 5), AbstractMachineBlockEntity.class);
        charger.energy().produce(charger.energy().capacity());
        charger.inventory().setStack(0, new ItemStack(MobContent.MOB_CAPSULE.get()));
        var s = charger.inventory().slots();
        h.succeedWhen(() -> h.assertTrue(MobCapsuleItem.energy(charger.inventory().stack(s.firstOutput())) == full,
                "the Charger should fill the capsule"));
    }

    /** The Tree Farm replants with the saplings in its outputs before touching its input slots. */
    public static void treeFarmReplantsFromOutputs(GameTestHelper h) {
        h.setBlock(new BlockPos(4, 1, 8), ModBlocks.machine(MachineType.TREE_FARM).get());
        var farm = h.getBlockEntity(new BlockPos(4, 1, 8), TreeFarmBlockEntity.class);
        farm.energy().produce(farm.energy().capacity());
        var slots = farm.inventory().slots();
        int[] order = TreeFarmBlockEntity.plantingOrder(slots);
        h.assertTrue(order.length == slots.outputs() + slots.inputs() && order[0] == slots.firstOutput()
                && order[order.length - 1] == slots.firstInput() + slots.inputs() - 1, "outputs first, then inputs");
        farm.inventory().setStack(0, new ItemStack(Items.OAK_SAPLING, 4));
        farm.inventory().setStack(slots.firstOutput(), new ItemStack(Items.BIRCH_SAPLING, 16));
        BlockPos spot = farm.spot(0);
        h.getLevel().setBlockAndUpdate(spot.below(), Blocks.DIRT.defaultBlockState());
        h.getLevel().setBlockAndUpdate(spot, Blocks.AIR.defaultBlockState());
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getBlockState(spot).is(Blocks.BIRCH_SAPLING), "the harvested (output) sapling is planted");
            h.assertTrue(farm.inventory().stack(0).getCount() == 4, "the input saplings are kept for later");
            h.assertTrue(farm.inventory().stack(slots.firstOutput()).is(Items.BIRCH_SAPLING), "the extra saplings stay in the output");
        });
    }

    /** Hologram: a strict cell (a controller) passes only facing the right way, also when the projection is turned. */
    public static void hologramChecksFacing(GameTestHelper h) {
        GuideMultiblocks.Layout coke = GuideMultiblocks.variants("coke_oven").get(0);
        GuideMultiblocks.Cell ctrl = coke.cells().stream().filter(c -> c.x() == coke.controller.getX() && c.y() == coke.controller.getY()
                && c.z() == coke.controller.getZ()).findFirst().orElseThrow();
        GuideMultiblocks.Cell brick = coke.cells().stream().filter(c -> !c.strict()).findFirst().orElseThrow();
        h.assertTrue(ctrl.strict(), "the controller's facing matters");
        h.assertTrue(GuideMultiblocks.facingOf(ctrl.state()) == Direction.NORTH, "canonical layouts face north");
        for (Rotation r : Rotation.values()) {
            BlockState want = ctrl.state().rotate(r);
            Direction d = r.rotate(Direction.NORTH);
            h.assertTrue(GuideMultiblocks.facingOf(want) == d, "turning the projection turns the facing: " + r);
            h.assertTrue(GuideMultiblocks.matches(want, GuideMultiblocks.facing(ctrl.state().getBlock(), d), true), "right way passes");
            BlockState wrong = GuideMultiblocks.facing(ctrl.state().getBlock(), d.getOpposite());
            h.assertTrue(!GuideMultiblocks.matches(want, wrong, true) && GuideMultiblocks.wrongFacing(want, wrong, true),
                    "facing the wrong way fails (and is told apart from a wrong block)");
        }
        h.assertTrue(!GuideMultiblocks.wrongFacing(ctrl.state(), Blocks.STONE.defaultBlockState(), true), "a wrong block is not a wrong facing");
        h.assertTrue(GuideMultiblocks.matches(brick.state(), brick.state(), brick.strict()), "plain blocks only need the block");
        for (String id : new String[] {"coke_oven", "blast_furnace", "fission_reactor", "wind_turbine"}) {
            var l = GuideMultiblocks.variants(id).get(0);
            h.assertTrue(l.cells().stream().anyMatch(c -> c.strict() && c.x() == l.controller.getX() && c.y() == l.controller.getY()
                    && c.z() == l.controller.getZ()), id + ": the controller must be strict (its check reads the facing)");
        }
        // the real check agrees: a coke oven with its controller facing into the structure is not formed
        BlockPos origin = new BlockPos(3, 1, 3);
        for (GuideMultiblocks.Cell c : coke.cells()) {
            BlockState s = c.strict() ? GuideMultiblocks.facing(c.state().getBlock(), Direction.SOUTH) : c.state();
            h.setBlock(origin.offset(c.x(), c.y(), c.z()), s);
        }
        BlockPos controller = h.absolutePos(origin.offset(coke.controller));
        h.runAfterDelay(20, () -> {
            h.assertTrue(!coke.validator.formed(h.getLevel(), controller), "a controller facing inward must not form the oven");
            h.setBlock(origin.offset(coke.controller), ctrl.state());
            h.runAfterDelay(20, () -> {
                h.assertTrue(coke.validator.formed(h.getLevel(), controller), "facing outward it forms");
                h.succeed();
            });
        });
    }

    /** Magnetic Boots in low gravity: walls are climbed, ceilings held, sneaking lets go, and floors keep the old rule. */
    public static void bootsWallsAndCeilings(GameTestHelper h) {
        double g = 0.166;
        h.assertTrue(MagneticBoots.hold(true, g, false, true, false, false, false, false, 0) == Hold.FLOOR, "on the floor");
        h.assertTrue(MagneticBoots.hold(true, g, false, true, true, false, false, false, 1) == Hold.WALL, "walking into a wall climbs it");
        h.assertTrue(MagneticBoots.hold(true, g, false, true, true, false, false, false, 0) == Hold.FLOOR,
                "standing next to a wall on the floor stays on the floor");
        h.assertTrue(MagneticBoots.hold(true, g, false, false, true, false, false, false, 0) == Hold.WALL, "off the ground at a wall: hold on");
        h.assertTrue(MagneticBoots.hold(true, g, false, false, false, true, false, true, 0) == Hold.CEILING, "jumping into a ceiling sticks");
        h.assertTrue(MagneticBoots.hold(true, g, false, false, false, true, true, false, 0) == Hold.CEILING, "and stays stuck");
        h.assertTrue(MagneticBoots.hold(true, g, false, false, false, true, false, false, 0) == Hold.FLOOR,
                "drifting past a ceiling without jumping doesn't stick");
        h.assertTrue(MagneticBoots.hold(true, g, true, false, true, true, true, true, 1) == Hold.NONE, "sneaking lets go");
        h.assertTrue(MagneticBoots.hold(true, 1.0, false, false, true, true, true, true, 1) == Hold.NONE, "at home the magnets do nothing");
        h.assertTrue(MagneticBoots.hold(false, g, false, false, true, true, true, true, 1) == Hold.NONE, "only with the boots on");
        h.assertTrue(MagneticBoots.verticalSpeed(Hold.WALL, -0.3, 1, false) == MagneticBoots.CLIMB_UP, "forward climbs");
        h.assertTrue(MagneticBoots.verticalSpeed(Hold.WALL, -0.3, -1, false) == MagneticBoots.CLIMB_DOWN, "back climbs down");
        h.assertTrue(MagneticBoots.verticalSpeed(Hold.WALL, -0.3, 0, false) == 0.0, "no keys: you stay put on the wall");
        h.assertTrue(MagneticBoots.verticalSpeed(Hold.CEILING, -0.3, 0, false) > 0, "the soles press up into the ceiling");
        h.assertTrue(MagneticBoots.verticalSpeed(Hold.FLOOR, -0.3, 0, false) == -0.3, "the floor leaves the speed alone");
        h.succeed();
    }
}
