package net.juli2kapo.factoryascent.fluid.machine;

import java.util.List;
import java.util.Locale;
import net.juli2kapo.factoryascent.Age;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The fluid machines, with their place in the ages, power and screen layout. Behaviour lives in
 * one block entity class each (see {@link FluidMachineBlockEntity}).
 */
public enum FluidMachine {
    /** Fire-tube boiler: burns furnace fuel and boils water into steam for Steam Engines and Turbines. */
    BOILER(Age.ELECTRIC, Power.NONE, Shapes.block(),
            List.of(slot(0, 44, 53, false), slot(1, 152, 17, false), slot(2, 152, 53, true)),
            List.of(tank(80, 17, 18, 54), tank(118, 17, 18, 54)), 0, 0, true),
    /** Electric pump: pulls fluid source blocks around and below it into its tank. */
    PUMP(Age.ELECTRIC, Power.CONSUMER, Block.box(2, 0, 2, 14, 16, 14),
            List.of(slot(0, 120, 17, false), slot(1, 120, 53, true)), List.of(tank(80, 17, 26, 54)), 0, 0, false),
    /** Rots organic items into biogas, for the Biogas Generator. */
    BIOGAS_DIGESTER(Age.AUTOMATION, Power.NONE, Shapes.block(),
            List.of(slot(0, 44, 17, false), slot(1, 44, 35, false), slot(2, 44, 53, false)),
            List.of(tank(110, 17, 26, 54)), 72, 35, false),
    /** Burns diesel: steady industrial power. */
    DIESEL_GENERATOR(Age.AUTOMATION, Power.PRODUCER, Shapes.block(),
            List.of(slot(0, 44, 17, false), slot(1, 44, 53, true)), List.of(tank(80, 17, 26, 54)), 0, 0, false),
    /** Multiblock pumpjack: draws crude oil out of the oil pocket under it. */
    OIL_DERRICK(Age.AUTOMATION, Power.CONSUMER, Shapes.block(),
            List.of(), List.of(tank(110, 17, 26, 54)), 72, 35, false),
    /** Multiblock distillation tower: crude oil into diesel, rocket fuel, plastic and tar. */
    REFINERY(Age.AUTOMATION, Power.CONSUMER, Shapes.block(),
            List.of(slot(0, 152, 17, true), slot(1, 152, 53, true)),
            List.of(tank(30, 17, 18, 54), tank(98, 17, 18, 54), tank(124, 17, 18, 54)), 52, 35, false),
    /** Big steam turbine: turns a reactor's (or several boilers') steam into a lot of FE. */
    STEAM_TURBINE(Age.INDUSTRIAL, Power.PRODUCER, Shapes.block(),
            List.of(), List.of(tank(80, 17, 26, 54)), 0, 0, false),
    /** Fills Launch Controllers and docked Shuttles with liquid rocket fuel. */
    FUELLING_PORT(Age.ORBITAL, Power.NONE, Block.box(1, 0, 1, 15, 15, 15),
            List.of(slot(0, 44, 35, false), slot(1, 132, 35, true)), List.of(tank(80, 17, 26, 54)), 0, 0, false);

    public enum Power { NONE, CONSUMER, PRODUCER }

    public record SlotSpec(int index, int x, int y, boolean output) {}

    /** A tank gauge on the screen (position relative to the screen, size). */
    public record TankSpec(int x, int y, int w, int h) {}

    public static final FluidMachine[] VALUES = values();

    private final Age age;
    private final Power power;
    private final VoxelShape shape;
    private final List<SlotSpec> slots;
    private final List<TankSpec> tanks;
    private final int arrowX, arrowY;
    private final boolean flame;

    FluidMachine(Age age, Power power, VoxelShape shape, List<SlotSpec> slots, List<TankSpec> tanks, int arrowX, int arrowY, boolean flame) {
        this.age = age;
        this.power = power;
        this.shape = shape;
        this.slots = slots;
        this.tanks = tanks;
        this.arrowX = arrowX;
        this.arrowY = arrowY;
        this.flame = flame;
    }

    private static SlotSpec slot(int index, int x, int y, boolean output) {
        return new SlotSpec(index, x, y, output);
    }

    private static TankSpec tank(int x, int y, int w, int h) {
        return new TankSpec(x, y, w, h);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Age age() {
        return age;
    }

    public Power power() {
        return power;
    }

    public VoxelShape shape() {
        return shape;
    }

    public boolean fullBlock() {
        return shape == Shapes.block();
    }

    public List<SlotSpec> slots() {
        return slots;
    }

    public List<TankSpec> tankSpecs() {
        return tanks;
    }

    public boolean hasArrow() {
        return arrowX > 0;
    }

    public int arrowX() {
        return arrowX;
    }

    public int arrowY() {
        return arrowY;
    }

    public boolean hasFlame() {
        return flame;
    }

    public boolean multiblock() {
        return this == OIL_DERRICK || this == REFINERY;
    }
}
