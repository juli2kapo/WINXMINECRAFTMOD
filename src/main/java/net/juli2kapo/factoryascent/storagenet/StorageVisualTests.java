package net.juli2kapo.factoryascent.storagenet;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** GameTests for what the storage blocks show: the drive's bays mirror the cells inside. */
public final class StorageVisualTests {
    private StorageVisualTests() {}

    public static void driveShowsCells(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, StorageContent.DRIVE.get());
        StorageDriveBlockEntity drive = h.getBlockEntity(pos, StorageDriveBlockEntity.class);
        drive.setCell(0, new ItemStack(StorageContent.CELL_1K.get()));
        drive.setCell(2, new ItemStack(StorageContent.CELL_4K.get()));
        BlockState state = h.getBlockState(pos);
        h.assertTrue(state.getValue(StorageDriveBlock.CELLS[0]) == 1, "bay 0 shows a 1k cell");
        h.assertTrue(state.getValue(StorageDriveBlock.CELLS[1]) == 0, "bay 1 is empty");
        h.assertTrue(state.getValue(StorageDriveBlock.CELLS[2]) == 2, "bay 2 shows a 4k cell");
        drive.setCell(0, ItemStack.EMPTY);
        h.assertTrue(h.getBlockState(pos).getValue(StorageDriveBlock.CELLS[0]) == 0, "bay 0 empties when the cell is taken out");
        h.succeed();
    }
}
