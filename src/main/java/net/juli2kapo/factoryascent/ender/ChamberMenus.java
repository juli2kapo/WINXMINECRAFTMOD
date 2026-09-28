package net.juli2kapo.factoryascent.ender;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** What the Ender Anchor and Ender Beacon menus share: layout, owner names, shift-click. */
public final class ChamberMenus {
    public static final int WIDTH = 196, HEIGHT = 190;
    public static final int INV_X = 18, PLAYER_INV_Y = 108;
    /** The pearl slot, inside the drawn chamber on the left. */
    public static final int PEARL_X = 18, PEARL_Y = 34;

    private ChamberMenus() {}

    /** A player's name for the screen: online name, else the server's name cache, else "?". */
    public static String ownerName(MinecraftServer server, @Nullable UUID owner) {
        if (owner == null) return "";
        ServerPlayer online = server.getPlayerList().getPlayer(owner);
        if (online != null) return online.getGameProfile().name();
        try {
            return server.services().nameToIdCache().get(owner).map(NameAndId::name).orElse("?");
        } catch (RuntimeException e) {
            return "?";
        }
    }

    /** Shift-click: pearls from the inventory go into the chamber; nothing comes back out. */
    static ItemStack quickMove(AbstractContainerMenu menu, Player player, int index, java.util.function.BiFunction<ItemStack, int[], Boolean> mover) {
        Slot slot = menu.slots.get(index);
        if (slot == null || !slot.hasItem() || index == 0) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int end = menu.slots.size();
        boolean moved = stack.is(Items.ENDER_PEARL) && mover.apply(stack, new int[] {0, 1});
        if (!moved) {
            int hotbar = end - 9;
            moved = index < hotbar ? mover.apply(stack, new int[] {hotbar, end}) : mover.apply(stack, new int[] {1, hotbar});
        }
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
