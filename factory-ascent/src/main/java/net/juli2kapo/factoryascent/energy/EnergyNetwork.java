package net.juli2kapo.factoryascent.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A connected group of cables sharing one buffer. Generators insert through any cable; every tick
 * the network splits its buffer evenly across everything that touches it and accepts energy.
 */
public final class EnergyNetwork {
    public static final long CAPACITY_PER_CABLE = 2_000;

    private final ServerLevel level;
    private final Set<BlockPos> cables;
    private final List<BlockCapabilityCache<EnergyHandler, @Nullable Direction>> endpoints = new ArrayList<>();
    private boolean endpointsDirty = true;
    private long energy;
    private final long rate;
    private final long capacity;
    private int roundRobin;
    private final Journal journal = new Journal();

    EnergyNetwork(ServerLevel level, Set<BlockPos> cables, long energy, long rate, long capacity) {
        this.level = level;
        this.cables = cables;
        this.rate = rate;
        this.capacity = capacity;
        this.energy = Math.min(energy, capacity);
    }

    /** FE per tick this network can deliver: the rate of its slowest cable. */
    public long rate() {
        return rate;
    }

    public Set<BlockPos> cables() {
        return cables;
    }

    public long energy() {
        return energy;
    }

    public long capacity() {
        return capacity;
    }

    void markEndpointsDirty() {
        endpointsDirty = true;
    }

    /** This cable's even share of the buffer, used when saving. */
    long shareFor(PowerCableBlockEntity cable) {
        return cables.isEmpty() ? 0 : energy / cables.size();
    }

    int insert(int amount, TransactionContext transaction) {
        long accepted = Math.min(amount, capacity() - energy);
        if (accepted <= 0) return 0;
        journal.updateSnapshots(transaction);
        energy += accepted;
        return (int) accepted;
    }

    private void rebuildEndpoints() {
        endpoints.clear();
        for (BlockPos cable : cables) {
            for (Direction dir : Direction.values()) {
                BlockPos other = cable.relative(dir);
                if (cables.contains(other)) continue;
                if (level.isLoaded(other) && level.getBlockState(other).getBlock() instanceof PowerCableBlock) continue;
                endpoints.add(BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, other, dir.getOpposite()));
            }
        }
        endpointsDirty = false;
    }

    void tick() {
        if (endpointsDirty) rebuildEndpoints();
        if (energy <= 0 || endpoints.isEmpty()) return;

        List<EnergyHandler> targets = new ArrayList<>(endpoints.size());
        for (var cache : endpoints) {
            EnergyHandler h = cache.getCapability();
            if (h != null) targets.add(h);
        }
        if (targets.isEmpty()) return;

        // Pass 1: equal shares. Pass 2: hand what is left to whoever still has room.
        int n = targets.size();
        roundRobin = (roundRobin + 1) % n;
        long budget = Math.min(energy, rate);
        for (int pass = 0; pass < 2 && budget > 0; pass++) {
            long share = pass == 0 ? Math.max(1, budget / n) : budget;
            for (int k = 0; k < n && budget > 0; k++) {
                EnergyHandler target = targets.get((k + roundRobin) % n);
                int offer = (int) Math.min(Integer.MAX_VALUE, Math.min(share, budget));
                try (Transaction tx = Transaction.openRoot()) {
                    int accepted = target.insert(offer, tx);
                    tx.commit();
                    energy -= accepted;
                    budget -= accepted;
                }
            }
        }
    }

    private final class Journal extends SnapshotJournal<Long> {
        @Override
        protected Long createSnapshot() {
            return energy;
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            energy = snapshot;
        }
    }
}
