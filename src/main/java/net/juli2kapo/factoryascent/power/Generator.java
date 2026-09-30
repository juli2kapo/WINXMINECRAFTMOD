package net.juli2kapo.factoryascent.power;

import java.util.Locale;
import java.util.function.IntSupplier;
import net.juli2kapo.factoryascent.Age;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The single-block generators of the power ladder, in age order. */
public enum Generator {
    /** Turns the rotation of a Water Wheel or Windmill next to it into FE: the first FE of the game. */
    KINETIC_DYNAMO(Age.BRONZE, Shapes.or(Block.box(2, 0, 2, 14, 12, 14), Block.box(0, 4, 4, 16, 8, 12), Block.box(4, 4, 0, 12, 8, 16)),
            () -> Math.round(PowerConfig.get(PowerConfig.DYNAMO_FE_PER_POINT) * 1.2f)),
    /** Burns fuel under a boiler; the steam drives a piston and flywheel. Needs water and time to heat up. */
    STEAM_ENGINE(Age.ELECTRIC, Shapes.block(), () -> PowerConfig.get(PowerConfig.STEAM_ENGINE_OUTPUT)),
    /** A tall three-bladed turbine on a mast: more wind the higher it stands, more in storms. */
    WIND_TURBINE(Age.ELECTRIC, Block.box(3, 0, 3, 13, 14, 13), () -> Math.round(PowerConfig.get(PowerConfig.WIND_TURBINE_OUTPUT) * 1.8f)),
    /** Digests organic items into biogas and burns it. */
    BIOGAS_GENERATOR(Age.AUTOMATION, Shapes.or(Block.box(1, 0, 1, 15, 13, 15), Block.box(10, 13, 10, 13, 16, 13)),
            () -> PowerConfig.get(PowerConfig.BIOGAS_OUTPUT)),
    /** Burns lava (buckets, magma blocks) through a heat exchanger. */
    MAGMATIC_GENERATOR(Age.AUTOMATION, Shapes.block(), () -> PowerConfig.get(PowerConfig.MAGMATIC_OUTPUT)),
    /** A big tilted solar array: six solar panels' worth, better still in airless space. */
    SOLAR_ARRAY(Age.AUTOMATION, Shapes.or(Block.box(6, 0, 6, 10, 8, 10), Block.box(0, 6, 0, 16, 13, 16)),
            () -> Math.round(PowerConfig.get(PowerConfig.SOLAR_ARRAY_OUTPUT) * 1.5f)),
    /** Radioisotope thermoelectric generator: small steady power from a decaying pellet, anywhere. */
    RTG(Age.ORBITAL, Shapes.or(Block.box(4, 0, 4, 12, 15, 12), Block.box(0, 2, 6, 16, 13, 10), Block.box(6, 2, 0, 10, 13, 16)),
            () -> PowerConfig.get(PowerConfig.RTG_OUTPUT));

    public static final Generator[] VALUES = values();

    private final Age age;
    private final VoxelShape shape;
    private final IntSupplier peak;

    Generator(Age age, VoxelShape shape, IntSupplier peak) {
        this.age = age;
        this.shape = shape;
        this.peak = peak;
    }

    public Age age() {
        return age;
    }

    public VoxelShape shape() {
        return shape;
    }

    /** Highest FE/t it can make (before the global generator multiplier). */
    public int peak() {
        return peak.getAsInt();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean fullBlock() {
        return shape == Shapes.block();
    }
}
