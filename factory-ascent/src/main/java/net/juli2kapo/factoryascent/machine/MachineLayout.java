package net.juli2kapo.factoryascent.machine;

/** GUI coordinates shared by the menu (slot positions) and the screen (slot frames). */
public final class MachineLayout {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 178;
    public static final int PLAYER_INV_Y = 96;
    public static final int ARROW_Y = 35;

    private MachineLayout() {}

    public record Pos(int x, int y) {}

    public static Pos input(MachineType type, int n) {
        if (type.moldSlots() > 0) return new Pos(44, 24);
        int count = type.inputSlots();
        return switch (count) {
            case 1 -> new Pos(44, 35);
            case 2 -> new Pos(35 + 18 * n, 35);
            default -> new Pos(35 + 18 * (n % 2), 26 + 18 * (n / 2));
        };
    }

    public static Pos mold(int n) {
        return new Pos(44, 48);
    }

    public static Pos fuel(int n) {
        return new Pos(80, 50);
    }

    public static Pos output(MachineType type, int n) {
        if (type == MachineType.MINER) {
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
}
