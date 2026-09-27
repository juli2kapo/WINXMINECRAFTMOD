package net.juli2kapo.factoryascent.storagenet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Base of every storage network block entity. Nodes hold no network state; they only tell the
 * {@link StorageNetManager} when they appear or disappear (load, unload) so networks get rebuilt.
 */
public abstract class StorageNodeBlockEntity extends BlockEntity {
    protected StorageNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The network this node belongs to (server only; built on demand). */
    public @Nullable StorageNet network() {
        return level instanceof ServerLevel server ? StorageNetManager.get(server).networkAt(worldPosition) : null;
    }

    /** Devices refresh their "online" light once a second; the controller overrides this with its power logic. */
    public void serverTick(ServerLevel level) {
        if ((level.getGameTime() + worldPosition.hashCode()) % 20 != 0) return;
        StorageNet net = network();
        setOnline(level, net != null && net.isOnline());
    }

    protected void setOnline(ServerLevel level, boolean online) {
        BlockState state = getBlockState();
        if (state.hasProperty(StorageDeviceBlock.ONLINE) && state.getValue(StorageDeviceBlock.ONLINE) != online) {
            level.setBlock(worldPosition, state.setValue(StorageDeviceBlock.ONLINE, online), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) StorageNetManager.get(server).onNodeChanged(worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) StorageNetManager.get(server).onNodeChanged(worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) StorageNetManager.get(server).onNodeChanged(worldPosition);
    }
}
