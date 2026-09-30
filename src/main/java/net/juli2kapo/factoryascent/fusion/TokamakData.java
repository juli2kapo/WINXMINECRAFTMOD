package net.juli2kapo.factoryascent.fusion;

import net.juli2kapo.factoryascent.power.PowerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerData;

/** GUI sync of the tokamak screen, ints as two 16-bit slots each. */
public class TokamakData implements ContainerData {
    public static final int ENERGY = 0, CAPACITY = 1, RATE = 2, STATE = 3, CHARGE_K = 4, STARTUP_K = 5, PLASMA = 6, ENABLED = 7,
            D_BURN = 8, H_BURN = 9, ERROR = 10, BAD_X = 11, BAD_Y = 12, BAD_Z = 13, OUTPUT = 14, INTS = 15;

    private final int[] shorts = new int[INTS * 2];

    void update(TokamakCoreBlockEntity t) {
        put(ENERGY, t.output().energy());
        put(CAPACITY, t.output().capacity());
        put(RATE, t.rate());
        put(STATE, t.state().ordinal());
        put(CHARGE_K, (int) (t.charge() / 1000));
        put(STARTUP_K, (int) (TokamakCoreBlockEntity.startupEnergy() / 1000));
        int plasma = switch (t.state()) {
            case IGNITING -> t.timer() * 1000 / TokamakCoreBlockEntity.IGNITION_TICKS;
            case RUNNING -> 1000;
            default -> 0;
        };
        put(PLASMA, plasma);
        put(ENABLED, t.enabled() ? 1 : 0);
        put(D_BURN, t.deuteriumBurn() * 1000 / Math.max(1, PowerConfig.get(PowerConfig.FUSION_DEUTERIUM_SECONDS) * 20));
        put(H_BURN, t.helium3Burn() * 1000 / Math.max(1, PowerConfig.get(PowerConfig.FUSION_HELIUM3_SECONDS) * 20));
        put(ERROR, t.structure().error().ordinal());
        BlockPos bad = t.structure().bad();
        put(BAD_X, bad == null ? 0 : bad.getX());
        put(BAD_Y, bad == null ? 0 : bad.getY());
        put(BAD_Z, bad == null ? 0 : bad.getZ());
        put(OUTPUT, TokamakCoreBlockEntity.outputPerTick());
    }

    public void put(int index, int value) {
        shorts[index * 2] = value & 0xFFFF;
        shorts[index * 2 + 1] = (value >>> 16) & 0xFFFF;
    }

    public int i(int index) {
        return (shorts[index * 2] & 0xFFFF) | ((shorts[index * 2 + 1] & 0xFFFF) << 16);
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
