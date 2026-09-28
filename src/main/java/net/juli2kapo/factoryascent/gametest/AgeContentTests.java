package net.juli2kapo.factoryascent.gametest;

import java.util.Optional;
import net.juli2kapo.factoryascent.automation.BlockBreakerBlockEntity;
import net.juli2kapo.factoryascent.automation.FloodlightBlockEntity;
import net.juli2kapo.factoryascent.automation.MobFarmBlockEntity;
import net.juli2kapo.factoryascent.automation.TreeFarmBlockEntity;
import net.juli2kapo.factoryascent.gear.BackpackMenu;
import net.juli2kapo.factoryascent.gear.GearContent;
import net.juli2kapo.factoryascent.gear.GrapplingHookItem;
import net.juli2kapo.factoryascent.gear.ItemMagnetItem;
import net.juli2kapo.factoryascent.kinetic.KineticBlockEntity;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.ProcessingMachineBlockEntity;
import net.juli2kapo.factoryascent.mobs.CapturedMob;
import net.juli2kapo.factoryascent.mobs.MobCapsuleItem;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.juli2kapo.factoryascent.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** GameTests for the early and mid-game additions (Stone to Industrial ages). Registered in {@link ModGameTests}. */
public final class AgeContentTests {
    private AgeContentTests() {}

    private static AbstractMachineBlockEntity place(GameTestHelper h, BlockPos pos, MachineType type) {
        h.setBlock(pos, ModBlocks.machine(type).get());
        return h.getBlockEntity(pos, AbstractMachineBlockEntity.class);
    }

    private static void charge(AbstractMachineBlockEntity be) {
        be.energy().produce(be.energy().capacity());
    }

    private static boolean outputsHave(AbstractMachineBlockEntity be, Item item, int count) {
        var s = be.inventory().slots();
        int n = 0;
        for (int i = s.firstOutput(); i < s.firstUpgrade(); i++) {
            if (be.inventory().stack(i).is(item)) n += be.inventory().stack(i).getCount();
        }
        return n >= count;
    }

    private static Item mat(String name) {
        return ModItems.MATERIALS.get(name).get();
    }

    // ---------------------------------------------------------------- stone age

    /** Cranking a Sieve full of gravel gives flint every time (plus chance nuggets). */
    public static void sieveSiftsGravel(GameTestHelper h) {
        var sieve = (ProcessingMachineBlockEntity) place(h, new BlockPos(4, 1, 4), MachineType.SIEVE);
        sieve.inventory().setStack(0, new ItemStack(Items.GRAVEL, 8));
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        for (int i = 0; i < 40; i++) h.runAfterDelay(5L * i + 1, () -> sieve.crank(player));
        h.succeedWhen(() -> h.assertTrue(outputsHave(sieve, Items.FLINT, 2), "sieve should have sifted 2 flint out of gravel"));
    }

    /** A Water Wheel in water turns the Quern next to it with nobody cranking. */
    public static void waterWheelDrivesQuern(GameTestHelper h) {
        var quern = place(h, new BlockPos(3, 1, 4), MachineType.QUERN);
        quern.inventory().setStack(0, new ItemStack(Items.RAW_IRON, 2));
        var wheel = (KineticBlockEntity) place(h, new BlockPos(4, 1, 4), MachineType.WATER_WHEEL);
        h.setBlock(new BlockPos(4, 1, 3), Blocks.WATER);
        h.setBlock(new BlockPos(4, 1, 5), Blocks.WATER);
        h.succeedWhen(() -> {
            h.assertTrue(wheel.output() > 0, "the wheel should turn in water");
            h.assertTrue(wheel.driven() == 1, "the wheel should drive the quern");
            h.assertTrue(outputsHave(quern, mat("iron_dust"), 1), "the wheel-driven quern should grind raw iron");
        });
    }

