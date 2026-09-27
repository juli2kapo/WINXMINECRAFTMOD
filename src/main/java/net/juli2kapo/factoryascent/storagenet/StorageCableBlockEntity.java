package net.juli2kapo.factoryascent.storagenet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Cables hold nothing; the block entity only reports load/unload to the network manager. */
public class StorageCableBlockEntity extends StorageNodeBlockEntity {
    public StorageCableBlockEntity(BlockPos pos, BlockState state) {
        super(StorageContent.CABLE_BE.get(), pos, state);
    }
}
