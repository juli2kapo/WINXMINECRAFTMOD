package net.juli2kapo.factoryascent.fluid;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/** Cached fluid handlers of a block entity's six neighbours, and pushing a tank into them. */
public final class FluidNeighbors {
    private final BlockEntity owner;
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>[] caches = new BlockCapabilityCache[6];

    public FluidNeighbors(BlockEntity owner) {
        this.owner = owner;
    }

    public @Nullable ResourceHandler<FluidResource> get(Direction side) {
        if (!(owner.getLevel() instanceof ServerLevel level)) return null;
        int i = side.ordinal();
        if (caches[i] == null) {
            caches[i] = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, owner.getBlockPos().relative(side), side.getOpposite());
        }
        return caches[i].getCapability();
    }

    /** Pushes up to {@code max} mB of the tank's fluid into the neighbours on {@code sides}; returns how much left. */
    public int push(FluidTank tank, int max, Direction... sides) {
        if (tank.isEmpty() || max <= 0) return 0;
        int moved = 0;
        for (Direction side : sides) {
            if (tank.isEmpty() || moved >= max) break;
            ResourceHandler<FluidResource> target = get(side);
            if (target == null) continue;
            FluidResource r = tank.getResource(0);
            try (Transaction tx = Transaction.openRoot()) {
                int in = target.insert(r, Math.min(max - moved, tank.amount()), tx);
                if (in > 0) {
                    int out = tank.extract(0, r, in, tx);
                    if (out == in) {
                        tx.commit();
                        moved += in;
                    }
                }
            }
        }
        return moved;
    }

    /** Pulls up to {@code max} mB of fluid the tank accepts from the neighbours on {@code sides}. */
    public int pull(FluidTank tank, int max, Direction... sides) {
        int moved = 0;
        for (Direction side : sides) {
            if (moved >= max || tank.space() <= 0) break;
            ResourceHandler<FluidResource> source = get(side);
            if (source == null) continue;
            for (int i = 0; i < source.size() && moved < max; i++) {
                FluidResource r = source.getResource(i);
                if (r.isEmpty() || !tank.isValid(0, r)) continue;
                try (Transaction tx = Transaction.openRoot()) {
                    int out = source.extract(i, r, Math.min(max - moved, tank.space()), tx);
                    if (out > 0 && tank.insert(0, r, out, tx) == out) {
                        tx.commit();
                        moved += out;
                    }
                }
            }
        }
        return moved;
    }

    public void clear() {
        java.util.Arrays.fill(caches, null);
    }
}
