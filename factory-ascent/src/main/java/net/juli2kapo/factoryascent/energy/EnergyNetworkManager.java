package net.juli2kapo.factoryascent.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * Owns the cable networks of one level. Networks are built lazily and torn down whenever the
 * cable graph changes; a torn-down network hands its energy back to its cables first, so nothing
 * is lost when cables are added, removed, loaded or unloaded.
 */
public final class EnergyNetworkManager {
    private static final Map<ServerLevel, EnergyNetworkManager> MANAGERS = new WeakHashMap<>();

    private final ServerLevel level;
    private final Map<BlockPos, EnergyNetwork> byCable = new HashMap<>();
    private final Set<EnergyNetwork> networks = new LinkedHashSet<>();
    private final Set<BlockPos> pending = new LinkedHashSet<>();

    private EnergyNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static EnergyNetworkManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, EnergyNetworkManager::new);
    }

    public static void remove(ServerLevel level) {
        MANAGERS.remove(level);
    }

    public static void tickLevel(ServerLevel level) {
        EnergyNetworkManager manager = MANAGERS.get(level);
        if (manager != null) manager.tick();
    }

    private void tick() {
        if (!pending.isEmpty()) {
            List<BlockPos> todo = new ArrayList<>(pending);
            pending.clear();
            for (BlockPos pos : todo) {
                if (!byCable.containsKey(pos)) build(pos);
            }
        }
        for (EnergyNetwork network : List.copyOf(networks)) network.tick();
    }

    /** The network containing this cable, building it on demand. */
    public @Nullable EnergyNetwork networkAt(BlockPos pos) {
        EnergyNetwork net = byCable.get(pos);
        return net != null ? net : build(pos);
    }

    private @Nullable EnergyNetwork build(BlockPos start) {
        if (!level.isLoaded(start) || !(level.getBlockEntity(start) instanceof PowerCableBlockEntity)) return null;
        Set<BlockPos> members = new HashSet<>();
        List<PowerCableBlockEntity> entities = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        members.add(start);
        long energy = 0;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof PowerCableBlockEntity cable)) continue;
            EnergyNetwork old = byCable.get(pos);
            if (old != null) dissolve(old); // merge: returns energy into its cables first
            entities.add(cable);
            energy += cable.stored;
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (!members.contains(next) && level.isLoaded(next)
                        && level.getBlockState(next).getBlock() instanceof PowerCableBlock) {
                    members.add(next);
                    queue.add(next);
                }
            }
        }
        members.removeIf(p -> !(level.getBlockEntity(p) instanceof PowerCableBlockEntity));
        long rate = Long.MAX_VALUE;
        long capacity = 0;
        for (PowerCableBlockEntity cable : entities) {
            if (cable.getBlockState().getBlock() instanceof PowerCableBlock block) {
                rate = Math.min(rate, block.rate());
                capacity += block.rate();
            }
        }
        if (rate == Long.MAX_VALUE) rate = 0;
        EnergyNetwork network = new EnergyNetwork(level, members, energy, rate, Math.max(capacity, EnergyNetwork.CAPACITY_PER_CABLE));
        for (PowerCableBlockEntity cable : entities) {
            cable.stored = 0;
            cable.network = network;
            byCable.put(cable.getBlockPos(), network);
        }
        networks.add(network);
        return network;
    }

    /**
     * Gives every cable its even share of the network's energy and forgets the network; members
     * get rebuilt later. Shares match what {@link PowerCableBlockEntity} saves, so cables in an
     * unloading chunk keep exactly what they wrote to disk and nothing is duplicated.
     */
    public void dissolve(EnergyNetwork network) {
        if (!networks.remove(network)) return;
        long share = network.cables().isEmpty() ? 0 : network.energy() / network.cables().size();
        for (BlockPos pos : network.cables()) {
            byCable.remove(pos);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PowerCableBlockEntity cable) {
                cable.stored = share;
                cable.network = null;
                cable.setChanged();
                pending.add(pos);
            }
        }
    }

    void onCableLoaded(BlockPos pos) {
        pending.add(pos.immutable());
    }

    void onCablePlaced(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            EnergyNetwork net = byCable.get(pos.relative(dir));
            if (net != null) dissolve(net);
        }
        pending.add(pos.immutable());
    }

    void onCableRemoved(BlockPos pos) {
        EnergyNetwork net = byCable.get(pos);
        if (net != null) {
            // The removed cable is already gone, so its share is lost with it (a small, fair cost).
            dissolve(net);
        }
        byCable.remove(pos);
        pending.remove(pos);
    }

    void onCableNeighborChanged(BlockPos pos) {
        EnergyNetwork net = byCable.get(pos);
        if (net != null) net.markEndpointsDirty();
    }
}
