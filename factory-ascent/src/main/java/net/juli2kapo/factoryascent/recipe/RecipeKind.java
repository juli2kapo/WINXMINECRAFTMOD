package net.juli2kapo.factoryascent.recipe;

import java.util.Locale;

/** One recipe type per processing machine. The number of input slots bounds the ingredient count. */
public enum RecipeKind {
    SMELTING(1, false),
    CRUSHING(1, false),
    PRESSING(1, true),
    ALLOYING(2, false),
    ASSEMBLING(4, false),
    COKING(1, false),
    BLASTING(2, false);

    public static final RecipeKind[] VALUES = values();

    private final int maxInputs;
    private final boolean usesMold;

    RecipeKind(int maxInputs, boolean usesMold) {
        this.maxInputs = maxInputs;
        this.usesMold = usesMold;
    }

    public int maxInputs() {
        return maxInputs;
    }

    /** Whether the machine has a mould slot whose item selects (but is not consumed by) the recipe. */
    public boolean usesMold() {
        return usesMold;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
