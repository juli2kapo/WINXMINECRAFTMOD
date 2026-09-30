package net.juli2kapo.factoryascent.power;

import net.minecraft.world.inventory.ContainerData;

/**
 * GUI sync of a power block: energy, capacity, FE/t, status and up to {@link #EXTRA} machine
 * read-outs, each a full int sent as two 16-bit container data slots.
 */
public class PowerData implements ContainerData {
    public static final int EXTRA = 8;
    public static final int ENERGY = 0, CAPACITY = 1, RATE = 2, STATUS = 3, FIRST_EXTRA = 4;
    public static final int INTS = FIRST_EXTRA + EXTRA;

    private final int[] shorts = new int[INTS * 2];

    void update(PowerBlockEntity be) {
        put(ENERGY, be.energy().energy());
        put(CAPACITY, be.energy().capacity());
        put(RATE, be.lastRate());
        put(STATUS, be.status());
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
    public int extra(int i) { return getInt(FIRST_EXTRA + i); }

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
