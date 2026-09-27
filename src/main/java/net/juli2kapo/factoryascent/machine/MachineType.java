package net.juli2kapo.factoryascent.machine;

import java.util.Locale;
import net.juli2kapo.factoryascent.Age;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.recipe.RecipeKind;
import org.jspecify.annotations.Nullable;

/**
 * Every machine block. Machines are not tiered: a better machine for the same job is a different
 * block from a later {@link Age} with a higher {@code grade}. Recipes may require a minimum grade
 * ({@code min_grade}), which is how better machines unlock better yields.
 */
public enum MachineType {
    // ---- Stone age
    QUERN(Age.STONE, Category.PROCESSOR, RecipeKind.CRUSHING, Power.MANUAL, 1, 1f, 0, 0),
    BRICK_KILN(Age.STONE, Category.PROCESSOR, RecipeKind.ALLOYING, Power.FUEL, 1, 1f, 0, 0),
    // ---- Bronze age
    BURNER_CRUSHER(Age.BRONZE, Category.PROCESSOR, RecipeKind.CRUSHING, Power.FUEL, 2, 1f, 0, 0),
    BURNER_PRESS(Age.BRONZE, Category.PROCESSOR, RecipeKind.PRESSING, Power.FUEL, 1, 1f, 0, 0),
    COKE_OVEN(Age.BRONZE, Category.PROCESSOR, RecipeKind.COKING, Power.NONE, 1, 1f, 0, 0),
    BLAST_FURNACE(Age.BRONZE, Category.PROCESSOR, RecipeKind.BLASTING, Power.FUEL, 1, 1f, 0, 0),
    // ---- Electric age
    ELECTRIC_FURNACE(Age.ELECTRIC, Category.PROCESSOR, RecipeKind.SMELTING, Power.ELECTRIC, 3, 2f, 16, 2),
    CRUSHER(Age.ELECTRIC, Category.PROCESSOR, RecipeKind.CRUSHING, Power.ELECTRIC, 3, 2f, 16, 2),
    METAL_PRESS(Age.ELECTRIC, Category.PROCESSOR, RecipeKind.PRESSING, Power.ELECTRIC, 3, 2f, 16, 2),
    ALLOY_SMELTER(Age.ELECTRIC, Category.PROCESSOR, RecipeKind.ALLOYING, Power.ELECTRIC, 3, 2f, 32, 2),
    ASSEMBLER(Age.ELECTRIC, Category.PROCESSOR, RecipeKind.ASSEMBLING, Power.ELECTRIC, 3, 1f, 32, 2),
    COMBUSTION_GENERATOR(Age.ELECTRIC, Category.GENERATOR, null, Power.FUEL, 1, 1f, 40, 0),
    SOLAR_PANEL(Age.ELECTRIC, Category.GENERATOR, null, Power.NONE, 1, 1f, 8, 0),
    AUTO_FARMER(Age.ELECTRIC, Category.FARMER, null, Power.ELECTRIC, 3, 1f, 24, 2),
    ENERGY_CELL(Age.ELECTRIC, Category.STORAGE, null, Power.ELECTRIC, 1, 1f, 0, 0, Tier.LV),
    // ---- Automation age
    MINER(Age.AUTOMATION, Category.MINER, null, Power.ELECTRIC, 4, 2f, 64, 3),
    GEOTHERMAL_GENERATOR(Age.AUTOMATION, Category.GENERATOR, null, Power.NONE, 1, 1f, 48, 0),
    ADVANCED_ENERGY_CELL(Age.AUTOMATION, Category.STORAGE, null, Power.ELECTRIC, 1, 1f, 0, 0, Tier.MV),
    /** Washes raw ore into 3 dust (grade 4 crushing). */
    ORE_WASHER(Age.AUTOMATION, Category.PROCESSOR, RecipeKind.CRUSHING, Power.ELECTRIC, 4, 2.5f, 48, 3),
    // ---- Industrial age
    /** Smelts titanium (grade 5) and runs every smelting recipe fast. */
    INDUCTION_SMELTER(Age.INDUSTRIAL, Category.PROCESSOR, RecipeKind.SMELTING, Power.ELECTRIC, 5, 4f, 64, 3),
    /** Presses titanium plates and gears (grade 5). */
    HYDRAULIC_PRESS(Age.INDUSTRIAL, Category.PROCESSOR, RecipeKind.PRESSING, Power.ELECTRIC, 5, 3f, 64, 3),
    INDUSTRIAL_ENERGY_CELL(Age.INDUSTRIAL, Category.STORAGE, null, Power.ELECTRIC, 1, 1f, 0, 0, Tier.HV),
    // ---- Orbital age
    /** Builds orbital components such as the Orbital Targeting Core (grade 6). */
    PRECISION_ASSEMBLER(Age.ORBITAL, Category.PROCESSOR, RecipeKind.ASSEMBLING, Power.ELECTRIC, 6, 2f, 128, 3),
    /** Forges quantum alloy (grade 7): the breakthrough into the Quantum age. */
    PLASMA_FORGE(Age.ORBITAL, Category.PROCESSOR, RecipeKind.ALLOYING, Power.ELECTRIC, 7, 2f, 256, 3),
    // ---- Quantum age
    QUANTUM_ENERGY_CELL(Age.QUANTUM, Category.STORAGE, null, Power.ELECTRIC, 1, 1f, 0, 0, Tier.EV);

