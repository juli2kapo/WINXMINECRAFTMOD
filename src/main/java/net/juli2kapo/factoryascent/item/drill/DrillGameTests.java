package net.juli2kapo.factoryascent.item.drill;

import net.juli2kapo.factoryascent.item.ElectricDrillItem;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.juli2kapo.factoryascent.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** GameTests for the Electric Drill's mining modes (registered in ModGameTests.TESTS). */
public final class DrillGameTests {
    private DrillGameTests() {}

    /** A survival player holding a full drill in {@code mode}. */
    @SuppressWarnings("removal")
    private static ServerPlayer driller(GameTestHelper h, DrillMode mode) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        ItemStack drill = new ItemStack(ModItems.ELECTRIC_DRILL.get());
        drill.set(ModComponents.ENERGY.get(), ElectricDrillItem.CAPACITY);
        ElectricDrillItem.setMode(drill, mode);
        player.setItemInHand(InteractionHand.MAIN_HAND, drill);
        return player;
    }

    private static int used(ServerPlayer player) {
        return ElectricDrillItem.CAPACITY - ElectricDrillItem.energy(player.getMainHandItem());
    }

    private static void wall(GameTestHelper h, BlockPos centre, Block block) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) h.setBlock(centre.offset(dx, dy, 0), block);
        }
    }

    /** Area mode on the north face of a 3x3 stone wall breaks all 9 blocks for 9 x 100 FE. */
    public static void areaBreaksThreeByThree(GameTestHelper h) {
        BlockPos centre = new BlockPos(4, 3, 4);
        wall(h, centre, Blocks.STONE);
        h.setBlock(centre.north(), Blocks.STONE); // behind the plane: must survive
        ServerPlayer player = driller(h, DrillMode.AREA);
        h.assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(DrillComponents.MODE) != null,
                "factoryascent:drill_mode should be registered");
        h.assertTrue(ElectricDrillItem.drillBlock(player, h.absolutePos(centre), Direction.NORTH), "centre should break");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) h.assertBlockPresent(Blocks.AIR, centre.offset(dx, dy, 0));
        }
        h.assertBlockPresent(Blocks.STONE, centre.north());
        h.assertValueEqual(used(player), 9 * ElectricDrillItem.COST_PER_BLOCK, "FE used by a 3x3 dig");
        player.discard();
        h.succeed();
    }

    /** Area mode leaves bedrock (and blocks much harder than the centre, like obsidian) in place. */
    public static void areaSkipsBedrock(GameTestHelper h) {
        BlockPos centre = new BlockPos(4, 3, 4);
        wall(h, centre, Blocks.STONE);
        h.setBlock(centre.offset(1, 1, 0), Blocks.BEDROCK);
        h.setBlock(centre.offset(-1, -1, 0), Blocks.OBSIDIAN);
        ServerPlayer player = driller(h, DrillMode.AREA);
        ElectricDrillItem.drillBlock(player, h.absolutePos(centre), Direction.SOUTH);
        h.assertBlockPresent(Blocks.BEDROCK, centre.offset(1, 1, 0));
        h.assertBlockPresent(Blocks.OBSIDIAN, centre.offset(-1, -1, 0));
        h.assertBlockPresent(Blocks.AIR, centre.offset(-1, 1, 0));
        h.assertValueEqual(used(player), 7 * ElectricDrillItem.COST_PER_BLOCK, "FE used around bedrock and obsidian");
        player.discard();
        h.succeed();
    }

    /**
     * Vein mode takes a connected cluster of 5 iron ore (one link diagonal), but not an ore 2 blocks away, nor the
     * bedrock and stone touching the cluster.
     */
    public static void veinMinesCluster(GameTestHelper h) {
        BlockPos start = new BlockPos(2, 2, 4);
        BlockPos[] cluster = {start, start.east(), start.east().above(), start.east(2), start.east(3).above()};
        for (BlockPos pos : cluster) h.setBlock(pos, Blocks.IRON_ORE);
        BlockPos lonely = start.east(3).below().south(2); // 2 blocks from the nearest ore of the cluster
        h.setBlock(lonely, Blocks.IRON_ORE);
        h.setBlock(start.above(), Blocks.BEDROCK);
        h.setBlock(start.north(), Blocks.STONE);
        ServerPlayer player = driller(h, DrillMode.VEIN);
        ElectricDrillItem.drillBlock(player, h.absolutePos(start), Direction.UP);
        for (BlockPos pos : cluster) h.assertBlockPresent(Blocks.AIR, pos);
        h.assertBlockPresent(Blocks.IRON_ORE, lonely);
        h.assertBlockPresent(Blocks.BEDROCK, start.above());
        h.assertBlockPresent(Blocks.STONE, start.north());
        h.assertValueEqual(used(player), 5 * ElectricDrillItem.COST_PER_BLOCK, "FE used by a 5-ore vein");
        player.discard();
        h.succeed();
    }
}
