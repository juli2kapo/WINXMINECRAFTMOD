package net.juli2kapo.factoryascent.machine;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/** Energy buffer whose limits follow the machine tier. */
public class MachineEnergy extends SimpleEnergyHandler {
    private final Runnable onChange;

    public MachineEnergy(Runnable onChange) {
        super(0, 0, 0);
        this.onChange = onChange;
    }

    public void configure(int capacity, int maxInsert, int maxExtract) {
        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
        if (energy > capacity) energy = capacity;
    }

    public int energy() {
        return energy;
    }

    public int capacity() {
        return capacity;
    }

    public int space() {
        return capacity - energy;
    }

    /** Internal use by the owning machine; bypasses the automation limits. */
    public void consume(int amount) {
        if (amount > 0) set(Math.max(0, energy - amount));
    }

    /** Internal use by the owning machine; bypasses the automation limits. */
    public int produce(int amount) {
        int added = Math.min(amount, capacity - energy);
        if (added > 0) set(energy + added);
        return added;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onChange.run();
    }
}
