package net.juli2kapo.factoryascent.fluid.pipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.juli2kapo.factoryascent.pipe.PipeConnection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A connected set of fluid pipes and the tanks/machines they deliver to. Like item pipes, fluid
 * moves instantly from a source to the destinations (round-robin); how much moves is limited by
 * the pipes it enters through (their tier's rate per tick).
 */
public final class FluidNetwork {
    public record Endpoint(BlockPos target, BlockPos pipe, Direction dir,
                           BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> cache) {}

    private final ServerLevel level;
    private final Set<BlockPos> pipes;
    private final List<Endpoint> destinations = new ArrayList<>();
    private int roundRobin;
    private int minRate = Integer.MAX_VALUE;
    private @Nullable Fluid shown;
    private long shownAt = Long.MIN_VALUE / 2;

    FluidNetwork(ServerLevel level, Set<BlockPos> pipes) {
        this.level = level;
        this.pipes = pipes;
        for (BlockPos pipe : pipes) {
            var state = level.getBlockState(pipe);
            if (!(state.getBlock() instanceof FluidPipeBlock block)) continue;
            minRate = Math.min(minRate, block.rate());
            for (Direction dir : Direction.values()) {
                if (state.getValue(FluidPipeBlock.PROPERTIES.get(dir)) != PipeConnection.CONNECTED) continue;
                BlockPos other = pipe.relative(dir);
                if (pipes.contains(other)) continue;
                if (level.isLoaded(other) && level.getBlockState(other).getBlock() instanceof FluidPipeBlock) continue;
                destinations.add(new Endpoint(other, pipe, dir,
                        BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, other, dir.getOpposite())));
            }
        }
        if (minRate == Integer.MAX_VALUE) minRate = 0;
    }

    public Set<BlockPos> pipes() {
        return pipes;
    }

    public int destinationCount() {
        return destinations.size();
    }

    public List<Endpoint> destinations() {
        return destinations;
    }

    /** The slowest pipe's rate: a network is only as fast as its weakest pipe. */
    public int rate() {
        return minRate;
    }

    /**
     * Delivers up to {@code amount} of the fluid to the network's destinations, round-robin, never
     * back into {@code source}. Returns how much was accepted (inside the transaction).
     */
    public int route(FluidResource resource, int amount, @Nullable BlockPos source, TransactionContext tx) {
        int n = destinations.size();
        if (n == 0 || amount <= 0) return 0;
        int start = roundRobin = (roundRobin + 1) % n;
        int moved = 0;
        // split evenly first, then let whoever has room take the rest
        for (int pass = 0; pass < 2 && moved < amount; pass++) {
            for (int k = 0; k < n && moved < amount; k++) {
                Endpoint end = destinations.get((start + k) % n);
                if (end.target().equals(source)) continue;
                ResourceHandler<FluidResource> handler = end.cache().getCapability();
                if (handler == null) continue;
                int share = pass == 0 ? Math.max(1, (amount - moved) / Math.max(1, n - k)) : amount - moved;
                moved += handler.insert(resource, Math.min(share, amount - moved), tx);
            }
        }
        return moved;
    }

    /** Shows the fluid in every pipe of the network (the pipes sync it to clients only when it changes). */
    void showFlow(Fluid fluid) {
        long now = level.getGameTime();
        if (fluid == shown && now - shownAt < 10) return;
        shown = fluid;
        shownAt = now;
        for (BlockPos p : pipes) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof FluidPipeBlockEntity be) be.showFlow(fluid, now);
        }
    }
}
