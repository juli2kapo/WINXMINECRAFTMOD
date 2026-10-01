package net.juli2kapo.factoryascent.fluid.machine;

import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * GUI sync of a fluid machine: energy, capacity, FE/t, status, progress, up to {@link #TANKS}
 * tanks (fluid registry id, amount, capacity) and {@link #EXTRA} read-outs, each a full int sent
 * as two 16-bit container data slots.
 */
public class FluidMachineData implements ContainerData {
    public static final int TANKS = 4, EXTRA = 4;
    public static final int ENERGY = 0, CAPACITY = 1, RATE = 2, STATUS = 3, PROGRESS = 4, FIRST_TANK = 5,
            FIRST_EXTRA = FIRST_TANK + TANKS * 3, INTS = FIRST_EXTRA + EXTRA;

    private final int[] shorts = new int[INTS * 2];

    void update(FluidMachineBlockEntity be) {
        put(ENERGY, be.energy().energy());
        put(CAPACITY, be.energy().capacity());
        put(RATE, be.lastRate());
        put(STATUS, be.status());
        put(PROGRESS, be.progress());
        for (int i = 0; i < TANKS; i++) {
            FluidTank t = i < be.tanks().size() ? be.tanks().get(i) : null;
            put(FIRST_TANK + i * 3, t == null || t.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(t.fluid()));
            put(FIRST_TANK + i * 3 + 1, t == null ? 0 : t.amount());
            put(FIRST_TANK + i * 3 + 2, t == null ? 0 : t.capacity());
        }
        int[] extra = be.extras();
        for (int i = 0; i < EXTRA; i++) put(FIRST_EXTRA + i, extra[i]);
    }

    public void put(int index, int value) {
        shorts[index * 2] = value & 0xFFFF;
        shorts[index * 2 + 1] = (value >>> 16) & 0xFFFF;
    }

    public int getInt(int index) {
        return (shorts[index * 2] & 0xFFFF) | ((shorts[index * 2 + 1] & 0xFFFF) << 16);
    }

    public int energy() { return getInt(ENERGY); }
    public int capacity() { return getInt(CAPACITY); }
    public int rate() { return getInt(RATE); }
    public int status() { return getInt(STATUS); }
    public int progress() { return getInt(PROGRESS); }
    public int extra(int i) { return getInt(FIRST_EXTRA + i); }

    public Fluid tankFluid(int i) {
        int id = getInt(FIRST_TANK + i * 3);
        return id < 0 ? Fluids.EMPTY : BuiltInRegistries.FLUID.byId(id);
    }

    public int tankAmount(int i) {
        return getInt(FIRST_TANK + i * 3 + 1);
    }

    public int tankCapacity(int i) {
        return getInt(FIRST_TANK + i * 3 + 2);
    }

    @Override
    public int get(int index) {
        return shorts[index];
    }

    @Override
    public void set(int index, int value) {
        shorts[index] = value;
    }

    @Override
    public int getCount() {
        return shorts.length;
    }
}
