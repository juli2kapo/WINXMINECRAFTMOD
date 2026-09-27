package net.juli2kapo.factoryascent.storagenet.client;

import java.util.List;
import net.juli2kapo.factoryascent.storagenet.CellContents;
import net.juli2kapo.factoryascent.storagenet.DriveMenu;
import net.juli2kapo.factoryascent.storagenet.StorageCellItem;
import net.juli2kapo.factoryascent.storagenet.StorageDriveBlockEntity;
import net.juli2kapo.factoryascent.storagenet.StorageFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Four cell slots, each with a fill bar underneath (green, turning red as the cell fills up). */
public class DriveScreen extends AbstractContainerScreen<DriveMenu> {
    private static final int BAR_Y = DriveMenu.CELL_Y + 19;

    public DriveScreen(DriveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, DriveMenu.WIDTH, DriveMenu.HEIGHT);
        this.inventoryLabelY = DriveMenu.PLAYER_INV_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        StorageGui.panel(g, x, y, imageWidth, imageHeight);
        for (int i = 0; i < StorageDriveBlockEntity.SLOTS; i++) {
            int sx = x + DriveMenu.CELL_X + i * 18, sy = y + DriveMenu.CELL_Y;
            StorageGui.slot(g, sx, sy);
            g.fill(sx - 1, y + BAR_Y, sx + 17, y + BAR_Y + 4, StorageGui.SLOT_DARK);
            ItemStack cell = menu.getSlot(i).getItem();
            if (cell.getItem() instanceof StorageCellItem item) {
                float f = Math.min(1f, StorageCellItem.contents(cell).total() / (float) item.capacity());
                int w = Math.round(16 * f);
                int color = f < 0.75f ? 0xFF3FD13F : f < 1f ? 0xFFE0C040 : 0xFFD03030;
                if (w > 0) g.fill(sx, y + BAR_Y + 1, sx + w, y + BAR_Y + 3, color);
            }
        }
        StorageGui.playerInventory(g, x, y, DriveMenu.PLAYER_INV_Y);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        for (int i = 0; i < StorageDriveBlockEntity.SLOTS; i++) {
            int sx = DriveMenu.CELL_X + i * 18;
            ItemStack cell = menu.getSlot(i).getItem();
            if (cell.getItem() instanceof StorageCellItem item && isHovering(sx - 1, BAR_Y, 18, 4, mouseX, mouseY)) {
                CellContents c = StorageCellItem.contents(cell);
                g.setComponentTooltipForNextFrame(font, List.of(
                        Component.translatable("tooltip.factoryascent.storage_cell.used",
                                StorageFormat.compact(c.total()), StorageFormat.compact(item.capacity())),
                        Component.translatable("tooltip.factoryascent.storage_cell.types", c.types(), item.maxTypes())),
                        mouseX, mouseY);
            }
        }
    }
}
