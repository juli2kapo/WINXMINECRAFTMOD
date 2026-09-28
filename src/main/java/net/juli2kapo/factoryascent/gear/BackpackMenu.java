package net.juli2kapo.factoryascent.gear;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The open backpack: its 27 slots over the player's inventory. Every change is written straight
 * back into the held backpack's container component, and the backpack itself is locked in place
 * while it is open (you can't move, drop or swap it out from under the menu).
 */
public class BackpackMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 168;
    public static final int GRID_X = 8, GRID_Y = 18, PLAYER_INV_Y = 86;

    private final Player player;
    private final InteractionHand hand;
    private final int lockedSlot;
    private final SimpleContainer contents = new SimpleContainer(BackpackItem.SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            save();
        }
    };
    private boolean loading;

    public BackpackMenu(int id, Inventory inventory, InteractionHand hand) {
        super(GearContent.BACKPACK_MENU.get(), id);
        this.player = inventory.player;
        this.hand = hand;
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : -1;
        load();
        for (int i = 0; i < BackpackItem.SLOTS; i++) {
            addSlot(new Slot(contents, i, GRID_X + (i % 9) * 18, GRID_Y + (i / 9) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return BackpackItem.fits(stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            final int index = col;
            addSlot(new Slot(inventory, col, 8 + col * 18, PLAYER_INV_Y + 58) {
                @Override
                public boolean mayPickup(Player p) {
                    return index != lockedSlot && super.mayPickup(p);
                }
            });
        }
    }

    public static BackpackMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new BackpackMenu(id, inventory, buf.readEnum(InteractionHand.class));
    }

    private ItemStack backpack() {
        return player.getItemInHand(hand);
    }

    private void load() {
        loading = true;
        NonNullList<ItemStack> items = NonNullList.withSize(BackpackItem.SLOTS, ItemStack.EMPTY);
        backpack().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        for (int i = 0; i < items.size(); i++) contents.setItem(i, items.get(i));
        loading = false;
    }

    private void save() {
        if (loading || player.level().isClientSide()) return;
        ItemStack bag = backpack();
        if (!(bag.getItem() instanceof BackpackItem)) return;
        bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents.getItems()));
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player p) {
        // Number keys and the offhand key can't swap the open backpack out of its slot either.
        if (input == ContainerInput.SWAP && (button == lockedSlot || (hand == InteractionHand.OFF_HAND && button == 40))) return;
        super.clicked(slotIndex, button, input, p);
    }

    @Override
    public boolean stillValid(Player p) {
        return backpack().getItem() instanceof BackpackItem;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        save();
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int bag = BackpackItem.SLOTS;
        if (index < bag) {
            if (!moveItemStackTo(stack, bag, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!BackpackItem.fits(stack) || !moveItemStackTo(stack, 0, bag, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
