package net.juli2kapo.factoryascent.ui;

import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.item.ElectricDrillItem;
import net.juli2kapo.factoryascent.item.drill.DrillMode;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineMenu;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlock;
import net.juli2kapo.factoryascent.pipe.PipeConnection;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.juli2kapo.factoryascent.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** GameTests for the screens' server-side actions (registered in ModGameTests.TESTS). */
public final class UiGameTests {
    private UiGameTests() {}

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h, BlockPos near) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos at = h.absolutePos(near);
        p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }

    /**
     * The pipe screen's face switch: insert/extract on a face touching a chest, refused on a face
     * with nothing there, and refused from far away.
     */
    public static void pipeSideConfig(GameTestHelper h) {
        BlockPos chest = new BlockPos(2, 2, 4), pipeRel = new BlockPos(3, 2, 4);
        h.setBlock(chest, Blocks.CHEST);
        h.setBlock(pipeRel, ModBlocks.ITEM_PIPES.get(Tier.LV).get());
        BlockPos pipe = h.absolutePos(pipeRel);
        ServerPlayer p = player(h, new BlockPos(4, 2, 2));
        int west = Direction.WEST.get3DDataValue(), east = Direction.EAST.get3DDataValue();
        h.assertBlockProperty(pipeRel, ItemPipeBlock.PROPERTIES.get(Direction.WEST), PipeConnection.CONNECTED);
        h.assertTrue(NetworkScreens.setPipeFace(p, pipe, west, ScreenPayloads.PipeAction.EXTRACT), "extract on the chest face");
        h.assertBlockProperty(pipeRel, ItemPipeBlock.PROPERTIES.get(Direction.WEST), PipeConnection.EXTRACT);
        h.assertTrue(NetworkScreens.setPipeFace(p, pipe, west, ScreenPayloads.PipeAction.EXTRACT), "setting it again is fine");
        h.assertTrue(NetworkScreens.setPipeFace(p, pipe, west, ScreenPayloads.PipeAction.INSERT), "back to insert");
        h.assertBlockProperty(pipeRel, ItemPipeBlock.PROPERTIES.get(Direction.WEST), PipeConnection.CONNECTED);
        h.assertFalse(NetworkScreens.setPipeFace(p, pipe, east, ScreenPayloads.PipeAction.EXTRACT), "nothing to extract from in the east");
        h.assertBlockProperty(pipeRel, ItemPipeBlock.PROPERTIES.get(Direction.EAST), PipeConnection.NONE);
        h.assertFalse(NetworkScreens.setPipeFace(p, pipe, 9, ScreenPayloads.PipeAction.EXTRACT), "bad side index");
        h.assertFalse(NetworkScreens.setPipeFace(p, pipe, west, 5), "bad mode");
        var view = NetworkScreens.pipeView(h.getLevel(), pipe, false);
        h.assertTrue(view.kinds().get(west) == ScreenPayloads.SIDE_INVENTORY && view.kinds().get(east) == ScreenPayloads.SIDE_NOTHING,
                "the view must tell inventory faces apart, got " + view.kinds());
        BlockPos far = pipe.offset(40, 0, 0);
        p.snapTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        h.assertFalse(NetworkScreens.setPipeFace(p, pipe, west, ScreenPayloads.PipeAction.EXTRACT), "too far away");
        h.assertBlockProperty(pipeRel, ItemPipeBlock.PROPERTIES.get(Direction.WEST), PipeConnection.CONNECTED);
        p.discard();
        h.succeed();
    }

    /** The drill screen's mode buttons, only for a drill really held in that hand. */
    public static void drillModeScreen(GameTestHelper h) {
        ServerPlayer p = player(h, new BlockPos(2, 2, 2));
        ItemStack drill = new ItemStack(ModItems.ELECTRIC_DRILL.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, drill);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
        h.assertTrue(ElectricDrillItem.handleModeAction(p, 0, DrillMode.VEIN.ordinal()), "picking vein mode");
        h.assertTrue(ElectricDrillItem.mode(p.getMainHandItem()) == DrillMode.VEIN, "the drill must be in vein mode");
        h.assertFalse(ElectricDrillItem.handleModeAction(p, 0, 99), "bad mode index");
        h.assertFalse(ElectricDrillItem.handleModeAction(p, 1, DrillMode.AREA.ordinal()), "a stick is not a drill");
        h.assertTrue(ElectricDrillItem.mode(p.getMainHandItem()) == DrillMode.VEIN, "the mode must be unchanged");
        p.discard();
        h.succeed();
    }

    /** The machine screen's Sides panel turns an Energy Cell's output face, horizontally only. */
    public static void machineSidesPanel(GameTestHelper h) {
        BlockPos rel = new BlockPos(4, 2, 4);
        h.setBlock(rel, ModBlocks.machine(MachineType.ENERGY_CELL).get());
        AbstractMachineBlockEntity cell = h.getBlockEntity(rel, AbstractMachineBlockEntity.class);
        ServerPlayer p = player(h, new BlockPos(2, 2, 2));
        MachineMenu menu = new MachineMenu(1, p.getInventory(), cell);
        h.assertTrue(menu.clickMenuButton(p, MachineMenu.BUTTON_FACE + Direction.EAST.get2DDataValue()), "turning the output east");
        h.assertBlockProperty(rel, MachineBlock.FACING, Direction.EAST);
        h.assertTrue(menu.clickMenuButton(p, MachineMenu.BUTTON_FACE + Direction.SOUTH.get2DDataValue()), "turning the output south");
        h.assertBlockProperty(rel, MachineBlock.FACING, Direction.SOUTH);
        h.assertFalse(menu.clickMenuButton(p, MachineMenu.BUTTON_FACE + 4), "no fifth face");
        h.assertFalse(MachineBlock.setFacing(h.getLevel(), h.absolutePos(rel), Direction.UP), "machines only face sideways");
        p.discard();
        h.succeed();
    }
}