    /** A Windmill turns with open air in front of its sails and jams with a block there. */
    public static void windmillNeedsOpenAir(GameTestHelper h) {
        var sieve = place(h, new BlockPos(4, 3, 5), MachineType.SIEVE);
        sieve.inventory().setStack(0, new ItemStack(Items.DIRT, 4));
        var mill = (KineticBlockEntity) place(h, new BlockPos(4, 3, 4), MachineType.WINDMILL);
        // facing north: the sails sweep the 3x3 around (4, 3, 3)
        h.runAfterDelay(30, () -> {
            h.assertTrue(mill.output() > 0, "an unobstructed windmill should turn");
            h.assertTrue(mill.driven() == 1, "the windmill should drive the sieve behind it");
            h.setBlock(new BlockPos(5, 4, 3), Blocks.STONE);
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(mill.output() == 0, "a block in the sails' way should stop the windmill");
            h.succeed();
        });
    }

    /** Rotten flesh hung on a Drying Rack becomes leather, without fuel. */
    public static void dryingRackMakesLeather(GameTestHelper h) {
        var rack = place(h, new BlockPos(4, 1, 4), MachineType.DRYING_RACK);
        rack.inventory().setStack(0, new ItemStack(Items.ROTTEN_FLESH, 2));
        h.succeedWhen(() -> h.assertTrue(outputsHave(rack, Items.LEATHER, 1), "the rack should dry rotten flesh into leather"));
    }

    // ---------------------------------------------------------------- bronze age

