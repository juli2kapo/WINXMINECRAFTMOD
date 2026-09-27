package net.juli2kapo.factoryascent.storagenet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * Owns the storage networks of one level. A network is built lazily (breadth-first over touching
 * storage blocks, never crossing into unloaded chunks) the first time something asks for it, and
 * forgotten as soon as a node is placed, removed, loaded or unloaded next to it.
 */
public final class StorageNetManager {
    private static final Map<ServerLevel, StorageNetManager> MANAGERS = new WeakHashMap<>();

    private final ServerLevel level;
    private final Map<BlockPos, StorageNet> byPos = new HashMap<>();

    private StorageNetManager(ServerLevel level) {
        this.level = level;
    }

    public static StorageNetManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, StorageNetManager::new);
    }

    public static void remove(ServerLevel level) {
        MANAGERS.remove(level);
    }

    public @Nullable StorageNet networkAt(BlockPos pos) {
        StorageNet net = byPos.get(pos);
        return net != null ? net : build(pos.immutable());
    }

    private boolean isNode(BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof StorageNodeBlockEntity;
    }

    private List<BlockPos> bfs(BlockPos start) {
        Set<BlockPos> seen = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!seen.contains(next) && isNode(next)) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    private @Nullable StorageNet build(BlockPos start) {
        if (!isNode(start)) return null;
        List<BlockPos> order = bfs(start);
        List<StorageControllerBlockEntity> controllers = new ArrayList<>();
        for (BlockPos p : order) {
            if (level.getBlockEntity(p) instanceof StorageControllerBlockEntity c) controllers.add(c);
        }
        // Drive order starts at the controller so it does not depend on who asked first.
        if (controllers.size() == 1 && !controllers.get(0).getBlockPos().equals(start)) {
            order = bfs(controllers.get(0).getBlockPos());
        }
        List<StorageDriveBlockEntity> drives = new ArrayList<>();
        int devices = 0;
        for (BlockPos p : order) {
            var be = level.getBlockEntity(p);
            if (be instanceof StorageDriveBlockEntity d) drives.add(d);
            if (be instanceof StorageNodeBlockEntity && !(be instanceof StorageCableBlockEntity)
                    && !(be instanceof StorageControllerBlockEntity)) {
                devices++;
            }
        }
        StorageNet net = new StorageNet(new LinkedHashSet<>(order), List.copyOf(controllers), List.copyOf(drives), devices);
        for (BlockPos p : order) {
            StorageNet old = byPos.put(p, net);
            if (old != null && old != net) invalidate(old);
        }
        return net;
    }

    private void invalidate(StorageNet net) {
        net.valid = false;
        for (BlockPos p : net.members()) {
            if (byPos.get(p) == net) byPos.remove(p);
        }
    }

    /** A node appeared, disappeared, loaded or unloaded here: forget the networks around it. */
    public void onNodeChanged(BlockPos pos) {
        StorageNet net = byPos.get(pos);
        if (net != null) invalidate(net);
        for (Direction dir : Direction.values()) {
            StorageNet n = byPos.get(pos.relative(dir));
            if (n != null) invalidate(n);
        }
    }
}
