package net.juli2kapo.factoryascent.fluid;

import java.util.List;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * What pipes, buckets and other mods see of a machine's tanks: one index per tank; inserting only
 * into tanks pipes may fill, extracting only from tanks pipes may drain.
 */
public class TankPorts implements ResourceHandler<FluidResource> {
    private final List<FluidTank> tanks;

    public TankPorts(List<FluidTank> tanks) {
        this.tanks = tanks;
    }

    public TankPorts(FluidTank... tanks) {
        this(List.of(tanks));
    }

    public List<FluidTank> tanks() {
        return tanks;
    }

    @Override
    public int size() {
        return tanks.size();
    }

    @Override
    public FluidResource getResource(int index) {
        return tanks.get(index).getResource(0);
    }

    @Override
    public long getAmountAsLong(int index) {
        return tanks.get(index).getAmountAsLong(0);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return tanks.get(index).getCapacityAsLong(0, resource);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        FluidTank t = tanks.get(index);
        return t.pipesFill() && t.isValid(0, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        FluidTank t = tanks.get(index);
        return t.pipesFill() ? t.insert(0, resource, amount, transaction) : 0;
    }

    @Override
    public int insert(FluidResource resource, int amount, TransactionContext transaction) {
        // Prefer a tank that already holds this fluid, so two tanks never fill with the same thing.
        int inserted = 0;
        for (FluidTank t : tanks) {
            if (inserted >= amount) break;
            if (t.pipesFill() && !t.isEmpty() && t.getResource(0).equals(resource)) inserted += t.insert(0, resource, amount - inserted, transaction);
        }
        for (FluidTank t : tanks) {
            if (inserted >= amount) break;
            if (t.pipesFill() && t.isEmpty()) inserted += t.insert(0, resource, amount - inserted, transaction);
        }
        return inserted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        FluidTank t = tanks.get(index);
        return t.pipesDrain() ? t.extract(0, resource, amount, transaction) : 0;
    }
}
