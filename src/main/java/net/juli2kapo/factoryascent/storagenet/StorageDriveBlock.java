package net.juli2kapo.factoryascent.storagenet;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The drive block: besides facing/online it shows what sits in each of its four bays
 * ({@code cell0..cell3}: 0 empty, 1 a 1k cell, 2 a bigger cell), so the model can draw the cartridges.
 */
public class StorageDriveBlock extends StorageDeviceBlock {
    public static final IntegerProperty[] CELLS = {
            IntegerProperty.create("cell0", 0, 2), IntegerProperty.create("cell1", 0, 2),
            IntegerProperty.create("cell2", 0, 2), IntegerProperty.create("cell3", 0, 2)};
    private static final MapCodec<StorageDriveBlock> CODEC = simpleCodec(StorageDriveBlock::new);

    public StorageDriveBlock(Properties properties) {
        super(Kind.DRIVE, properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CELLS);
    }

    /** The bay value shown for a cell stack. */
    static int shown(ItemStack cell) {
        if (!(cell.getItem() instanceof StorageCellItem item)) return 0;
        return item.capacity() > 1_024 ? 2 : 1;
    }
}
