package net.juli2kapo.factoryascent.machine;

import java.util.Locale;
import net.juli2kapo.factoryascent.recipe.RecipeKind;
import org.jspecify.annotations.Nullable;

/** Every tiered machine block. Each exists as {@code <tier>_<id>}, e.g. {@code elite_crusher}. */
public enum MachineType {
    ELECTRIC_FURNACE(Category.PROCESSOR, RecipeKind.SMELTING, 16),
    CRUSHER(Category.PROCESSOR, RecipeKind.CRUSHING, 12),
    METAL_PRESS(Category.PROCESSOR, RecipeKind.PRESSING, 12),
    ALLOY_SMELTER(Category.PROCESSOR, RecipeKind.ALLOYING, 32),
    ASSEMBLER(Category.PROCESSOR, RecipeKind.ASSEMBLING, 24),
    MINER(Category.MINER, null, 32),
    COMBUSTION_GENERATOR(Category.GENERATOR, null, 40),
    SOLAR_PANEL(Category.GENERATOR, null, 8),
    GEOTHERMAL_GENERATOR(Category.GENERATOR, null, 24),
    ENERGY_CELL(Category.STORAGE, null, 0);

    public static final MachineType[] VALUES = values();

    public enum Category { PROCESSOR, MINER, GENERATOR, STORAGE }

    private final Category category;
    private final @Nullable RecipeKind recipeKind;
    private final int baseEnergy;

    MachineType(Category category, @Nullable RecipeKind recipeKind, int baseEnergy) {
        this.category = category;
        this.recipeKind = recipeKind;
        this.baseEnergy = baseEnergy;
    }

    public Category category() {
        return category;
    }

    public @Nullable RecipeKind recipeKind() {
        return recipeKind;
    }

    /** Basic-tier energy per tick: consumption for machines, production for generators. */
    public int baseEnergy() {
        return baseEnergy;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether the block turns to face the player when placed. */
    public boolean hasFacing() {
        return this != MINER && this != SOLAR_PANEL;
    }

    public boolean acceptsUpgrades() {
        return category == Category.PROCESSOR || category == Category.MINER;
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
            case MINER -> 6;
            default -> 0;
        };
    }

    public int fuelSlots() {
        return this == COMBUSTION_GENERATOR ? 1 : 0;
    }

    public int upgradeSlots() {
        return acceptsUpgrades() ? 3 : 0;
    }
}
