package net.juli2kapo.factoryascent.registry;

import java.util.EnumMap;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.recipe.MachineRecipe;
import net.juli2kapo.factoryascent.recipe.RecipeKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, FactoryAscent.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, FactoryAscent.MOD_ID);

    private static final EnumMap<RecipeKind, Supplier<RecipeType<MachineRecipe>>> TYPE_BY_KIND = new EnumMap<>(RecipeKind.class);
    private static final EnumMap<RecipeKind, Supplier<RecipeSerializer<MachineRecipe>>> SERIALIZER_BY_KIND =
            new EnumMap<>(RecipeKind.class);

    static {
        for (RecipeKind kind : RecipeKind.VALUES) {
            TYPE_BY_KIND.put(kind, TYPES.register(kind.id(),
                    () -> RecipeType.simple(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, kind.id()))));
            SERIALIZER_BY_KIND.put(kind, SERIALIZERS.register(kind.id(),
                    () -> new RecipeSerializer<>(MachineRecipe.codec(kind), MachineRecipe.streamCodec(kind))));
        }
    }

    public static RecipeType<MachineRecipe> type(RecipeKind kind) {
        return TYPE_BY_KIND.get(kind).get();
    }

    public static RecipeSerializer<MachineRecipe> serializer(RecipeKind kind) {
        return SERIALIZER_BY_KIND.get(kind).get();
    }

    private ModRecipes() {}
}
