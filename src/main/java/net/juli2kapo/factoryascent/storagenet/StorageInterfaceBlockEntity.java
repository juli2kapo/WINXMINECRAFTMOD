package net.juli2kapo.factoryascent.storagenet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Exposes the whole network as an item handler on every side, for pipes, hoppers and machines. */
public class StorageInterfaceBlockEntity extends StorageNodeBlockEntity {
    private final NetworkItemHandler handler = new NetworkItemHandler(this::network);

    public StorageInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(StorageContent.INTERFACE_BE.get(), pos, state);
    }

    public ResourceHandler<ItemResource> itemHandler() {
        return handler;
    }
}
