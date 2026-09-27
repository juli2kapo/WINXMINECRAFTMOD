package net.juli2kapo.factoryascent.gametest;

import java.util.List;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
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
            new Test("crusher_doubles_ore", 200, ModGameTests::crusherDoublesOre),
            new Test("ultimate_crusher_quadruples_ore", 200, ModGameTests::ultimateCrusherQuadruplesOre),
            new Test("upgrade_keeps_contents", 40, ModGameTests::upgradeKeepsContents),
            new Test("press_uses_mold", 200, ModGameTests::pressUsesMold),
            new Test("recipe_needs_tier", 200, ModGameTests::recipeNeedsTier),
            new Test("generator_cable_furnace_chain", 400, ModGameTests::generatorCableFurnaceChain),
            new Test("pipe_extracts_between_chests", 100, ModGameTests::pipeExtractsBetweenChests),
            new Test("miner_digs_only_ore", 400, ModGameTests::minerDigsOnlyOre),
            new Test("speed_upgrade_needs_slot", 20, ModGameTests::speedUpgradeNeedsSlot)
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

    private static AbstractMachineBlockEntity place(GameTestHelper h, BlockPos pos, MachineType type, Tier tier) {
        h.setBlock(pos, ModBlocks.machine(type, tier).get());
        return h.getBlockEntity(pos, AbstractMachineBlockEntity.class);
    }

    private static void charge(AbstractMachineBlockEntity be) {
        be.energy().produce(be.energy().capacity());
    }

    private static ItemStack slot(AbstractMachineBlockEntity be, int index) {
        return be.inventory().stack(index);
    }

    private static void expect(GameTestHelper h, ItemStack stack, Item item, int count, String what) {
        h.assertTrue(stack.is(item) && stack.getCount() >= count,
                what + ": expected " + count + "x " + item + " but found " + stack);
    }

    // ---------------------------------------------------------------- tests

    private static void crusherDoublesOre(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.CRUSHER, Tier.BASIC);
        charge(be);
        be.inventory().setStack(0, new ItemStack(Items.RAW_IRON));
        int out = be.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(be, out), ModItems.MATERIALS.get("iron_dust").get(), 2, "basic crusher output"));
    }

    private static void ultimateCrusherQuadruplesOre(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.CRUSHER, Tier.ULTIMATE);
        charge(be);
        be.inventory().setStack(0, new ItemStack(Items.RAW_IRON));
        int out = be.inventory().slots().firstOutput();
        h.succeedWhen(() -> {
            ItemStack s = slot(be, out);
            h.assertTrue(s.is(ModItems.MATERIALS.get("iron_dust").get()) && s.getCount() == 4,
                    "ultimate crusher should give exactly 4 dust, got " + s);
        });
    }

    private static void upgradeKeepsContents(GameTestHelper h) {
        BlockPos pos = new BlockPos(4, 1, 4);
        var be = place(h, pos, MachineType.ELECTRIC_FURNACE, Tier.BASIC);
        be.inventory().setStack(0, new ItemStack(Items.COBBLESTONE, 17));
        be.energy().produce(1234);
        MachineBlock next = ModBlocks.machine(MachineType.ELECTRIC_FURNACE, Tier.REINFORCED).get();
        h.setBlock(pos, next.defaultBlockState());
        var after = h.getBlockEntity(pos, AbstractMachineBlockEntity.class);
        after.onTierChanged();
        h.assertTrue(after == be, "upgrade must keep the same block entity");
        h.assertTrue(after.tier() == Tier.REINFORCED, "tier should now be reinforced, is " + after.tier());
        expect(h, slot(after, 0), Items.COBBLESTONE, 17, "input kept");
        h.assertTrue(after.energy().energy() >= 1234, "energy kept");
        h.succeed();
    }

    private static void pressUsesMold(GameTestHelper h) {
        var be = place(h, new BlockPos(4, 1, 4), MachineType.METAL_PRESS, Tier.BASIC);
        charge(be);
        var s = be.inventory().slots();
        be.inventory().setStack(s.firstInput(), new ItemStack(Items.IRON_INGOT, 2));
        be.inventory().setStack(s.firstMold(), new ItemStack(ModItems.MOLDS.get("gear_mold").get()));
        h.succeedWhen(() -> {
            expect(h, slot(be, s.firstOutput()), ModItems.MATERIALS.get("iron_gear").get(), 1, "press output");
            h.assertTrue(!slot(be, s.firstMold()).isEmpty(), "mould must not be consumed");
        });
    }

    private static void recipeNeedsTier(GameTestHelper h) {
        var basic = place(h, new BlockPos(2, 1, 4), MachineType.ELECTRIC_FURNACE, Tier.BASIC);
        var advanced = place(h, new BlockPos(6, 1, 4), MachineType.ELECTRIC_FURNACE, Tier.ADVANCED);
        charge(basic);
        charge(advanced);
        Item dust = ModItems.MATERIALS.get("titanium_dust").get();
        basic.inventory().setStack(0, new ItemStack(dust));
        advanced.inventory().setStack(0, new ItemStack(dust));
        int out = basic.inventory().slots().firstOutput();
        h.succeedWhen(() -> {
            expect(h, slot(advanced, out), ModItems.MATERIALS.get("titanium_ingot").get(), 1, "advanced furnace");
            h.assertTrue(slot(basic, out).isEmpty(), "basic furnace must not smelt titanium");
            h.assertTrue(basic.status() == AbstractMachineBlockEntity.STATUS_TIER_TOO_LOW, "basic furnace should report tier too low");
        });
    }

    private static void generatorCableFurnaceChain(GameTestHelper h) {
        var gen = place(h, new BlockPos(1, 1, 4), MachineType.COMBUSTION_GENERATOR, Tier.BASIC);
        for (int x = 2; x <= 5; x++) h.setBlock(new BlockPos(x, 1, 4), ModBlocks.POWER_CABLES.get(Tier.BASIC).get());
        var furnace = place(h, new BlockPos(6, 1, 4), MachineType.ELECTRIC_FURNACE, Tier.BASIC);
        gen.inventory().setStack(gen.inventory().slots().firstFuel(), new ItemStack(Items.COAL, 4));
        furnace.inventory().setStack(0, new ItemStack(Items.RAW_IRON, 3));
        int out = furnace.inventory().slots().firstOutput();
        h.succeedWhen(() -> expect(h, slot(furnace, out), Items.IRON_INGOT, 3, "furnace powered through cables"));
    }

    private static void pipeExtractsBetweenChests(GameTestHelper h) {
        BlockPos from = new BlockPos(2, 1, 4), pipePos = new BlockPos(3, 1, 4), to = new BlockPos(5, 1, 4);
        h.setBlock(from, Blocks.CHEST);
        h.setBlock(pipePos, ModBlocks.ITEM_PIPES.get(Tier.BASIC).get());
        h.setBlock(new BlockPos(4, 1, 4), ModBlocks.ITEM_PIPES.get(Tier.BASIC).get());
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
        var miner = (MinerBlockEntity) place(h, minerPos, MachineType.MINER, Tier.BASIC);
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

    private static void speedUpgradeNeedsSlot(GameTestHelper h) {
        var basic = place(h, new BlockPos(2, 1, 4), MachineType.CRUSHER, Tier.BASIC);
        var s = basic.inventory().slots();
        var speed = net.neoforged.neoforge.transfer.item.ItemResource.of(ModItems.SPEED_UPGRADE.get());
        h.assertTrue(basic.isItemValid(s.firstUpgrade(), speed), "basic machines have one upgrade slot");
        h.assertTrue(!basic.isItemValid(s.firstUpgrade() + 1, speed), "second slot must be locked at basic");
        basic.inventory().setStack(s.firstUpgrade(), new ItemStack(ModItems.SPEED_UPGRADE.get()));
        h.assertTrue(Math.abs(basic.speedMultiplier() - 1.5f) < 0.001f, "one speed upgrade = x1.5");
        h.succeed();
    }

}
