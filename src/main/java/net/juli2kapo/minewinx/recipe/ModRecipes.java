package net.juli2kapo.minewinx.recipe;

import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MineWinx.MOD_ID);

    public static final RegistryObject<RecipeSerializer<TecnoArmorRecipe>> TECNO_ARMOR =
            SERIALIZERS.register("tecno_armor", TecnoArmorRecipe.Serializer::new);

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
