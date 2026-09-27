package net.juli2kapo.factoryascent.util;

import net.juli2kapo.factoryascent.machine.MachineEnergy;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public final class EnergyUtil {
    private EnergyUtil() {}

    /** Pushes up to {@code max} FE from {@code source} into neighbours on the given sides. Returns FE moved. */
    public static int push(NeighborCaches neighbors, MachineEnergy source, int max, Direction... sides) {
        int budget = Math.min(max, source.energy());
        int moved = 0;
        for (Direction dir : sides) {
            if (budget - moved <= 0) break;
            EnergyHandler target = neighbors.energy(dir);
            if (target == null) continue;
            try (Transaction tx = Transaction.openRoot()) {
                int accepted = target.insert(budget - moved, tx);
                tx.commit();
                moved += accepted;
            }
        }
        if (moved > 0) source.consume(moved);
        return moved;
    }

    public static String format(long fe) {
        if (fe >= 1_000_000_000L) return String.format("%.2fG", fe / 1e9);
        if (fe >= 1_000_000L) return String.format("%.2fM", fe / 1e6);
        if (fe >= 10_000L) return String.format("%.1fk", fe / 1e3);
        return Long.toString(fe);
    }
}
