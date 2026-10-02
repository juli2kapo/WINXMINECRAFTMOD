package net.juli2kapo.factoryascent.guide.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.juli2kapo.factoryascent.client.ClientRecipes;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.recipe.ChanceOutput;
import net.juli2kapo.factoryascent.recipe.MachineRecipe;
import net.juli2kapo.factoryascent.recipe.RecipeKind;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import org.jspecify.annotations.Nullable;

/**
 * Recipes for the Manual's recipe pages: crafting (synced by {@code GuideMod}), the machine recipes
 * ({@link ClientRecipes}) and smelting, turned into simple slot lists to draw.
 */
public final class GuideRecipes {
    public enum Kind { CRAFTING, MACHINE, SMELTING }

    /**
     * @param inputs   one list of alternatives per slot (empty list = empty slot); crafting: width × height, row by row
     * @param machines what runs it (crafting table, furnace or the machines of the kind)
     * @param note     time / grade line, or ""
     */
    public record View(Kind kind, int width, int height, List<List<ItemStack>> inputs, ItemStack output,
                       List<ItemStack> extras, List<ItemStack> machines, String note) {}

    private static @Nullable RecipeMap crafting;

    private GuideRecipes() {}

    static void onRecipesReceived(RecipesReceivedEvent event) {
        if (event.getRecipeTypes().contains(RecipeType.CRAFTING)) crafting = event.getRecipeMap();
    }

    private static List<ItemStack> alternatives(Ingredient ingredient, int count) {
        List<ItemStack> list = new ArrayList<>();
        ingredient.items().limit(32).forEach(h -> list.add(new ItemStack(h, count)));
        return list;
    }

    /** Every recipe that makes {@code item}. */
    public static List<View> producing(Item item) {
        List<View> out = new ArrayList<>();
        for (View v : all()) if (v.output.is(item)) out.add(v);
        return out;
    }

    /** Every recipe that uses {@code item}. */
    public static List<View> using(Item item) {
        List<View> out = new ArrayList<>();
        for (View v : all()) {
            boolean uses = v.inputs.stream().anyMatch(slot -> slot.stream().anyMatch(s -> s.is(item)));
            if (uses && !v.output.is(item)) out.add(v);
        }
        return out;
    }

    private static @Nullable List<View> cache;
    private static @Nullable RecipeMap cachedFor;

    private static List<View> all() {
        if (cache != null && cachedFor == crafting) return cache;
        List<View> out = new ArrayList<>();
        if (crafting != null) {
            for (RecipeHolder<CraftingRecipe> holder : crafting.byType(RecipeType.CRAFTING)) {
                View v = crafting(holder.value());
                if (v != null) out.add(v);
            }
        }
        for (RecipeKind kind : RecipeKind.VALUES) {
            for (RecipeHolder<MachineRecipe> holder : ClientRecipes.holders(kind)) out.add(machine(holder.value()));
        }
        for (SmeltingRecipe r : ClientRecipes.vanillaSmelting()) {
            ItemStack result = r.assemble(new SingleRecipeInput(ItemStack.EMPTY));
            out.add(new View(Kind.SMELTING, 1, 1, List.of(alternatives(r.input(), 1)), result, List.of(),
                    List.of(new ItemStack(Items.FURNACE)), ""));
        }
        cache = out;
        cachedFor = crafting;
        return out;
    }

    /** Machine recipes arrive after crafting ones on a reload: drop the cache when anything arrives. */
    static void invalidate() {
        cache = null;
    }

    private static @Nullable View crafting(CraftingRecipe recipe) {
        ItemStack table = new ItemStack(Items.CRAFTING_TABLE);
        if (recipe instanceof ShapedRecipe shaped) {
            List<List<ItemStack>> inputs = new ArrayList<>();
            for (Optional<Ingredient> ing : shaped.getIngredients()) inputs.add(ing.map(i -> alternatives(i, 1)).orElse(List.of()));
            return new View(Kind.CRAFTING, shaped.getWidth(), shaped.getHeight(), inputs, shaped.assemble(CraftingInput.EMPTY),
                    List.of(), List.of(table), "");
        }
        if (recipe instanceof ShapelessRecipe shapeless && shapeless.result() != null) {
            List<List<ItemStack>> inputs = new ArrayList<>();
            for (Ingredient ing : shapeless.placementInfo().ingredients()) inputs.add(alternatives(ing, 1));
            int n = inputs.size(), w = n <= 1 ? 1 : n <= 4 ? 2 : 3;
            return new View(Kind.CRAFTING, w, (n + w - 1) / w, inputs, shapeless.result().create(), List.of(), List.of(table), "");
        }
        return null;
    }

    private static View machine(MachineRecipe r) {
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (var sized : r.inputs()) inputs.add(alternatives(sized.ingredient(), sized.count()));
        r.mold().ifPresent(m -> inputs.add(alternatives(m, 1)));
        List<ItemStack> extras = new ArrayList<>();
        r.byproduct().map(ChanceOutput::item).ifPresent(t -> extras.add(t.create()));
        for (ChanceOutput c : r.extras()) extras.add(c.item().create());
        List<ItemStack> machines = new ArrayList<>();
        for (MachineType type : MachineType.VALUES) {
            if (type.recipeKind() == r.kind() && type.grade() >= r.minGrade()) machines.add(new ItemStack(ModBlocks.machine(type).get()));
        }
        String note = String.format("%.1f s", r.time() / 20f);
        return new View(Kind.MACHINE, inputs.size(), 1, inputs, r.result().create(), extras, machines, note);
    }
}
