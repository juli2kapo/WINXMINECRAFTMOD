package net.juli2kapo.factoryascent.mobs;

import java.util.List;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/** GameTests for mounting and removing a size ray's scope (registered in ModGameTests.TESTS). */
public final class SizeRayRecipeTests {
    private SizeRayRecipeTests() {}

    private static RecipeHolder<CraftingRecipe> recipe(GameTestHelper h, CraftingInput input, String what) {
        return h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel())
                .orElseThrow(() -> h.assertionException(Component.literal("no crafting recipe matched " + what)));
    }

    /** Ray + spyglass gives the scoped ray with its energy; the scoped ray alone gives it back plus the spyglass. */
    public static void scopeRoundTrip(GameTestHelper h) {
        ItemStack ray = new ItemStack(MobContent.MAXIMIZER_RAY.get());
        ray.set(ModComponents.ENERGY.get(), 12_345);

        CraftingInput attach = CraftingInput.of(2, 1, List.of(ray.copy(), new ItemStack(Items.SPYGLASS)));
        ItemStack scoped = recipe(h, attach, "ray + spyglass").value().assemble(attach);
        h.assertTrue(scoped.is(MobContent.MAXIMIZER_RAY.get()), "attach should give the same ray, got " + scoped);
        h.assertTrue(SizeRayItem.isScoped(scoped), "attach should set factoryascent:scoped");
        h.assertValueEqual(scoped.getOrDefault(ModComponents.ENERGY.get(), 0), 12_345, "energy after attaching");

        CraftingInput twice = CraftingInput.of(2, 1, List.of(scoped.copy(), new ItemStack(Items.SPYGLASS)));
        h.assertTrue(h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, twice, h.getLevel()).isEmpty(),
                "a scoped ray must not take a second scope");

        CraftingInput detach = CraftingInput.of(1, 1, List.of(scoped.copy()));
        RecipeHolder<CraftingRecipe> unscope = recipe(h, detach, "scoped ray alone");
        ItemStack bare = unscope.value().assemble(detach);
        h.assertTrue(bare.is(MobContent.MAXIMIZER_RAY.get()) && !SizeRayItem.isScoped(bare), "unscope should give the bare ray");
        h.assertValueEqual(bare.getOrDefault(ModComponents.ENERGY.get(), 0), 12_345, "energy after unscoping");
        NonNullList<ItemStack> left = unscope.value().getRemainingItems(detach);
        h.assertTrue(left.get(0).is(Items.SPYGLASS), "the spyglass should stay in the grid");
        h.succeed();
    }
}
