package net.juli2kapo.factoryascent.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** The input slots of a processing machine, in slot order. */
public record MachineRecipeInput(List<ItemStack> items, int tier) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public int size() {
        return items.size();
    }
}