    public static final MachineType[] VALUES = values();

    public enum Category { PROCESSOR, MINER, GENERATOR, STORAGE, FARMER }

    /** Where a machine's work comes from. */
    public enum Power { MANUAL, FUEL, ELECTRIC, NONE }

    private final Age age;
    private final Category category;
    private final @Nullable RecipeKind recipeKind;
    private final Power power;
    private final int grade;
    private final float speed;
    private final int baseEnergy;
    private final int upgradeSlots;
    private final @Nullable Tier tier;

    MachineType(Age age, Category category, @Nullable RecipeKind recipeKind, Power power, int grade, float speed,
                int baseEnergy, int upgradeSlots) {
        this(age, category, recipeKind, power, grade, speed, baseEnergy, upgradeSlots, null);
    }

    MachineType(Age age, Category category, @Nullable RecipeKind recipeKind, Power power, int grade, float speed,
                int baseEnergy, int upgradeSlots, @Nullable Tier tier) {
        this.age = age;
        this.category = category;
        this.recipeKind = recipeKind;
        this.power = power;
        this.grade = grade;
        this.speed = speed;
        this.baseEnergy = baseEnergy;
        this.upgradeSlots = upgradeSlots;
        this.tier = tier;
    }

    public Age age() { return age; }
    public Category category() { return category; }
    public @Nullable RecipeKind recipeKind() { return recipeKind; }

    /** Electric smelters also run every vanilla furnace recipe (at a fifth of the furnace's time). */
    public boolean runsVanillaSmelting() {
        return recipeKind == RecipeKind.SMELTING && power == Power.ELECTRIC;
    }
    public Power power() { return power; }
    /** Recipes with {@code min_grade} above this are out of reach for this machine. */
    public int grade() { return grade; }
    /** Work points per tick before upgrades. */
    public float speed() { return speed; }
    /** Energy per tick at full speed (consumers) or produced per tick (generators). */
    public int baseEnergy() { return baseEnergy; }
    public int upgradeSlots() { return upgradeSlots; }
    /** Infrastructure tier, for energy cells only. */
    public @Nullable Tier tier() { return tier; }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean hasFacing() {
        return this != MINER && this != SOLAR_PANEL && this != QUERN;
    }

    public boolean isMultiblock() {
        return this == COKE_OVEN || this == BLAST_FURNACE;
    }

    public boolean usesEnergy() {
        return power == Power.ELECTRIC || category == Category.GENERATOR || category == Category.STORAGE;
    }

    public int inputSlots() {
        return recipeKind == null ? 0 : recipeKind.maxInputs();
    }

    public int moldSlots() {
        return recipeKind != null && recipeKind.usesMold() ? 1 : 0;
    }

    public int outputSlots() {
        return switch (category) {
            case PROCESSOR -> 2;
            case MINER, FARMER -> 6;
            default -> 0;
        };
    }

    /** Fuel slot for burners; for energy cells the same slot charges items. */
    public int fuelSlots() {
        return power == Power.FUEL || category == Category.STORAGE ? 1 : 0;
    }

    /** Auto-Farmer keeps seeds/saplings to replant in its input slots. */
    public int extraInputSlots() {
        return category == Category.FARMER ? 3 : 0;
    }
}
