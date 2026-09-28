package net.juli2kapo.factoryascent.gear.client;

import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.gear.BackpackItem;
import net.juli2kapo.factoryascent.gear.BackpackMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The open Bronze Backpack: the mod's bevelled panel with the bronze-age accent, a stitched leather flap and 27 slots. */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {
    private static final int BRONZE = 0xFFD08A3A;
    private static final int LEATHER = 0xFF7A4A26, LEATHER_DARK = 0xFF55321A, STITCH = 0xFFE8C890;

    public BackpackScreen(BackpackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BackpackMenu.WIDTH, BackpackMenu.HEIGHT);
        this.inventoryLabelY = BackpackMenu.PLAYER_INV_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        FactoryGui.panel(g, x, y, imageWidth, imageHeight, BRONZE);
        // leather flap behind the bag slots, with a stitched seam and two bronze buckles
        int fx = x + BackpackMenu.GRID_X - 4, fy = y + BackpackMenu.GRID_Y - 4, fw = 9 * 18 + 6, fh = 3 * 18 + 6;
        g.fill(fx, fy, fx + fw, fy + fh, LEATHER_DARK);
        g.fill(fx + 1, fy + 1, fx + fw - 1, fy + fh - 1, LEATHER);
        for (int i = fx + 3; i < fx + fw - 3; i += 4) {
            g.fill(i, fy + 2, i + 2, fy + 3, STITCH);
            g.fill(i, fy + fh - 3, i + 2, fy + fh - 2, STITCH);
        }
        for (int bx : new int[] {fx + 76, fx + fw - 40}) {
            g.fill(bx, y + 5, bx + 8, fy, LEATHER_DARK);
            g.fill(bx - 1, fy - 3, bx + 9, fy + 1, BRONZE);
            g.fill(bx + 2, fy - 2, bx + 6, fy, 0xFF6E4617);
        }
        for (int i = 0; i < BackpackItem.SLOTS; i++) {
            FactoryGui.slot(g, x + BackpackMenu.GRID_X + (i % 9) * 18, y + BackpackMenu.GRID_Y + (i / 9) * 18);
        }
        FactoryGui.playerInventory(g, x, y, BackpackMenu.PLAYER_INV_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, FactoryGui.fit(font, title, 60), 8, 6, FactoryGui.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, FactoryGui.TEXT, false);
    }
}
