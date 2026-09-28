package net.juli2kapo.factoryascent.space;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.TransmuteRecipe;

/**
 * Astronaut Suit chestplate + Advanced Jetpack → Jet Suit. A transmute recipe (the suit's
 * components, air included, carry over; recipe book and JEI show it like any transmute) that also
 * brings the jetpack's charge and hover setting along.
 */
public class JetSuitRecipe extends TransmuteRecipe {
    private final Ingredient suit;
    private final Ingredient jetpack;
    private final ItemStackTemplate result;

    public JetSuitRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, Ingredient suit,
                         Ingredient jetpack, ItemStackTemplate result) {
        super(commonInfo, bookInfo, suit, jetpack, DEFAULT_MATERIAL_COUNT, result, false);
        this.suit = suit;
        this.jetpack = jetpack;
        this.result = result;
    }

    private static final MapCodec<JetSuitRecipe> OWN_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
            Ingredient.CODEC.fieldOf("input").forGetter(o -> o.suit),
            Ingredient.CODEC.fieldOf("material").forGetter(o -> o.jetpack),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result)
    ).apply(i, JetSuitRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, JetSuitRecipe> OWN_STREAM = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
            Ingredient.CONTENTS_STREAM_CODEC, o -> o.suit,
            Ingredient.CONTENTS_STREAM_CODEC, o -> o.jetpack,
            ItemStackTemplate.STREAM_CODEC, o -> o.result,
            JetSuitRecipe::new);

    public static final RecipeSerializer<TransmuteRecipe> SERIALIZER = new RecipeSerializer<>(
            OWN_CODEC.xmap(r -> (TransmuteRecipe) r, r -> (JetSuitRecipe) r),
            OWN_STREAM.map(r -> (TransmuteRecipe) r, r -> (JetSuitRecipe) r));

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack out = super.assemble(input);
        if (out.isEmpty()) return out;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty() && jetpack.test(stack)) {
                int max = Jetpack.tier(out).capacity;
                out.set(ModComponents.ENERGY.get(), Math.min(max, Jetpack.energy(stack)));
                if (Jetpack.hover(stack)) out.set(SpaceContent.JETPACK_HOVER.get(), true);
                break;
            }
        }
        return out;
    }

    @Override
    public RecipeSerializer<TransmuteRecipe> getSerializer() {
        return SpaceContent.JET_SUIT_RECIPE.get();
    }
}
