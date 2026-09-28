package net.juli2kapo.factoryascent.pipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/** A connected set of item pipes and the inventories they deliver to. Items move instantly. */
public final class ItemNetwork {
    /** An inventory touching the network: where it is, and the pipe face that reaches it. */
    public record Endpoint(BlockPos inventory, BlockPos pipe, Direction dir,
                           BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> cache) {}

    private final Set<BlockPos> pipes;
    private final List<Endpoint> destinations = new ArrayList<>();
    private int roundRobin;

    ItemNetwork(ServerLevel level, Set<BlockPos> pipes) {
        this.pipes = pipes;
        for (BlockPos pipe : pipes) {
            var state = level.getBlockState(pipe);
            if (!(state.getBlock() instanceof ItemPipeBlock)) continue;
            for (Direction dir : Direction.values()) {
                if (state.getValue(ItemPipeBlock.PROPERTIES.get(dir)) != PipeConnection.CONNECTED) continue;
                BlockPos other = pipe.relative(dir);
                if (pipes.contains(other)) continue;
                if (level.isLoaded(other) && level.getBlockState(other).getBlock() instanceof ItemPipeBlock) continue;
                destinations.add(new Endpoint(other, pipe, dir,
                        BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, other, dir.getOpposite())));
            }
        }
    }

    public Set<BlockPos> pipes() {
        return pipes;
    }

    /** How many pipe faces deliver into an inventory. */
    public int destinationCount() {
        return destinations.size();
    }

    /**
     * Delivers up to {@code amount} of the resource to the network's inventories, round-robin,
     * never back into {@code source}. Returns how many were accepted (inside the transaction).
     */
    public int route(ItemResource resource, int amount, @Nullable BlockPos source, TransactionContext tx) {
        int n = destinations.size();
        if (n == 0 || amount <= 0) return 0;
        int start = roundRobin = (roundRobin + 1) % n;
        int moved = 0;
        for (int k = 0; k < n && moved < amount; k++) {
            Endpoint end = destinations.get((start + k) % n);
            if (end.inventory().equals(source)) continue;
            ResourceHandler<ItemResource> handler = end.cache().getCapability();
            if (handler == null) continue;
            moved += handler.insert(resource, amount - moved, tx);
        }
        return moved;
    }
}
