package net.juli2kapo.factoryascent.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerData;

/** GUI sync of the reactor screen: read-outs and the top-down core map, ints as two 16-bit slots each. */
public class ReactorData implements ContainerData {
    public static final int ENERGY = 0, CAPACITY = 1, RATE = 2, TEMP = 3, COOLANT = 4, COOLANT_CAP = 5, INSERTION = 6, FLAGS = 7,
            HEAT = 8, RODS = 9, CHANNELS = 10, CONTROL_RODS = 11, BONUS = 12, AUTHORITY = 13, DIMS = 14, BURN = 15, ERROR = 16,
            BAD_X = 17, BAD_Y = 18, BAD_Z = 19, REMOVED = 20, CORE = 21, INTS = CORE + 25;
    public static final int F_VALID = 1, F_SCRAM_BUTTON = 2, F_REDSTONE = 4, F_WASTE = 8, F_MELTED = 16;

    private final int[] shorts = new int[INTS * 2];

    void update(ReactorControllerBlockEntity r) {
        ReactorStructure s = r.structure();
        put(ENERGY, r.energy().energy());
        put(CAPACITY, r.energy().capacity());
        put(RATE, r.rate());
        put(TEMP, Math.round(r.temperature() * 10));
        put(COOLANT, Math.round(r.coolant()));
        put(COOLANT_CAP, r.coolantCapacity());
        put(INSERTION, r.insertion());
        put(FLAGS, (s.valid() ? F_VALID : 0) | (r.scramButton() ? F_SCRAM_BUTTON : 0) | (r.redstoneScram() ? F_REDSTONE : 0)
                | (r.wasteScram() ? F_WASTE : 0) | (r.meltedDown() ? F_MELTED : 0));
        put(HEAT, Math.round(r.heat() * 10));
        put(RODS, r.activeRods());
        put(CHANNELS, s.channels());
        put(CONTROL_RODS, s.controlRods());
        put(BONUS, Math.round(r.neighbourBonus() * 100));
        put(AUTHORITY, Math.round(r.authority() * 100));
        put(DIMS, s.width() * 100 + s.height() * 10 + s.depth());
        put(BURN, Math.round(Math.min(1f, r.burnFraction()) * 1000));
        put(ERROR, s.error().ordinal());
        BlockPos bad = s.bad();
        put(BAD_X, bad == null ? 0 : bad.getX());
        put(BAD_Y, bad == null ? 0 : bad.getY());
        put(BAD_Z, bad == null ? 0 : bad.getZ());
        put(REMOVED, Math.round(r.removedHeat() * 10));
        int[][] cols = s.columns();
        for (int i = 0; i < 25; i++) {
            int a = i % 5, c = i / 5;
            put(CORE + i, a < cols.length && c < (cols.length > 0 ? cols[0].length : 0) ? cols[a][c] + 1 : 0);
        }
    }

    public void put(int index, int value) {
        shorts[index * 2] = value & 0xFFFF;
        shorts[index * 2 + 1] = (value >>> 16) & 0xFFFF;
    }

    public int get(int index, boolean asInt) {
        return (shorts[index * 2] & 0xFFFF) | ((shorts[index * 2 + 1] & 0xFFFF) << 16);
    }

    public int i(int index) {
        return get(index, true);
    }

    public boolean flag(int f) {
        return (i(FLAGS) & f) != 0;
    }

    /** Core map cell (a, c): -1 outside the core, else channels * 8 + control rods in that column. */
    public int cell(int a, int c) {
        return i(CORE + c * 5 + a) - 1;
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
