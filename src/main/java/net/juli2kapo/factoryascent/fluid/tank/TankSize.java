package net.juli2kapo.factoryascent.fluid.tank;

import java.util.Locale;
import net.juli2kapo.factoryascent.Age;

/** The three fluid tank sizes. */
public enum TankSize {
    BRONZE(Age.BRONZE, 16_000),
    STEEL(Age.ELECTRIC, 64_000),
    TITANIUM(Age.INDUSTRIAL, 256_000);

    public static final TankSize[] VALUES = values();

    private final Age age;
    private final int capacity;

    TankSize(Age age, int capacity) {
        this.age = age;
        this.capacity = capacity;
    }

    public Age age() {
        return age;
    }

    public int capacity() {
        return capacity;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_fluid_tank";
    }
}
