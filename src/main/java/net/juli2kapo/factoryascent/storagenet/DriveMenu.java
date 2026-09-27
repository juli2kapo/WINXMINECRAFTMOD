package net.juli2kapo.factoryascent.storagenet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/** The drive's four cell slots plus the player inventory. */
public class DriveMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, HEIGHT = 150;
    public static final int CELL_X = 53, CELL_Y = 24;
    public static final int PLAYER_INV_Y = 68;

    private final StorageDriveBlockEntity drive;

    public DriveMenu(int id, Inventory playerInventory, StorageDriveBlockEntity drive) {
        super(StorageContent.DRIVE_MENU.get(), id);
        this.drive = drive;
        StorageDriveBlockEntity.CellSlots cells = drive.cellSlots();
        for (int i = 0; i < StorageDriveBlockEntity.SLOTS; i++) {
            addSlot(new ResourceHandlerSlot(cells, cells::set, i, CELL_X + i * 18, CELL_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return StorageCellItem.isCell(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public int getMaxStackSize(ItemStack stack) {
                    return 1;
                }
            });
        }
        addStandardInventorySlots(playerInventory, 8, PLAYER_INV_Y);
    }

    public static DriveMenu fromNetwork(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof StorageDriveBlockEntity drive) {
            return new DriveMenu(id, playerInventory, drive);
        }
        throw new IllegalStateException("No storage drive at " + pos);
    }

    public StorageDriveBlockEntity drive() {
        return drive;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(drive, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int cells = StorageDriveBlockEntity.SLOTS;
        int end = slots.size();
        if (index < cells) {
            if (!moveItemStackTo(stack, cells, end, true)) return ItemStack.EMPTY;
        } else if (StorageCellItem.isCell(stack)) {
            if (!moveItemStackTo(stack, 0, cells, false)) return ItemStack.EMPTY;
        } else {
            int hotbar = end - 9;
            if (index < hotbar) {
                if (!moveItemStackTo(stack, hotbar, end, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, cells, hotbar, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
