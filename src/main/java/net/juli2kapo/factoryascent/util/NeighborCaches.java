package net.juli2kapo.factoryascent.util;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/** Lazily-created capability caches for the six neighbours of a block entity. */
public final class NeighborCaches {
    private final BlockEntity owner;
    private final BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>[] items;
    private final BlockCapabilityCache<EnergyHandler, @Nullable Direction>[] energy;

    @SuppressWarnings("unchecked")
    public NeighborCaches(BlockEntity owner) {
        this.owner = owner;
        this.items = new BlockCapabilityCache[6];
        this.energy = new BlockCapabilityCache[6];
    }

    /** Item handler of the block on {@code side}, seen from our face. */
    public @Nullable ResourceHandler<ItemResource> items(Direction side) {
        if (!(owner.getLevel() instanceof ServerLevel level)) return null;
        int i = side.ordinal();
        if (items[i] == null) {
            items[i] = BlockCapabilityCache.create(Capabilities.Item.BLOCK, level,
                    owner.getBlockPos().relative(side), side.getOpposite());
        }
        return items[i].getCapability();
    }

    /** Energy handler of the block on {@code side}, seen from our face. */
    public @Nullable EnergyHandler energy(Direction side) {
        if (!(owner.getLevel() instanceof ServerLevel level)) return null;
        int i = side.ordinal();
        if (energy[i] == null) {
            energy[i] = BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level,
                    owner.getBlockPos().relative(side), side.getOpposite());
        }
        return energy[i].getCapability();
    }

    public void clear() {
        for (int i = 0; i < 6; i++) {
            items[i] = null;
            energy[i] = null;
        }
    }
}
