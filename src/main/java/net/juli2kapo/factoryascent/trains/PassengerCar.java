package net.juli2kapo.factoryascent.trains;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A coach with four seats (two benches facing forward), windows and a roof. */
public class PassengerCar extends RollingStock {
    private static final Vec3[] SEATS = {new Vec3(3.5 / 16, 4 / 16.0, 10 / 16.0), new Vec3(-3.5 / 16, 4 / 16.0, 10 / 16.0),
            new Vec3(3.5 / 16, 4 / 16.0, -10 / 16.0), new Vec3(-3.5 / 16, 4 / 16.0, -10 / 16.0)};

    public PassengerCar(EntityType<? extends PassengerCar> type, Level level) {
        super(type, level);
    }

    @Override
    public double length() {
        return 44 / 16.0;
    }

    @Override
    public double mass() {
        return 1.5 + getPassengers().size() * 0.1;
    }

    @Override
    protected Vec3[] seats() {
        return SEATS;
    }
}
