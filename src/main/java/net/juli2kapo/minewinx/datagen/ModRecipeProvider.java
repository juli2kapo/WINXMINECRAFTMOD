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
        buildCenteredCraftingRecipe(pWriter, ModItems.NATURESTAGE1.get(), ModItems.LOWQMANACRYSTAL.get(), Items.WHEAT_SEEDS);
        buildCenteredCraftingRecipe(pWriter, ModItems.NATURESTAGE2.get(), ModItems.MEDIUMQMANACRYSTAL.get(), Items.SCUTE);
        // Retoño del Origen: 9 saplings de cualquier tipo (vía tag, se pueden mezclar)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ORIGIN_SAPLING.get())
                .pattern("###")
                .pattern("###")
                .pattern("###")
                .define('#', net.minecraft.tags.ItemTags.SAPLINGS)
                .unlockedBy("has_sapling", has(net.minecraft.tags.ItemTags.SAPLINGS))
                .save(pWriter);
        buildCenteredCraftingRecipe(pWriter, ModItems.NATURESTAGE3.get(), ModItems.HIGHQMANACRYSTAL.get(), ModItems.ORIGIN_SAPLING.get());
        buildCenteredCraftingRecipe(pWriter, ModItems.ICESTAGE1.get(), ModItems.LOWQMANACRYSTAL.get(), Items.SNOW_BLOCK);
        buildCenteredCraftingRecipe(pWriter, ModItems.ICESTAGE2.get(), ModItems.MEDIUMQMANACRYSTAL.get(), Items.BLUE_ICE);
        // Hielo del End: combinación de hielo + End; centro de la receta de ice stage 3
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModBlocks.END_ICE.get())
                .requires(Items.ICE)
                .requires(Items.END_STONE)
                .unlockedBy(getHasName(Items.END_STONE), has(Items.END_STONE))
                .save(pWriter);
        buildCenteredCraftingRecipe(pWriter, ModItems.ICESTAGE3.get(), ModItems.HIGHQMANACRYSTAL.get(), ModBlocks.END_ICE.get());
        buildCenteredCraftingRecipe(pWriter, ModItems.STORMSTAGE1.get(), ModItems.LOWQMANACRYSTAL.get(), Items.LIGHTNING_ROD);
        buildCenteredCraftingRecipe(pWriter, ModItems.STORMSTAGE2.get(), ModItems.MEDIUMQMANACRYSTAL.get(), Items.PHANTOM_MEMBRANE);
        buildCenteredCraftingRecipe(pWriter, ModItems.STORMSTAGE3.get(), ModItems.HIGHQMANACRYSTAL.get(), Items.TRIDENT);

        buildCenteredCraftingRecipe(pWriter, ModItems.MUSICSTAGE1.get(), ModItems.LOWQMANACRYSTAL.get(), Items.NOTE_BLOCK);
        buildCenteredCraftingRecipe(pWriter, ModItems.MUSICSTAGE2.get(), ModItems.MEDIUMQMANACRYSTAL.get(), Items.JUKEBOX);
        buildCenteredCraftingRecipe(pWriter, ModItems.MUSICSTAGE3.get(), ModItems.HIGHQMANACRYSTAL.get(), net.minecraft.tags.ItemTags.MUSIC_DISCS);
        buildCenteredCraftingRecipe(pWriter, ModItems.TECHNOLOGYSTAGE1.get(), ModItems.LOWQMANACRYSTAL.get(), Items.REDSTONE);
        buildCenteredCraftingRecipe(pWriter, ModItems.TECHNOLOGYSTAGE2.get(), ModItems.MEDIUMQMANACRYSTAL.get(), Items.COMPARATOR);
        // Final difícil: la brújula de recuperación pide fragmentos de eco (Ciudad Antigua)
        buildCenteredCraftingRecipe(pWriter, ModItems.TECHNOLOGYSTAGE3.get(), ModItems.HIGHQMANACRYSTAL.get(), Items.RECOVERY_COMPASS);
        buildCenteredCraftingRecipe(pWriter, ModItems.SUNANDMOONSTAGE1.get(), ModItems.LOWQMANACRYSTAL.get(), Items.GLOWSTONE);
        buildCenteredCraftingRecipe(pWriter, ModItems.SUNANDMOONSTAGE2.get(), ModItems.MEDIUMQMANACRYSTAL.get(), Items.DAYLIGHT_DETECTOR);
        buildCenteredCraftingRecipe(pWriter, ModItems.SUNANDMOONSTAGE3.get(), ModItems.HIGHQMANACRYSTAL.get(), Items.BEACON);
        tecnoArmor(pWriter);
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
    /** Variante con tag en el centro (p. ej. cualquier disco de música). */
    private void buildCenteredCraftingRecipe(Consumer<FinishedRecipe> writer, ItemLike result, ItemLike outerMaterial, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> centerTag) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern("###")
                .pattern("#S#")
                .pattern("###")
                .define('#', outerMaterial)
                .define('S', centerTag)
                .unlockedBy(getHasName(outerMaterial), has(outerMaterial))
                .save(writer);
    }

    private void tecnoArmor(Consumer<FinishedRecipe> writer){
        tecnoArmorPiece(writer, ModItems.TECNO_HELMET.get(), Items.NETHERITE_HELMET, Items.DIAMOND_HELMET);
        tecnoArmorPiece(writer, ModItems.TECNO_CHESTPLATE.get(), Items.NETHERITE_CHESTPLATE, Items.DIAMOND_CHESTPLATE);
        tecnoArmorPiece(writer, ModItems.TECNO_LEGGINGS.get(), Items.NETHERITE_LEGGINGS, Items.DIAMOND_LEGGINGS);
        tecnoArmorPiece(writer, ModItems.TECNO_BOOTS.get(), Items.NETHERITE_BOOTS, Items.DIAMOND_BOOTS);
    }

    private void tecnoArmorPiece(Consumer<FinishedRecipe> writer, ItemLike result, ItemLike netheritePiece, ItemLike diamondPiece){
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, result)
                .pattern("   ")
                .pattern("#SC")
                .pattern("   ")
                .define('#', netheritePiece)
                .define('S', Items.NETHER_STAR)
                .define('C', diamondPiece)
                .unlockedBy(getHasName(Items.NETHER_STAR), has(Items.NETHER_STAR))
                // Tipo propio: solo se puede fabricar con Tecnología al nivel máximo
                .save(recipe -> writer.accept(new TecnoArmorFinishedRecipe(recipe)));
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


    /** Igual que la receta con forma generada, pero con el tipo minewinx:tecno_armor. */
    private record TecnoArmorFinishedRecipe(FinishedRecipe base) implements FinishedRecipe {
        @Override
        public void serializeRecipeData(com.google.gson.JsonObject json) {
            base.serializeRecipeData(json);
        }

        @Override
        public net.minecraft.resources.ResourceLocation getId() {
            return base.getId();
        }

        @Override
        public RecipeSerializer<?> getType() {
            return net.juli2kapo.minewinx.recipe.ModRecipes.TECNO_ARMOR.get();
        }

        @Override
        public @org.jetbrains.annotations.Nullable com.google.gson.JsonObject serializeAdvancement() {
            return base.serializeAdvancement();
        }

        @Override
        public @org.jetbrains.annotations.Nullable net.minecraft.resources.ResourceLocation getAdvancementId() {
            return base.getAdvancementId();
        }
    }
}
