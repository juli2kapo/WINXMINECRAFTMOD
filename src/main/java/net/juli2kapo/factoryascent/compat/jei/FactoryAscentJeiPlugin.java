package net.juli2kapo.factoryascent.compat.jei;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.client.ClientRecipes;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.recipe.MachineRecipe;
import net.juli2kapo.factoryascent.recipe.RecipeKind;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.juli2kapo.factoryascent.registry.ModRecipes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Optional JEI integration: a recipe category per machine kind (crushing, pressing, alloying…),
 * with every machine that runs it as a catalyst, so "R" on quantum alloy shows the Alloy Smelter
 * recipe. Only loaded when JEI is installed; the mod never needs it.
 */
@JeiPlugin
public final class FactoryAscentJeiPlugin implements IModPlugin {
    private static final Map<RecipeKind, IRecipeHolderType<MachineRecipe>> TYPES = new EnumMap<>(RecipeKind.class);

    private static IRecipeHolderType<MachineRecipe> type(RecipeKind kind) {
        return TYPES.computeIfAbsent(kind, k -> IRecipeHolderType.create(
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, k.id())));
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (RecipeKind kind : RecipeKind.VALUES) {
            if (MachineRecipeCategory.machines(kind).isEmpty()) continue;
            registration.addRecipeCategories(new MachineRecipeCategory(gui, kind, type(kind)));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (RecipeKind kind : RecipeKind.VALUES) {
            if (MachineRecipeCategory.machines(kind).isEmpty()) continue;
            List<RecipeHolder<MachineRecipe>> recipes = new ArrayList<>(ClientRecipes.holders(kind));
            recipes.sort(java.util.Comparator.comparingInt((RecipeHolder<MachineRecipe> h) -> h.value().minGrade())
                    .thenComparing(h -> h.id().identifier().toString()));
            registration.addRecipes(type(kind), recipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineType machine : MachineType.VALUES) {
            RecipeKind kind = machine.recipeKind();
            if (kind == null) continue;
            IRecipeType<?> type = type(kind);
            registration.addCraftingStation(type, ModBlocks.machine(machine).get());
        }
        // Electric smelters also run every vanilla smelting recipe.
        for (MachineType machine : MachineType.VALUES) {
            if (machine.runsVanillaSmelting()) registration.addCraftingStation(RecipeTypes.SMELTING, ModBlocks.machine(machine).get());
        }
    }
}
