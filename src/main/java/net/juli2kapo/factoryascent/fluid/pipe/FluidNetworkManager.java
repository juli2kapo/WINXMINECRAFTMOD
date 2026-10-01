package net.juli2kapo.factoryascent.fluid.pipe;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/** Builds fluid networks on demand and drops them whenever a pipe or its neighbours change. */
public final class FluidNetworkManager {
    private static final Map<ServerLevel, FluidNetworkManager> MANAGERS = new WeakHashMap<>();

    private final ServerLevel level;
    private final Map<BlockPos, FluidNetwork> byPipe = new HashMap<>();

    private FluidNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static FluidNetworkManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, FluidNetworkManager::new);
    }

    public static void remove(ServerLevel level) {
        MANAGERS.remove(level);
    }

    public @Nullable FluidNetwork networkAt(BlockPos pos) {
        FluidNetwork net = byPipe.get(pos);
        return net != null ? net : build(pos);
    }

    private @Nullable FluidNetwork build(BlockPos start) {
        if (!level.isLoaded(start) || !(level.getBlockState(start).getBlock() instanceof FluidPipeBlock)) return null;
        Set<BlockPos> members = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        members.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!members.contains(next) && level.isLoaded(next) && level.getBlockState(next).getBlock() instanceof FluidPipeBlock) {
                    members.add(next);
                    queue.add(next);
                }
            }
        }
        FluidNetwork network = new FluidNetwork(level, members);
        for (BlockPos pos : members) byPipe.put(pos, network);
        return network;
    }

    public void invalidateAt(BlockPos pos) {
        FluidNetwork net = byPipe.get(pos);
        if (net != null) {
            for (BlockPos p : net.pipes()) byPipe.remove(p);
        }
    }

    public void onPipeChanged(BlockPos pos) {
        invalidateAt(pos);
        for (Direction dir : Direction.values()) invalidateAt(pos.relative(dir));
    }
}
