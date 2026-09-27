package net.juli2kapo.factoryascent.machine;

import net.minecraft.world.inventory.ContainerData;

/**
 * GUI sync values. Container data travels as 16-bit shorts, so 32-bit numbers use two slots.
 * The server fills it from the block entity every tick; the client just stores what arrives.
 */
public class MachineData implements ContainerData {
    public static final int ENERGY = 0;       // 2 slots
    public static final int CAPACITY = 2;     // 2 slots
    public static final int PROGRESS = 4;     // permille
    public static final int RATE_FE = 5;      // 2 slots, FE/t
    public static final int FLAGS = 7;        // bit0 auto-eject
    public static final int STATUS = 8;
    public static final int RATE_ITEMS = 9;   // 2 slots, per minute x10
    public static final int EXTRA_A = 11;
    public static final int EXTRA_B = 12;
    public static final int SPEED = 13;       // percent
    public static final int COUNT = 14;

    private final int[] values = new int[COUNT];

    void update(AbstractMachineBlockEntity be) {
        putInt(ENERGY, be.energy().energy());
        putInt(CAPACITY, be.energy().capacity());
        values[PROGRESS] = be.progressPermille();
        putInt(RATE_FE, be.lastEnergyRate());
        values[FLAGS] = be.autoEject() ? 1 : 0;
        values[STATUS] = be.status();
        putInt(RATE_ITEMS, be.ratePerMinuteX10());
        values[EXTRA_A] = be.extraA();
        values[EXTRA_B] = be.extraB();
        values[SPEED] = Math.round(be.speedMultiplier() * be.tier().speed() * 100);
    }

    private void putInt(int index, int value) {
        values[index] = value & 0xFFFF;
        values[index + 1] = (value >>> 16) & 0xFFFF;
    }

    private int getInt(int index) {
        return (values[index] & 0xFFFF) | ((values[index + 1] & 0xFFFF) << 16);
    }

    public int energy() { return getInt(ENERGY); }
    public int capacity() { return getInt(CAPACITY); }
    public int progress() { return values[PROGRESS]; }
    public int energyRate() { return getInt(RATE_FE); }
    public boolean autoEject() { return (values[FLAGS] & 1) != 0; }
    public int status() { return values[STATUS]; }
    public int itemRateX10() { return getInt(RATE_ITEMS); }
    public int extraA() { return values[EXTRA_A]; }
    public int extraB() { return values[EXTRA_B]; }
    public int speedPercent() { return values[SPEED]; }

    @Override
    public int get(int index) {
        return values[index];
    }

    @Override
    public void set(int index, int value) {
        values[index] = value;
    }

    @Override
    public int getCount() {
        return COUNT;
    }
}
