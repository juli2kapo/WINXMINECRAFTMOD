package net.juli2kapo.factoryascent.trains;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The screen of a locomotive (cab: fuel slots, gauges, horn/lights/stop) or a wagon (its hold, or a
 * tank gauge). The numbers come from the vehicle's synced entity data, so the menu only carries the
 * slots and the buttons.
 */
public class TrainMenu extends AbstractContainerMenu {
    public static final int PANEL_W = 124;
    public static final int GRID_X = 8, GRID_Y = 18;
    public static final int BUTTON_HORN = 0, BUTTON_LIGHTS = 1, BUTTON_STOP = 2;

    private final RollingStock stock;
    private final int stockSlots;
    private final int rows;

    public TrainMenu(int id, Inventory inventory, RollingStock stock) {
        super(TrainContent.TRAIN_MENU.get(), id);
        this.stock = stock;
        this.stockSlots = stock.inventorySize();
        this.rows = stockSlots >= 9 ? (stockSlots + 8) / 9 : 3;
        Container container = stock instanceof Container c ? c : new SimpleContainer(0);
        for (int i = 0; i < stockSlots; i++) {
            int x = stockSlots >= 9 ? GRID_X + (i % 9) * 18 : smallSlotX(i);
            int y = stockSlots >= 9 ? GRID_Y + (i / 9) * 18 : smallSlotY();
            addSlot(new StockSlot(container, stock, i, x, y));
        }
        addStandardInventorySlots(inventory, 8, playerInvY());
    }

    public static TrainMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        if (inventory.player.level().getEntity(entityId) instanceof RollingStock stock) return new TrainMenu(id, inventory, stock);
        throw new IllegalStateException("No train vehicle with id " + entityId);
    }

    public RollingStock stock() {
        return stock;
    }

    public int slotCount() {
        return stockSlots;
    }

    public int rows() {
        return rows;
    }

    /** Fuel / power slots of a locomotive sit in a row in the middle of the left panel. */
    public int smallSlotX(int i) {
        return 88 - stockSlots * 9 + i * 18;
    }

    public int smallSlotY() {
        return GRID_Y + 30;
    }

    public int playerInvY() {
        return GRID_Y + rows * 18 + 14;
    }

    public int imageWidth() {
        return 176 + PANEL_W;
    }

    public int imageHeight() {
        return playerInvY() + 76 + 7;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(stock instanceof Locomotive loco)) return false;
        switch (id) {
            case BUTTON_HORN -> loco.action(player, Locomotive.ACTION_HORN);
            case BUTTON_LIGHTS -> loco.action(player, Locomotive.ACTION_LIGHTS);
            case BUTTON_STOP -> loco.action(player, Locomotive.ACTION_STOP);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stock.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int end = this.slots.size();
        if (index < stockSlots) {
            if (!moveItemStackTo(stack, stockSlots, end, true)) return ItemStack.EMPTY;
        } else {
            if (stockSlots == 0 || !stock.canPlaceItem(0, stack) || !moveItemStackTo(stack, 0, stockSlots, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    private static final class StockSlot extends Slot {
        private final RollingStock stock;

        StockSlot(Container container, RollingStock stock, int index, int x, int y) {
            super(container, index, x, y);
            this.stock = stock;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stock.canPlaceItem(getContainerSlot(), stack);
        }
    }
}
