package net.juli2kapo.factoryascent.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.juli2kapo.factoryascent.registry.ModRecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jspecify.annotations.Nullable;

/**
 * A shapeless machine recipe: each ingredient (with a count) must be found in a distinct input
 * slot. {@code time} is the Mk1 duration in ticks; faster tiers finish proportionally sooner.
 */
public final class MachineRecipe implements Recipe<MachineRecipeInput> {
    private final RecipeKind kind;
    private final List<SizedIngredient> inputs;
    private final ItemStackTemplate result;
    private final Optional<ChanceOutput> byproduct;
    private final Optional<Ingredient> mold;
    private final int time;
    private final int minGrade;

    public MachineRecipe(RecipeKind kind, List<SizedIngredient> inputs, ItemStackTemplate result,
                         Optional<ChanceOutput> byproduct, Optional<Ingredient> mold, int time, int minGrade) {
        this.kind = kind;
        this.mold = mold;
        this.inputs = List.copyOf(inputs);
        this.result = result;
        this.byproduct = byproduct;
        this.time = time;
        this.minGrade = minGrade;
    }

    public RecipeKind kind() {
        return kind;
    }

    public List<SizedIngredient> inputs() {
        return inputs;
    }

    public ItemStackTemplate result() {
        return result;
    }

    public Optional<ChanceOutput> byproduct() {
        return byproduct;
    }

    /** For the Metal Press: the mould that must sit in the mould slot. Never consumed. */
    public Optional<Ingredient> mold() {
        return mold;
    }

    public boolean moldMatches(ItemStack moldStack) {
        return mold.map(m -> m.test(moldStack)).orElse(true);
    }

    public int time() {
        return time;
    }

    public int minGrade() {
        return minGrade;
    }

    /**
     * Finds which input slot satisfies each ingredient, or null if the recipe does not match.
     * Ignores the machine tier; see {@link #matches}.
     */
    public int @Nullable [] findSlots(List<ItemStack> items) {
        int[] assignment = new int[inputs.size()];
        boolean[] used = new boolean[items.size()];
        return assign(0, items, used, assignment) ? assignment : null;
    }

    private boolean assign(int ingredient, List<ItemStack> items, boolean[] used, int[] assignment) {
        if (ingredient == inputs.size()) {
            return true;
        }
        SizedIngredient wanted = inputs.get(ingredient);
        for (int slot = 0; slot < items.size(); slot++) {
            if (used[slot]) continue;
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && wanted.ingredient().test(stack) && stack.getCount() >= wanted.count()) {
                used[slot] = true;
                assignment[ingredient] = slot;
                if (assign(ingredient + 1, items, used, assignment)) return true;
                used[slot] = false;
            }
        }
        return false;
    }

    /** True if the item could be part of this recipe, used to route automated insertion. */
    public boolean accepts(ItemStack stack) {
        for (SizedIngredient in : inputs) {
            if (in.ingredient().test(stack)) return true;
        }
        return false;
    }

    @Override
    public boolean matches(MachineRecipeInput input, Level level) {
        return input.grade() >= minGrade && findSlots(input.items()) != null;
    }

    @Override
    public ItemStack assemble(MachineRecipeInput input) {
        return result.create();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<MachineRecipe> getSerializer() {
        return ModRecipes.serializer(kind);
    }

    @Override
    public RecipeType<MachineRecipe> getType() {
        return ModRecipes.type(kind);
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    public static MapCodec<MachineRecipe> codec(RecipeKind kind) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                SizedIngredient.NESTED_CODEC.listOf(1, kind.maxInputs()).fieldOf("ingredients").forGetter(MachineRecipe::inputs),
                ItemStackTemplate.CODEC.fieldOf("result").forGetter(MachineRecipe::result),
                ChanceOutput.CODEC.optionalFieldOf("byproduct").forGetter(MachineRecipe::byproduct),
                Ingredient.CODEC.optionalFieldOf("mold").forGetter(MachineRecipe::mold),
                Codec.intRange(1, 72000).optionalFieldOf("time", 40).forGetter(MachineRecipe::time),
                Codec.intRange(1, 10).optionalFieldOf("min_grade", 1).forGetter(MachineRecipe::minGrade)
        ).apply(i, (in, res, by, mold, time, grade) -> new MachineRecipe(kind, in, res, by, mold, time, grade)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec(RecipeKind kind) {
        return StreamCodec.composite(
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), MachineRecipe::inputs,
                ItemStackTemplate.STREAM_CODEC, MachineRecipe::result,
                ByteBufCodecs.optional(ChanceOutput.STREAM_CODEC), MachineRecipe::byproduct,
                ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), MachineRecipe::mold,
                ByteBufCodecs.VAR_INT, MachineRecipe::time,
                ByteBufCodecs.VAR_INT, MachineRecipe::minGrade,
                (in, res, by, mold, time, grade) -> new MachineRecipe(kind, new ArrayList<>(in), res, by, mold, time, grade));
    }

    /** Convenience for code that only needs a plain ingredient view. */
    public List<Ingredient> plainIngredients() {
        return inputs.stream().map(SizedIngredient::ingredient).toList();
    }
}
