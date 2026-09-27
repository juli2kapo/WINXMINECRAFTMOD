package net.juli2kapo.factoryascent.gametest;

import java.util.List;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.machine.ProcessingMachineBlockEntity;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.component.DataComponents;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.miner.MinerBlockEntity;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlock;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.juli2kapo.factoryascent.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Automated in-world checks for the core promises of the mod. Run with {@code ./gradlew runGameTestServer}.
 */
public final class ModGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, FactoryAscent.MOD_ID);
    private static final Identifier ARENA = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "test_arena");

    private record Test(String name, int maxTicks, Consumer<GameTestHelper> body) {}

    private static final List<Test> TESTS = List.of(
            new Test("quern_grinds_with_cranks", 200, ModGameTests::quernGrindsWithCranks),
            new Test("burner_crusher_doubles_ore", 300, ModGameTests::burnerCrusherDoublesOre),
            new Test("crusher_doubles_ore", 200, ModGameTests::crusherDoublesOre),
            new Test("press_uses_mold", 200, ModGameTests::pressUsesMold),
            new Test("recipe_needs_better_machine", 100, ModGameTests::recipeNeedsBetterMachine),
            new Test("coke_oven_multiblock", 600, ModGameTests::cokeOvenMultiblock),
            new Test("blast_furnace_makes_steel", 900, ModGameTests::blastFurnaceMakesSteel),
            new Test("generator_cable_furnace_chain", 400, ModGameTests::generatorCableFurnaceChain),
            new Test("pipe_extracts_between_chests", 100, ModGameTests::pipeExtractsBetweenChests),
            new Test("miner_digs_only_ore", 400, ModGameTests::minerDigsOnlyOre),
            new Test("auto_farmer_harvests_and_replants", 600, ModGameTests::autoFarmerHarvests),
            new Test("crate_keeps_contents", 40, ModGameTests::crateKeepsContents),
            new Test("energy_cell_charges_drill", 100, ModGameTests::energyCellChargesDrill),
            new Test("speed_upgrade_speeds_up", 20, ModGameTests::speedUpgrade)
    );

    private ModGameTests() {}

    public static void register(IEventBus modBus) {
        for (Test t : TESTS) FUNCTIONS.register(t.name(), () -> t.body());
        FUNCTIONS.register(modBus);
        modBus.addListener(ModGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "default"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test t : TESTS) {
            Identifier id = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, t.name());
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id),
                    new TestData<>(env, ARENA, t.maxTicks(), 0, true)));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static AbstractMachineBlockEntity place(GameTestHelper h, BlockPos pos, MachineType type) {
        h.setBlock(pos, ModBlocks.machine(type).get());
        return h.getBlockEntity(pos, AbstractMachineBlockEntity.class);
    }

    private static void charge(AbstractMachineBlockEntity be) {
        be.energy().produce(be.energy().capacity());
    }

    private static ItemStack slot(AbstractMachineBlockEntity be, int index) {
        return be.inventory().stack(index);
    }

    private static Item mat(String name) {
        return ModItems.MATERIALS.get(name).get();
    }

    private static void expect(GameTestHelper h, ItemStack stack, Item item, int count, String what) {
        h.assertTrue(stack.is(item) && stack.getCount() >= count,
                what + ": expected " + count + "x " + item + " but found " + stack);
    }

    private static void fuel(AbstractMachineBlockEntity be, ItemStack fuel) {
        be.inventory().setStack(be.inventory().slots().firstFuel(), fuel);
    }

    /** Fills the 3x3x3 cube whose front-bottom-centre is the controller at {@code c} (facing north). */
    private static void cube(GameTestHelper h, BlockPos c, net.minecraft.world.level.block.Block wall, boolean hollow) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = 0; dz <= 2; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (p.equals(c)) continue;
                    boolean center = dx == 0 && dy == 1 && dz == 1;
                    h.setBlock(p, center && hollow ? Blocks.AIR : wall);
                }
            }
        }
    }

    // ---------------------------------------------------------------- tests

    private static void quernGrindsWithCranks(GameTestHelper h) {
        var quern = (ProcessingMachineBlockEntity) place(h, new BlockPos(4, 1, 4), MachineType.QUERN);
        quern.inventory().setStack(0, new ItemStack(Items.RAW_IRON, 4));
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        for (int i = 0; i < 30; i++) h.runAfterDelay(5L * i + 1, () -> quern.crank(player));
        int out = quern.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(quern, out), mat("iron_dust"), 1, "quern output"));
    }

    private static void burnerCrusherDoublesOre(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.BURNER_CRUSHER);
        fuel(be, new ItemStack(Items.COAL, 2));
        be.inventory().setStack(0, new ItemStack(Items.RAW_IRON));
        int out = be.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(be, out), mat("iron_dust"), 2, "burner crusher output"));
    }

    private static void crusherDoublesOre(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.CRUSHER);
        charge(be);
        be.inventory().setStack(0, new ItemStack(Items.RAW_IRON));
        int out = be.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(be, out), mat("iron_dust"), 2, "crusher output"));
    }

    private static void pressUsesMold(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.METAL_PRESS);
        charge(be);
        var s = be.inventory().slots();
        be.inventory().setStack(s.firstInput(), new ItemStack(Items.IRON_INGOT, 2));
        be.inventory().setStack(s.firstMold(), new ItemStack(ModItems.MOLDS.get("gear_mold").get()));
        h.succeedWhen(() -> {
            expect(h, slot(be, s.firstOutput()), mat("iron_gear"), 1, "press output");
            h.assertTrue(!slot(be, s.firstMold()).isEmpty(), "mould must not be consumed");
        });
    }

    private static void recipeNeedsBetterMachine(GameTestHelper h) {
        var furnace = place(h, new BlockPos(4, 1, 4), MachineType.ELECTRIC_FURNACE);
        charge(furnace);
        furnace.inventory().setStack(0, new ItemStack(mat("titanium_dust")));
        int out = furnace.inventory().slots().firstOutput();
        h.runAfterDelay(60, () -> {
            h.assertTrue(slot(furnace, out).isEmpty(), "an Electric Furnace must not smelt titanium");
            h.assertTrue(furnace.status() == AbstractMachineBlockEntity.STATUS_TIER_TOO_LOW, "should report it needs a better machine");
            h.succeed();
        });
    }

    private static void cokeOvenMultiblock(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 1, 2);
        var oven = place(h, c, MachineType.COKE_OVEN);
        oven.inventory().setStack(0, new ItemStack(Items.COAL, 2));
        h.runAfterDelay(45, () -> {
            h.assertTrue(oven.status() == AbstractMachineBlockEntity.STATUS_INCOMPLETE, "no structure yet: must say incomplete");
            cube(h, c, ModBlocks.COKE_OVEN_BRICKS.get(), false);
        });
        int out = oven.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(oven, out), mat("coke"), 1, "coke oven output"));
    }

    private static void blastFurnaceMakesSteel(GameTestHelper h) {
        BlockPos c = new BlockPos(4, 1, 2);
        cube(h, c, ModBlocks.FIRE_BRICKS.get(), true);
        var furnace = place(h, c, MachineType.BLAST_FURNACE);
        fuel(furnace, new ItemStack(mat("coke"), 4));
        furnace.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 2));
        int out = furnace.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(furnace, out), mat("steel_ingot"), 1, "blast furnace output"));
    }

    private static void generatorCableFurnaceChain(GameTestHelper h) {
        var gen = place(h, new BlockPos(1, 1, 4), MachineType.COMBUSTION_GENERATOR);
        for (int x = 2; x <= 5; x++) h.setBlock(new BlockPos(x, 1, 4), ModBlocks.POWER_CABLES.get(Tier.LV).get());
        var furnace = place(h, new BlockPos(6, 1, 4), MachineType.ELECTRIC_FURNACE);
        fuel(gen, new ItemStack(Items.COAL, 4));
        furnace.inventory().setStack(0, new ItemStack(Items.RAW_IRON, 3));
        int out = furnace.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(furnace, out), Items.IRON_INGOT, 3, "furnace powered through cables"));
    }

    private static void pipeExtractsBetweenChests(GameTestHelper h) {
        BlockPos from = new BlockPos(2, 1, 4), pipePos = new BlockPos(3, 1, 4), to = new BlockPos(5, 1, 4);
        h.setBlock(from, Blocks.CHEST);
        h.setBlock(pipePos, ModBlocks.ITEM_PIPES.get(Tier.LV).get());
        h.setBlock(new BlockPos(4, 1, 4), ModBlocks.ITEM_PIPES.get(Tier.LV).get());
        h.setBlock(to, Blocks.CHEST);
        h.getBlockEntity(from, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 10));
        BlockPos abs = h.absolutePos(pipePos);
        var state = h.getLevel().getBlockState(abs);
        ((ItemPipeBlock) state.getBlock()).toggleExtract(h.getLevel(), abs, state, Direction.WEST);
        h.succeedWhen(() -> {
            Container chest = h.getBlockEntity(to, ChestBlockEntity.class);
            int total = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (chest.getItem(i).is(Items.DIAMOND)) total += chest.getItem(i).getCount();
            }
            h.assertTrue(total == 10, "expected 10 diamonds moved, found " + total);
        });
    }

    private static void minerDigsOnlyOre(GameTestHelper h) {
        BlockPos minerPos = new BlockPos(4, 4, 4);
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        h.setBlock(new BlockPos(3, 2, 3), Blocks.IRON_ORE);
        h.setBlock(new BlockPos(5, 1, 5), Blocks.DEEPSLATE_COPPER_ORE);
        var miner = (MinerBlockEntity) place(h, minerPos, MachineType.MINER);
        charge(miner);
        int out = miner.inventory().slots().firstOutput();
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 3));
            h.assertBlockPresent(Blocks.DEEPSLATE, new BlockPos(5, 1, 5));
            h.assertBlockPresent(Blocks.STONE, new BlockPos(4, 3, 4));
            boolean iron = false, copper = false;
            for (int i = out; i < miner.inventory().slots().firstUpgrade(); i++) {
                iron |= slot(miner, i).is(Items.RAW_IRON);
                copper |= slot(miner, i).is(Items.RAW_COPPER);
            }
            h.assertTrue(iron && copper, "miner should have raw iron and raw copper");
        });
    }

    private static void autoFarmerHarvests(GameTestHelper h) {
        // Farmer at z=0 facing south would need a rotation; default north: field is at z-1.. so place it at the back.
        BlockPos farmerPos = new BlockPos(4, 2, 8);
        BlockPos crop = new BlockPos(4, 2, 6);
        h.setBlock(crop.below(), Blocks.FARMLAND);
        h.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        var farmer = place(h, farmerPos, MachineType.AUTO_FARMER);
        charge(farmer);
        h.succeedWhen(() -> {
            boolean wheat = false;
            for (int i = farmer.inventory().slots().firstOutput(); i < farmer.inventory().slots().firstUpgrade(); i++) {
                wheat |= slot(farmer, i).is(Items.WHEAT);
            }
            h.assertTrue(wheat, "farmer should have harvested wheat");
            h.assertBlockPresent(Blocks.WHEAT, crop);
        });
    }

    private static void crateKeepsContents(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, ModBlocks.WOODEN_CRATE.get());
        h.getBlockEntity(pos, net.juli2kapo.factoryascent.storage.CrateBlockEntity.class).setItem(3, new ItemStack(Items.EMERALD, 7));
        h.getLevel().destroyBlock(h.absolutePos(pos), true);
        h.succeedWhen(() -> {
            var entities = h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(pos)).inflate(2));
            boolean ok = entities.stream().anyMatch(e -> e.getItem().is(ModBlocks.WOODEN_CRATE.get().asItem())
                    && e.getItem().get(DataComponents.CONTAINER) != null
                    && e.getItem().get(DataComponents.CONTAINER).nonEmptyItemCopyStream().anyMatch(s -> s.is(Items.EMERALD) && s.getCount() == 7));
            h.assertTrue(ok, "crate item should drop with the emeralds inside");
            h.assertTrue(entities.stream().noneMatch(e -> e.getItem().is(Items.EMERALD)), "emeralds must not spill");
        });
    }

    private static void energyCellChargesDrill(GameTestHelper h) {
        var cell = place(h, new BlockPos(4, 1, 4), MachineType.ENERGY_CELL);
        charge(cell);
        fuel(cell, new ItemStack(ModItems.ELECTRIC_DRILL.get()));
        h.succeedWhen(() -> {
            ItemStack drill = slot(cell, cell.inventory().slots().firstFuel());
            h.assertTrue(drill.getOrDefault(ModComponents.ENERGY.get(), 0) > 0, "drill should be charging");
        });
    }

    private static void speedUpgrade(GameTestHelper h) {
        var crusher = place(h, new BlockPos(2, 1, 4), MachineType.CRUSHER);
        var s = crusher.inventory().slots();
        crusher.inventory().setStack(s.firstUpgrade(), new ItemStack(ModItems.SPEED_UPGRADE.get()));
        h.assertTrue(Math.abs(crusher.speedMultiplier() - 1.5f) < 0.001f, "one speed upgrade = x1.5");
        h.succeed();
    }
}