    /** What goes into an open backpack is saved in the item and comes back when it is opened again. */
    public static void backpackKeepsContents(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        ItemStack bag = new ItemStack(GearContent.BRONZE_BACKPACK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bag);
        var menu = new BackpackMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 5));
        menu.getSlot(26).set(new ItemStack(Items.COBBLESTONE, 64));
        h.assertFalse(menu.getSlot(1).mayPlace(new ItemStack(GearContent.BRONZE_BACKPACK.get())), "a backpack must not fit in a backpack");
        menu.removed(player);
        ItemContainerContents saved = player.getMainHandItem().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        h.assertTrue(saved.nonEmptyItemCopyStream().anyMatch(s -> s.is(Items.DIAMOND) && s.getCount() == 5),
                "the backpack item should hold the 5 diamonds");
        var again = new BackpackMenu(2, player.getInventory(), InteractionHand.MAIN_HAND);
        h.assertTrue(again.getSlot(0).getItem().is(Items.DIAMOND) && again.getSlot(26).getItem().getCount() == 64,
                "re-opening the backpack should show what was left in it");
        player.discard();
        h.succeed();
    }

    /** The hook pulls you towards where it bit, and upwards a little. */
    public static void grapplingHookPulls(GameTestHelper h) {
        Vec3 from = new Vec3(0, 64, 0), target = new Vec3(10, 70, 0);
        Vec3 v = GrapplingHookItem.launchVelocity(from, target);
        h.assertTrue(v.x > 0.5 && v.y > 0.2 && Math.abs(v.z) < 1e-6, "launch should head for the target, got " + v);
        h.succeed();
    }

    // ---------------------------------------------------------------- electric age

    /** The Charger fills an empty Item Magnet and moves it to its output when full. */
    public static void chargerChargesItems(GameTestHelper h) {
        var charger = place(h, new BlockPos(4, 1, 4), MachineType.CHARGER);
        charge(charger);
        charger.inventory().setStack(0, new ItemStack(GearContent.ITEM_MAGNET.get()));
        var s = charger.inventory().slots();
        h.succeedWhen(() -> {
            ItemStack out = charger.inventory().stack(s.firstOutput());
            h.assertTrue(out.is(GearContent.ITEM_MAGNET.get()), "the charged magnet should be in the output");
            h.assertTrue(out.getOrDefault(ModComponents.ENERGY.get(), 0) == ItemMagnetItem.CAPACITY, "the magnet should be full");
        });
    }

    /** A switched-on, charged magnet brings loose items to the player. */
    public static void magnetPullsItems(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        BlockPos start = h.absolutePos(new BlockPos(1, 1, 1));
        player.snapTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        ItemStack magnet = new ItemStack(GearContent.ITEM_MAGNET.get());
        magnet.set(ModComponents.ENERGY.get(), ItemMagnetItem.CAPACITY);
        magnet.set(GearContent.MAGNET_ON.get(), true);
        ItemEntity loot = new ItemEntity(h.getLevel(), start.getX() + 5.5, start.getY(), start.getZ() + 5.5, new ItemStack(Items.EMERALD));
        loot.setNoPickUpDelay();
        h.getLevel().addFreshEntity(loot);
        int moved = ItemMagnetItem.pull(magnet, h.getLevel(), player);
        h.assertTrue(moved >= 1, "the magnet should pull the emerald");
        h.assertTrue(loot.distanceTo(player) < 1.5, "the emerald should be at the player");
        h.assertTrue(magnet.getOrDefault(ModComponents.ENERGY.get(), 0) < ItemMagnetItem.CAPACITY, "pulling should cost energy");
        loot.discard();
        player.discard();
        h.succeed();
    }

    /** A powered Floodlight lights the surfaces in front of it and takes its lights away when broken. */
    public static void floodlightLightsArea(GameTestHelper h) {
        BlockPos lamp = new BlockPos(4, 2, 7);
        var light = (FloodlightBlockEntity) place(h, lamp, MachineType.FLOODLIGHT);
        charge(light);
        for (int x = 0; x < 9; x++) for (int y = 1; y < 6; y++) h.setBlock(new BlockPos(x, y, 0), Blocks.STONE);
        AABB area = new AABB(h.absolutePos(BlockPos.ZERO)).expandTowards(9, 7, 9);
        h.runAfterDelay(20, () -> {
            h.assertTrue(light.lightCount() > 0, "the floodlight should have placed lights");
            h.assertTrue(BlockPos.betweenClosedStream(area).anyMatch(p -> h.getLevel().getBlockState(p).is(Blocks.LIGHT)),
                    "light blocks should be in the area");
            h.getLevel().destroyBlock(h.absolutePos(lamp), false);
        });
        h.runAfterDelay(22, () -> {
            h.assertTrue(BlockPos.betweenClosedStream(area).noneMatch(p -> h.getLevel().getBlockState(p).is(Blocks.LIGHT)),
                    "breaking the floodlight must remove its lights");
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- automation age

    /** The Block Breaker digs the block in front of it into its outputs; the Block Placer puts one back. */
    public static void breakerAndPlacer(GameTestHelper h) {
        var breaker = (BlockBreakerBlockEntity) place(h, new BlockPos(2, 1, 4), MachineType.BLOCK_BREAKER);
        charge(breaker);
        h.setBlock(new BlockPos(2, 1, 3), Blocks.STONE);
        var placer = place(h, new BlockPos(6, 1, 4), MachineType.BLOCK_PLACER);
        charge(placer);
        placer.inventory().setStack(0, new ItemStack(Items.OAK_PLANKS, 3));
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 3));
            h.assertTrue(outputsHave(breaker, Items.COBBLESTONE, 1), "the breaker should keep the cobblestone");
            h.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(6, 1, 3));
            h.assertTrue(placer.inventory().stack(0).getCount() == 2, "the placer should use one plank");
        });
    }

    /** Items lying around are pulled into the Vacuum Hopper. */
    public static void vacuumHopperCollects(GameTestHelper h) {
        var hopper = place(h, new BlockPos(4, 1, 4), MachineType.VACUUM_HOPPER);
        charge(hopper);
        BlockPos drop = h.absolutePos(new BlockPos(7, 1, 7));
        ItemEntity item = new ItemEntity(h.getLevel(), drop.getX() + 0.5, drop.getY() + 0.2, drop.getZ() + 0.5, new ItemStack(Items.IRON_INGOT, 7));
        item.setNoPickUpDelay();
        h.getLevel().addFreshEntity(item);
        h.succeedWhen(() -> h.assertTrue(outputsHave(hopper, Items.IRON_INGOT, 7), "the hopper should have sucked up the 7 ingots"));
    }

    /** The Tree Farm fells a grown tree at a planting spot and plants a sapling there again. */
    public static void treeFarmFellsAndReplants(GameTestHelper h) {
        var farm = (TreeFarmBlockEntity) place(h, new BlockPos(4, 1, 8), MachineType.TREE_FARM);
        charge(farm);
        farm.inventory().setStack(0, new ItemStack(Items.OAK_SAPLING, 4));
        BlockPos spot = farm.spot(0);
        var level = h.getLevel();
        level.setBlockAndUpdate(spot.below(), Blocks.DIRT.defaultBlockState());
        for (int y = 0; y < 4; y++) level.setBlockAndUpdate(spot.above(y), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(spot.above(4), Blocks.OAK_LEAVES.defaultBlockState());
        h.succeedWhen(() -> {
            h.assertTrue(outputsHave(farm, Items.OAK_LOG, 4), "the farm should have felled the 4 logs");
            h.assertTrue(level.getBlockState(spot.above(4)).isAir(), "the leaves should be cleared too");
            h.assertTrue(level.getBlockState(spot).is(Blocks.OAK_SAPLING), "a sapling should be planted in the spot");
        });
    }

    // ---------------------------------------------------------------- industrial age

    /** The Industrial Grinder turns one raw ore into four dust. */
    public static void industrialGrinderQuadruples(GameTestHelper h) {
        var grinder = place(h, new BlockPos(4, 1, 4), MachineType.INDUSTRIAL_GRINDER);
        charge(grinder);
        grinder.inventory().setStack(0, new ItemStack(Items.RAW_IRON, 1));
        h.succeedWhen(() -> h.assertTrue(outputsHave(grinder, mat("iron_dust"), 4), "the grinder should make 4 iron dust"));
    }

    /** The Recycler turns junk into scrap and a worn iron pickaxe into iron dust. */
    public static void recyclerSalvages(GameTestHelper h) {
        var junk = place(h, new BlockPos(2, 1, 4), MachineType.RECYCLER);
        charge(junk);
        junk.inventory().setStack(0, new ItemStack(Items.COBBLESTONE, 16));
        var gear = place(h, new BlockPos(6, 1, 4), MachineType.RECYCLER);
        charge(gear);
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(200);
        gear.inventory().setStack(0, pick);
        h.succeedWhen(() -> {
            h.assertTrue(outputsHave(junk, GearContent.SCRAP.get(), 1), "16 cobblestone should become scrap");
            h.assertTrue(outputsHave(gear, mat("iron_dust"), 2), "an iron pickaxe should give back 2 iron dust");
        });
    }

    /** A Mob Farm Controller with a captured cow produces cow drops; bosses can't be farmed. */
    public static void mobFarmDropsLoot(GameTestHelper h) {
        var cow = h.spawnWithNoFreeWill(EntityTypes.COW, new BlockPos(1, 1, 1));
        ItemStack capsule = MobCapsuleItem.store(cow);
        var farm = (MobFarmBlockEntity) place(h, new BlockPos(4, 1, 4), MachineType.MOB_FARM);
        charge(farm);
        farm.inventory().setStack(0, capsule);
        h.assertFalse(MobFarmBlockEntity.farmable(new CapturedMob(EntityTypes.WITHER, new CompoundTag(), 300, 300, Optional.empty())),
                "the Wither must not be farmable");
        h.succeedWhen(() -> {
            h.assertTrue(outputsHave(farm, Items.BEEF, 1), "the farm should have produced beef");
            h.assertTrue(farm.inventory().stack(0).is(capsule.getItem()), "the capsule stays in the farm");
        });
    }
}
