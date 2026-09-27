package net.juli2kapo.factoryascent.pipe;

import net.juli2kapo.factoryascent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Pipes hold no items. Inserting into a pipe routes straight into the network; a pipe with
 * extracting faces periodically pulls from those inventories and routes what it pulled.
 */
public class ItemPipeBlockEntity extends BlockEntity {
    public static final int EXTRACT_INTERVAL = 10;

    @SuppressWarnings("unchecked")
    private final ResourceHandler<ItemResource>[] faces = new ResourceHandler[6];

    public ItemPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ITEM_PIPE.get(), pos, state);
    }

    private @Nullable ItemNetwork network() {
        return level instanceof ServerLevel server ? ItemNetworkManager.get(server).networkAt(worldPosition) : null;
    }

    /** The handler seen by whatever is on {@code side}; items from it are never routed back to it. */
    public @Nullable ResourceHandler<ItemResource> itemHandler(@Nullable Direction side) {
        if (side == null) return null;
        int i = side.ordinal();
        if (faces[i] == null) faces[i] = new FaceHandler(worldPosition.relative(side));
        return faces[i];
    }

    public void serverTick(ServerLevel level) {
        if ((level.getGameTime() + worldPosition.asLong()) % EXTRACT_INTERVAL != 0) return;
        if (!(getBlockState().getBlock() instanceof ItemPipeBlock pipe)) return;
        ItemNetwork network = network();
        if (network == null) return;
        for (Direction dir : Direction.values()) {
            if (getBlockState().getValue(ItemPipeBlock.PROPERTIES.get(dir)) != PipeConnection.EXTRACT) continue;
            BlockPos sourcePos = worldPosition.relative(dir);
            ResourceHandler<ItemResource> source = level.getCapability(Capabilities.Item.BLOCK, sourcePos, dir.getOpposite());
            if (source == null) continue;
            pull(network, source, sourcePos, pipe.rate());
        }
    }

    private static void pull(ItemNetwork network, ResourceHandler<ItemResource> source, BlockPos sourcePos, int budget) {
        for (int slot = 0; slot < source.size() && budget > 0; slot++) {
            ItemResource resource = source.getResource(slot);
            int available = source.getAmountAsInt(slot);
            if (resource.isEmpty() || available <= 0) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int routed = network.route(resource, Math.min(available, budget), sourcePos, tx);
                if (routed <= 0) continue;
                int taken = source.extract(slot, resource, routed, tx);
                if (taken == routed) {
                    tx.commit();
                    budget -= taken;
                }
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) ItemNetworkManager.get(server).onPipeChanged(worldPosition);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) ItemNetworkManager.get(server).onPipeChanged(worldPosition);
    }

    /** Insert-only view of the network for one face. */
    private final class FaceHandler implements ResourceHandler<ItemResource> {
        private final BlockPos source;

        FaceHandler(BlockPos source) {
            this.source = source;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return true;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            ItemNetwork net = network();
            return net == null ? 0 : net.route(resource, amount, source, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }
}
