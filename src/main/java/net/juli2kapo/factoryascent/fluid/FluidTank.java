package net.juli2kapo.factoryascent.fluid;

import java.util.function.Predicate;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * One tank: a single {@link FluidStack} with a capacity, an optional filter, and whether pipes may
 * fill it and/or drain it. Machines use the direct helpers ({@link #fill}, {@link #drain}) for
 * their own work; pipes go through the transaction-aware {@code ResourceHandler} methods.
 */
public class FluidTank extends FluidStacksResourceHandler {
    private final Predicate<FluidResource> filter;
    private final Runnable onChange;
    private boolean pipesFill = true, pipesDrain = true;

    public FluidTank(int capacity, Predicate<FluidResource> filter, Runnable onChange) {
        super(1, capacity);
        this.filter = filter;
        this.onChange = onChange;
    }

    public static Predicate<FluidResource> only(Fluid... fluids) {
        return r -> {
            for (Fluid f : fluids) if (r.getFluid().isSame(f)) return true;
            return false;
        };
    }

    /** Input-only tank (pipes fill it, never drain it). */
    public FluidTank input() {
        pipesDrain = false;
        return this;
    }

    /** Output-only tank (pipes drain it, never fill it). */
    public FluidTank output() {
        pipesFill = false;
        return this;
    }

    public boolean pipesFill() {
        return pipesFill;
    }

    public boolean pipesDrain() {
        return pipesDrain;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return filter.test(resource);
    }

    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        onChange.run();
    }

    // ---------------------------------------------------------------- direct access

    public FluidStack stack() {
        return stacks.get(0);
    }

    public Fluid fluid() {
        return stacks.get(0).isEmpty() ? Fluids.EMPTY : stacks.get(0).getFluid();
    }

    public int amount() {
        return stacks.get(0).getAmount();
    }

    public int capacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int space() {
        return Math.max(0, capacity - amount());
    }

    public boolean isEmpty() {
        return stacks.get(0).isEmpty();
    }

    public boolean holds(Fluid fluid) {
        return !isEmpty() && fluid().isSame(fluid);
    }

    /** Adds up to {@code amount} of the fluid (if it is valid and fits the contents); returns how much went in. */
    public int fill(Fluid fluid, int amount) {
        if (amount <= 0 || fluid == Fluids.EMPTY) return 0;
        FluidResource r = FluidResource.of(fluid);
        try (Transaction tx = Transaction.openRoot()) {
            int in = insert(0, r, amount, tx);
            tx.commit();
            return in;
        }
    }

    /** Ignores the filter (machines filling their own output tanks). */
    public int forceFill(Fluid fluid, int amount) {
        if (amount <= 0 || fluid == Fluids.EMPTY) return 0;
        if (!isEmpty() && !fluid().isSame(fluid)) return 0;
        int in = Math.min(amount, space());
        if (in <= 0) return 0;
        set(0, FluidResource.of(fluid), amount() + in);
        return in;
    }

    /** Takes up to {@code amount} out; returns how much came out. */
    public int drain(int amount) {
        if (amount <= 0 || isEmpty()) return 0;
        int out = Math.min(amount, amount());
        int left = amount() - out;
        if (left <= 0) set(0, FluidResource.EMPTY, 0);
        else set(0, FluidResource.of(fluid()), left);
        return out;
    }

    public void setContents(Fluid fluid, int amount) {
        if (fluid == Fluids.EMPTY || amount <= 0) set(0, FluidResource.EMPTY, 0);
        else set(0, FluidResource.of(fluid), Math.min(amount, Math.max(amount, capacity)));
    }

    public void save(ValueOutput out, String key) {
        serialize(out.child(key));
    }

    public void load(ValueInput in, String key) {
        deserialize(in.childOrEmpty(key));
    }
}
