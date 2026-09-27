package net.juli2kapo.factoryascent.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.recipe.MachineRecipe;
import net.juli2kapo.factoryascent.recipe.RecipeKind;
import net.juli2kapo.factoryascent.registry.ModRecipes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import org.jspecify.annotations.Nullable;

/**
 * The machine recipes the server sent us (see {@code OnDatapackSyncEvent.sendRecipes}). Answers
 * "which machines can use this item?" for tooltips and "does this machine accept this?" for GUIs.
 */
public final class ClientRecipes {
    private static @Nullable RecipeMap recipes;
    private static final Map<Item, List<MachineType>> USED_IN = new IdentityHashMap<>();

    private ClientRecipes() {}

    public static void onRecipesReceived(RecipesReceivedEvent event) {
        recipes = event.getRecipeMap();
        USED_IN.clear();
    }

    public static boolean ready() {
        return recipes != null;
    }

    /** Recipes of a machine's kind that the machine is good enough to run, then the ones it isn't. */
    public static List<MachineRecipe> recipesFor(MachineType type) {
        List<MachineRecipe> list = new ArrayList<>();
        RecipeKind kind = type.recipeKind();
        if (recipes == null || kind == null) return list;
        for (RecipeHolder<MachineRecipe> holder : recipes.byType(ModRecipes.type(kind))) list.add(holder.value());
        list.sort(Comparator.comparingInt((MachineRecipe r) -> r.minGrade() > type.grade() ? 1 : 0)
                .thenComparing(r -> r.result().item().getRegisteredName()));
        return list;
    }

    /** Every synced recipe of a kind, whatever machine grade it needs (used by the JEI plugin). */
    public static List<RecipeHolder<MachineRecipe>> holders(RecipeKind kind) {
        if (recipes == null) return List.of();
        return List.copyOf(recipes.byType(ModRecipes.type(kind)));
    }

    public static List<SmeltingRecipe> vanillaSmelting() {
        List<SmeltingRecipe> list = new ArrayList<>();
        if (recipes == null) return list;
        for (RecipeHolder<SmeltingRecipe> holder : recipes.byType(RecipeType.SMELTING)) list.add(holder.value());
        return list;
    }

    /** Whether this machine has a recipe (within its grade) that uses the item. */
    public static boolean accepts(MachineType type, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (recipes == null) return true; // unknown: don't nag
        return usedIn(stack).contains(type);
    }

    /** Every machine that can use this item, in age order. */
    public static List<MachineType> usedIn(ItemStack stack) {
        if (recipes == null || stack.isEmpty()) return List.of();
        return USED_IN.computeIfAbsent(stack.getItem(), item -> {
            List<MachineType> machines = new ArrayList<>();
            for (MachineType type : MachineType.VALUES) {
                RecipeKind kind = type.recipeKind();
                if (kind == null) continue;
                boolean used = false;
                for (RecipeHolder<MachineRecipe> holder : recipes.byType(ModRecipes.type(kind))) {
                    MachineRecipe r = holder.value();
                    if (r.minGrade() <= type.grade() && r.accepts(stack)) {
                        used = true;
                        break;
                    }
                }
                if (!used && type == MachineType.ELECTRIC_FURNACE) {
                    for (RecipeHolder<SmeltingRecipe> holder : recipes.byType(RecipeType.SMELTING)) {
                        if (holder.value().input().test(stack)) {
                            used = true;
                            break;
                        }
                    }
                }
                if (used) machines.add(type);
            }
            return machines;
        });
    }
}
