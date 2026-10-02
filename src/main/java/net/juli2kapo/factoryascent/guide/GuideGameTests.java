package net.juli2kapo.factoryascent.guide;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.nuclear.ReactorStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The guide must not drift from the code: every layout of {@link GuideMultiblocks} is built in the
 * arena and the real structure check has to accept it; its bill of materials has to match what was
 * placed; and every entry's advancement, items and multiblock must exist.
 */
public final class GuideGameTests {
    private GuideGameTests() {}

    /** A test that builds layout {@code index} of structure {@code id} and checks it. */
    public static Consumer<GameTestHelper> builds(String id, int index) {
        return h -> buildAndCheck(h, GuideMultiblocks.variants(id).get(index));
    }

    private static void buildAndCheck(GameTestHelper h, GuideMultiblocks.Layout layout) {
        // the arena is 9x7x9 with a floor at y=0: tall layouts start on the floor itself
        BlockPos origin = new BlockPos((9 - layout.width) / 2, layout.height <= 6 ? 1 : 0, (9 - layout.depth) / 2);
        for (GuideMultiblocks.Cell c : layout.cells()) h.setBlock(origin.offset(c.x(), c.y(), c.z()), c.state());
        for (int y = 0; y < layout.height; y++) {
            for (int z = 0; z < layout.depth; z++) {
                for (int x = 0; x < layout.width; x++) {
                    if (layout.mustBeAir(x, y, z)) h.setBlock(origin.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR);
                }
            }
        }
        BlockPos controller = h.absolutePos(origin.offset(layout.controller));
        // bill of materials = what is standing in the world
        Map<Item, Integer> counted = new HashMap<>();
        for (GuideMultiblocks.Cell c : layout.cells()) {
            BlockState s = h.getLevel().getBlockState(h.absolutePos(origin.offset(c.x(), c.y(), c.z())));
            h.assertTrue(s.getBlock() == c.state().getBlock(), layout.id + ": " + s + " at " + c + " was not kept");
            if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) continue;
            counted.merge(s.getBlock().asItem(), 1, Integer::sum);
        }
        h.assertTrue(counted.equals(layout.materials()), layout.id + ": BOM " + layout.materials() + " vs world " + counted);
        for (Item item : layout.materials().keySet()) h.assertTrue(item != Items.AIR, layout.id + ": a block without an item");
        if (layout.id.startsWith("fission_reactor")) {
            ReactorStructure r = ReactorStructure.scan(h.getLevel(), controller, Direction.NORTH);
            h.assertTrue(r.valid(), layout.id + ": reactor scan says " + r.error() + " at " + r.bad());
            int channels = layout.materials().getOrDefault(GuideEntries.item("reactor_fuel_channel"), 0);
            int rods = layout.materials().getOrDefault(GuideEntries.item("reactor_control_rod"), 0);
            h.assertTrue(r.channels() == channels && r.controlRods() == rods, layout.id + ": channel/rod counts differ");
            h.assertTrue(rods * 4 >= channels, layout.id + ": the recommended layout should have full control authority");
        }
        // block entities (derrick, turbine) re-check their structure on their own ticks
        h.runAfterDelay(50, () -> {
            h.assertTrue(layout.validator.formed(h.getLevel(), controller), layout.id + ": the real structure check rejects the guide's layout");
            h.succeed();
        });
    }

    /** Every entry's advancement, items, icon and multiblock exist. */
    public static void entriesResolve(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        for (GuideEntries.Entry e : GuideEntries.all()) {
            if (e.advancement() != null) {
                h.assertTrue(server.getAdvancements().get(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, e.advancement())) != null,
                        e.id() + ": no advancement " + e.advancement());
            }
            h.assertTrue(GuideEntries.item(e.icon()) != Items.AIR, e.id() + ": no icon item " + e.icon());
            for (String item : e.items()) h.assertTrue(GuideEntries.item(item) != Items.AIR, e.id() + ": no item " + item);
            if (e.multiblock() != null) h.assertTrue(!GuideMultiblocks.variants(e.multiblock()).isEmpty(), e.id() + ": no multiblock");
        }
        for (var entry : GuideMultiblocks.all().entrySet()) {
            boolean shown = GuideEntries.all().stream().anyMatch(e -> entry.getKey().equals(e.multiblock()));
            h.assertTrue(shown, "multiblock " + entry.getKey() + " has no entry");
        }
        h.assertTrue(GuideEntries.forItem(GuideEntries.item("reactor_controller")) == GuideEntries.get("fission_reactor"),
                "machine screens must find the reactor's entry");
        h.succeed();
    }
}
