package net.juli2kapo.factoryascent.guide.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Optional JEI hook of the Manual: keeps the JEI runtime so the book's item links can open JEI's
 * recipes (or uses) of an item. Only loaded by JEI; the Manual checks that JEI is there first.
 */
@JeiPlugin
public final class GuideJeiPlugin implements IModPlugin {
    private static @Nullable IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "guide");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Opens JEI on the item's recipes ({@code uses}: on what it is used in). False if JEI isn't ready. */
    public static boolean show(ItemStack stack, boolean uses) {
        IJeiRuntime rt = runtime;
        if (rt == null || stack.isEmpty()) return false;
        var focus = rt.getJeiHelpers().getFocusFactory().createFocus(
                uses ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
        rt.getRecipesGui().show(focus);
        return true;
    }
}
