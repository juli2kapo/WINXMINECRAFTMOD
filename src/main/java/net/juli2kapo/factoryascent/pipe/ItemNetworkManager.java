package net.juli2kapo.factoryascent.pipe;

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

/** Builds item networks on demand and throws them away whenever a pipe or its neighbours change. */
public final class ItemNetworkManager {
    private static final Map<ServerLevel, ItemNetworkManager> MANAGERS = new WeakHashMap<>();

    private final ServerLevel level;
    private final Map<BlockPos, ItemNetwork> byPipe = new HashMap<>();

    private ItemNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static ItemNetworkManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, ItemNetworkManager::new);
    }

    public static void remove(ServerLevel level) {
        MANAGERS.remove(level);
    }

    public @Nullable ItemNetwork networkAt(BlockPos pos) {
        ItemNetwork net = byPipe.get(pos);
        return net != null ? net : build(pos);
    }

    private @Nullable ItemNetwork build(BlockPos start) {
        if (!level.isLoaded(start) || !(level.getBlockState(start).getBlock() instanceof ItemPipeBlock)) return null;
        Set<BlockPos> members = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        members.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!members.contains(next) && level.isLoaded(next)
                        && level.getBlockState(next).getBlock() instanceof ItemPipeBlock) {
                    members.add(next);
                    queue.add(next);
                }
            }
        }
        ItemNetwork network = new ItemNetwork(level, members);
        for (BlockPos pos : members) byPipe.put(pos, network);
        return network;
    }

    /** Forget the network containing this pipe (it is rebuilt the next time it is used). */
    public void invalidateAt(BlockPos pos) {
        ItemNetwork net = byPipe.get(pos);
        if (net != null) {
            for (BlockPos p : net.pipes()) byPipe.remove(p);
        }
    }

    /** A pipe was placed, removed, loaded or unloaded: networks around it are stale. */
    public void onPipeChanged(BlockPos pos) {
        invalidateAt(pos);
        for (Direction dir : Direction.values()) invalidateAt(pos.relative(dir));
    }
}
