package net.juli2kapo.factoryascent.machine;

/** GUI coordinates shared by the menu (slot positions) and the screen (slot frames and gauges). */
public final class MachineLayout {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 178;
    public static final int PLAYER_INV_Y = 96;
    public static final int ARROW_Y = 35;
    /** Flame gauge for fuel-burning machines. */
    public static final int FLAME_X = 45, FLAME_Y = 36;

    private MachineLayout() {}

    public record Pos(int x, int y) {}

    private static boolean burner(MachineType type) {
        return type.power() == MachineType.Power.FUEL && type.category() == MachineType.Category.PROCESSOR;
    }

    public static Pos input(MachineType type, int n) {
        if (type.category() == MachineType.Category.FARMER) return new Pos(26, 17 + 18 * n);
        if (burner(type)) {
            if (type.moldSlots() > 0 || type.inputSlots() == 2) return new Pos(35 + 18 * n, 17);
            return new Pos(44, 17);
        }
        if (type.moldSlots() > 0) return new Pos(44, 24);
        return switch (type.inputSlots()) {
            case 1 -> new Pos(44, 35);
            case 2 -> new Pos(35 + 18 * n, 35);
            default -> new Pos(35 + 18 * (n % 2), 26 + 18 * (n / 2));
        };
    }

    public static Pos mold(MachineType type) {
        return burner(type) ? new Pos(53, 17) : new Pos(44, 48);
    }

    public static Pos fuel(MachineType type) {
        if (type.category() == MachineType.Category.STORAGE) return new Pos(80, 35);
        if (burner(type)) return new Pos(44, 53);
        return new Pos(80, 50);
    }

    public static Pos output(MachineType type, int n) {
        if (type.outputSlots() > 2) {
            return new Pos(90 + 18 * (n % 3), 26 + 18 * (n / 3));
        }
        return new Pos(106 + 20 * n, 35);
    }

    public static Pos upgrade(int n) {
        return new Pos(152, 17 + 18 * n);
    }

    public static int arrowX(MachineType type) {
        return 76;
    }

    public static boolean hasFlame(MachineType type) {
        return burner(type);
    }
}
