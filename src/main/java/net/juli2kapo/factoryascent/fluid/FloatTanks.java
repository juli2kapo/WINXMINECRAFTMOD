package net.juli2kapo.factoryascent.fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A fluid handler over machines that keep their liquids as plain numbers (the Steam Engine's
 * water, the Biogas Generator's gas, the reactor's coolant...). Each index is one such number:
 * which fluids it takes (and how many units one mB is worth), the fluid it shows and gives,
 * its capacity, and whether pipes may fill and/or drain it. Fully transactional.
 */
public final class FloatTanks implements ResourceHandler<FluidResource> {
    /** One tank: units = mB x {@code unitsPerMb(fluid)}. */
    public static final class Tank {
        final Supplier<Fluid> shown;
        final java.util.function.ToDoubleFunction<Fluid> unitsPerMb;
        final Supplier<Float> get;
        final Consumer<Float> set;
        final IntSupplier capacity;
        final boolean fill, drain;

        /**
         * @param shown      the fluid this tank holds (reported, and what draining gives)
         * @param unitsPerMb units one mB of a fluid adds (0: not accepted)
         * @param capacity   capacity in units
         */
        public Tank(Supplier<Fluid> shown, java.util.function.ToDoubleFunction<Fluid> unitsPerMb, Supplier<Float> get, Consumer<Float> set,
                    IntSupplier capacity, boolean fill, boolean drain) {
            this.shown = shown;
            this.unitsPerMb = unitsPerMb;
            this.get = get;
            this.set = set;
            this.capacity = capacity;
            this.fill = fill;
            this.drain = drain;
        }
    }

    private final List<Tank> tanks;
    private final Runnable onCommit;
    private final Journal journal = new Journal();

    public FloatTanks(Runnable onCommit, Tank... tanks) {
        this.tanks = List.of(tanks);
        this.onCommit = onCommit;
    }

    /** Units per mB for exactly one fluid. */
    public static java.util.function.ToDoubleFunction<Fluid> only(Supplier<Fluid> fluid, double units) {
        return f -> f.isSame(fluid.get()) ? units : 0;
    }

    @Override
    public int size() {
        return tanks.size();
    }

    private double perMb(Tank t, Fluid f) {
        return t.unitsPerMb.applyAsDouble(f);
    }

    @Override
    public FluidResource getResource(int index) {
        Tank t = tanks.get(index);
        return t.get.get() >= perMbShown(t) ? FluidResource.of(t.shown.get()) : FluidResource.EMPTY;
    }

    private double perMbShown(Tank t) {
        double p = perMb(t, t.shown.get());
        return p <= 0 ? 1 : p;
    }

    @Override
    public long getAmountAsLong(int index) {
        Tank t = tanks.get(index);
        return (long) Math.floor(t.get.get() / perMbShown(t));
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        Tank t = tanks.get(index);
        if (resource.isEmpty()) return (long) (t.capacity.getAsInt() / perMbShown(t));
        double p = perMb(t, resource.getFluid());
        return p <= 0 ? 0 : (long) (t.capacity.getAsInt() / p);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        Tank t = tanks.get(index);
        return t.fill && perMb(t, resource.getFluid()) > 0;
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
        Tank t = tanks.get(index);
        if (!t.fill || resource.isEmpty() || amount <= 0) return 0;
        double p = perMb(t, resource.getFluid());
        if (p <= 0) return 0;
        float have = t.get.get();
        int fits = (int) Math.floor((t.capacity.getAsInt() - have) / p);
        int in = Math.min(amount, fits);
        if (in <= 0) return 0;
        journal.updateSnapshots(tx);
        t.set.accept((float) (have + in * p));
        return in;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
        Tank t = tanks.get(index);
        if (!t.drain || resource.isEmpty() || amount <= 0 || !resource.getFluid().isSame(t.shown.get())) return 0;
        double p = perMbShown(t);
        float have = t.get.get();
        int out = Math.min(amount, (int) Math.floor(have / p));
        if (out <= 0) return 0;
        journal.updateSnapshots(tx);
        t.set.accept((float) Math.max(0, have - out * p));
        return out;
    }

    private final class Journal extends SnapshotJournal<float[]> {
        @Override
        protected float[] createSnapshot() {
            float[] s = new float[tanks.size()];
            for (int i = 0; i < s.length; i++) s[i] = tanks.get(i).get.get();
            return s;
        }

        @Override
        protected void revertToSnapshot(float[] snapshot) {
            for (int i = 0; i < snapshot.length; i++) tanks.get(i).set.accept(snapshot[i]);
        }

        @Override
        protected void onRootCommit(float[] originalState) {
            onCommit.run();
        }
    }

    public static List<Tank> list(Tank... t) {
        return new ArrayList<>(List.of(t));
    }
}
