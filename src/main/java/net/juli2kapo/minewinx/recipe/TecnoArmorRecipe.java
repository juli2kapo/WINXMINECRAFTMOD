package net.juli2kapo.minewinx.recipe;

import com.google.gson.JsonObject;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * Receta con forma de la armadura Tecno que solo "matchea" si quien fabrica
 * puede usarla (Tecnología al nivel máximo). Así el resultado ni aparece:
 * no se consumen materiales ni se borra nada, con click normal o shift-click.
 */
public class TecnoArmorRecipe extends ShapedRecipe {

    // Nombres SRG: funcionan tanto en desarrollo como en el jar final.
    private static final Field CONTAINER_MENU = ObfuscationReflectionHelper.findField(TransientCraftingContainer.class, "f_286998_");
    private static final Field CRAFTING_MENU_PLAYER = ObfuscationReflectionHelper.findField(CraftingMenu.class, "f_39351_");
    private static final Field INVENTORY_MENU_OWNER = ObfuscationReflectionHelper.findField(InventoryMenu.class, "f_39703_");

    public TecnoArmorRecipe(ShapedRecipe base) {
        super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(),
                base.getIngredients(), base.getResultItem(RegistryAccess.EMPTY), base.showNotification());
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (!super.matches(container, level)) return false;
        if (level.isClientSide()) return true;
        Player player = craftingPlayer(container);
        if (player != null && PlayerDataProvider.canUseTecnoArmor(player)) return true;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.minewinx.tecno_armor.craft_denied"), true);
        }
        return false;
    }

    @Nullable
    private static Player craftingPlayer(CraftingContainer container) {
        try {
            if (!(container instanceof TransientCraftingContainer)) return null;
            AbstractContainerMenu menu = (AbstractContainerMenu) CONTAINER_MENU.get(container);
            if (menu instanceof CraftingMenu) return (Player) CRAFTING_MENU_PLAYER.get(menu);
            if (menu instanceof InventoryMenu) return (Player) INVENTORY_MENU_OWNER.get(menu);
        } catch (IllegalAccessException | ClassCastException ignored) {
        }
        return null;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TECNO_ARMOR.get();
    }

    /** Mismo formato JSON/red que una receta con forma común. */
    public static class Serializer implements RecipeSerializer<TecnoArmorRecipe> {
        @Override
        public TecnoArmorRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new TecnoArmorRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public @Nullable TecnoArmorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf);
            return base == null ? null : new TecnoArmorRecipe(base);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, TecnoArmorRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
        }
    }
}
