package net.juli2kapo.factoryascent.trains;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A covered box wagon with a 54-slot hold: right-click to open it; hoppers above or below the rails
 * load and unload it like a chest minecart, and a Train Station moves items between it and the
 * containers beside the platform. Its load makes it heavier.
 */
public class CargoWagon extends RollingStock implements Container {
    public static final int SLOTS = 54;

    public CargoWagon(EntityType<? extends CargoWagon> type, Level level) {
        super(type, level);
    }

    @Override
    public double length() {
        return 38 / 16.0;
    }

    @Override
    public int inventorySize() {
        return SLOTS;
    }

    @Override
    public double mass() {
        return 1.0 + 2.0 * fillFraction();
    }

    /** How full the hold is, 0..1 (by stacks' fullness). */
    public double fillFraction() {
        double f = 0;
        for (var s : items) if (!s.isEmpty()) f += s.getCount() / (double) s.getMaxStackSize();
        return f / SLOTS;
    }

    @Override
    protected void serverTick() {
        if (tickCount % 10 == 0) setFill((int) Math.round(fillFraction() * 1000));
    }
}
