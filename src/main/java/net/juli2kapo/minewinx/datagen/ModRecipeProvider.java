package net.juli2kapo.minewinx.datagen;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.block.ModBlocks;
import net.juli2kapo.minewinx.item.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.data.recipes.ShapedRecipeBuilder;

import java.util.List;
import java.util.function.Consumer;

public class ModRecipeProvider extends RecipeProvider {
    private static final List<ItemLike> MANA_SMELTABLES = List.of(ModItems.LOWQMANACRYSTAL.get(), ModBlocks.MANA_CRYSTAL_ORE.get(), ModBlocks.DEEPSLATE_MANA_CRYSTAL_ORE.get());
    public ModRecipeProvider(PackOutput pOutput) {
        super(pOutput);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> pWriter) {
        oreBlasting(pWriter, MANA_SMELTABLES, RecipeCategory.MISC, ModItems.MEDIUMQMANACRYSTAL.get(), 0.25f, 500, "mana_crystals");
        oreSmelting(pWriter, MANA_SMELTABLES, RecipeCategory.MISC, ModItems.MEDIUMQMANACRYSTAL.get(), 0.25f, 800, "mana_crystals");

        createCompactRecipe(pWriter, ModItems.LOWQMANACRYSTAL.get(), ModItems.MEDIUMQMANACRYSTAL.get());
        createCompactRecipe(pWriter, ModItems.MEDIUMQMANACRYSTAL.get(), ModItems.HIGHQMANACRYSTAL.get());

        buildCenteredCraftingRecipe(pWriter, ModItems.FIRESTAGE1.get(), ModItems.LOWQMANACRYSTAL.get(), Items.FIRE_CHARGE);
        buildCenteredCraftingRecipe(pWriter, ModItems.FIRESTAGE2.get(), ModItems.MEDIUMQMANACRYSTAL.get(), Items.MAGMA_CREAM);
        buildCenteredCraftingRecipe(pWriter, ModItems.FIRESTAGE3.get(), ModItems.HIGHQMANACRYSTAL.get(), Items.DRAGON_BREATH);
        buildCenteredCraftingRecipe(pWriter, ModItems.WATERSTAGE1.get(), ModItems.LOWQMANACRYSTAL.get(), Items.KELP);
        buildCenteredCraftingRecipe(pWriter, ModItems.WATERSTAGE2.get(), ModItems.MEDIUMQMANACRYSTAL.get(), Items.PRISMARINE_SHARD);
        buildCenteredCraftingRecipe(pWriter, ModItems.WATERSTAGE3.get(), ModItems.HIGHQMANACRYSTAL.get(), Items.HEART_OF_THE_SEA);
        buildCenteredCraftingRecipe(pWriter, ModItems.MANARADAR.get(), ModItems.LOWQMANACRYSTAL.get(), Items.COMPASS);
    }

    private void createCompactRecipe(Consumer<FinishedRecipe> writer, ItemLike input, ItemLike output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, output)
                .pattern("###")
                .pattern("###")
                .pattern("###")
                .define('#', input)
                .unlockedBy(getHasName(input), has(input))
                .save(writer);
    }

    private void buildCenteredCraftingRecipe(Consumer<FinishedRecipe> writer, ItemLike result, ItemLike outerMaterial, ItemLike centerItem) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern("###")
                .pattern("#S#")
                .pattern("###")
                .define('#', outerMaterial)
                .define('S', centerItem)
                .unlockedBy(getHasName(outerMaterial), has(outerMaterial))
                .save(writer);
    }


    protected static void oreSmelting(Consumer<FinishedRecipe> pFinishedRecipeConsumer, List<ItemLike> pIngredients, RecipeCategory pCategory, ItemLike pResult, float pExperience, int pCookingTIme, String pGroup) {
        oreCooking(pFinishedRecipeConsumer, RecipeSerializer.SMELTING_RECIPE, pIngredients, pCategory, pResult, pExperience, pCookingTIme, pGroup, "_from_smelting");
    }

    protected static void oreBlasting(Consumer<FinishedRecipe> pFinishedRecipeConsumer, List<ItemLike> pIngredients, RecipeCategory pCategory, ItemLike pResult, float pExperience, int pCookingTime, String pGroup) {
        oreCooking(pFinishedRecipeConsumer, RecipeSerializer.BLASTING_RECIPE, pIngredients, pCategory, pResult, pExperience, pCookingTime, pGroup, "_from_blasting");
    }

    protected static void oreCooking(Consumer<FinishedRecipe> pFinishedRecipeConsumer, RecipeSerializer<? extends AbstractCookingRecipe> pCookingSerializer, List<ItemLike> pIngredients, RecipeCategory pCategory, ItemLike pResult, float pExperience, int pCookingTime, String pGroup, String pRecipeName) {
        for(ItemLike itemlike : pIngredients) {
            SimpleCookingRecipeBuilder.generic(Ingredient.of(itemlike), pCategory, pResult, pExperience, pCookingTime, pCookingSerializer)
                    .group(pGroup).unlockedBy(getHasName(itemlike), has(itemlike))
                    .save(pFinishedRecipeConsumer, MineWinx.MOD_ID + ":" + getItemName(pResult) + pRecipeName + "_" + getItemName(itemlike));
        }
    }

}
