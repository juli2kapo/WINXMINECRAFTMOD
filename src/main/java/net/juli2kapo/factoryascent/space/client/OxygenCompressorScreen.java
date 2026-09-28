package net.juli2kapo.factoryascent.space.client;

import java.util.List;
import net.juli2kapo.factoryascent.space.OxygenCompressorBlockEntity;
import net.juli2kapo.factoryascent.space.OxygenCompressorMenu;
import net.juli2kapo.factoryascent.space.SpaceConfig;
import net.juli2kapo.factoryascent.space.SuitItems;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The Oxygen Compressor's screen: suit slot, energy gauge (left) and the suit's air (right). */
public class OxygenCompressorScreen extends AbstractContainerScreen<OxygenCompressorMenu> {
    private static final int BG = 0xFF1B2128, INSET = 0xFF0E1318, EDGE_LIGHT = 0xFF3A4652, EDGE_DARK = 0xFF080B0E;
    private static final int ACCENT = 0xFF40C8FF, TEXT = 0xFFE0ECF4, MUTED = 0xFF8898A8;
    private static final int ENERGY_X = 30, AIR_X = 136, GAUGE_Y = 18, GAUGE_W = 10, GAUGE_H = 52;

    public OxygenCompressorScreen(OxygenCompressorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, OxygenCompressorMenu.WIDTH, OxygenCompressorMenu.HEIGHT);
        this.inventoryLabelY = OxygenCompressorMenu.PLAYER_INV_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, EDGE_DARK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, BG);
        g.fill(x + 1, y + 1, x + imageWidth - 2, y + 2, EDGE_LIGHT);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + 4, ACCENT);
        slot(g, x + OxygenCompressorMenu.SLOT_X, y + OxygenCompressorMenu.SLOT_Y);
        gauge(g, x + ENERGY_X, y + GAUGE_Y, menu.energy() / (float) OxygenCompressorBlockEntity.CAPACITY, 0xFFF0C040);
        ItemStack suit = menu.suit();
        gauge(g, x + AIR_X, y + GAUGE_Y, suit.isEmpty() ? 0 : SuitItems.oxygen(suit) / (float) SpaceConfig.suitOxygen(), ACCENT);
        // a pipe from the tank to the slot
        g.fill(x + ENERGY_X + GAUGE_W + 2, y + OxygenCompressorMenu.SLOT_Y + 7, x + OxygenCompressorMenu.SLOT_X - 2,
                y + OxygenCompressorMenu.SLOT_Y + 9, EDGE_LIGHT);
        g.fill(x + OxygenCompressorMenu.SLOT_X + 18, y + OxygenCompressorMenu.SLOT_Y + 7, x + AIR_X - 2,
                y + OxygenCompressorMenu.SLOT_Y + 9, suit.isEmpty() ? EDGE_LIGHT : ACCENT);
        for (int i = 0; i < 27; i++) slot(g, x + 8 + (i % 9) * 18, y + OxygenCompressorMenu.PLAYER_INV_Y + (i / 9) * 18);
        for (int i = 0; i < 9; i++) slot(g, x + 8 + i * 18, y + OxygenCompressorMenu.PLAYER_INV_Y + 58);
    }

    private static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, EDGE_DARK);
        g.fill(x, y, x + 17, y + 17, EDGE_LIGHT);
        g.fill(x, y, x + 16, y + 16, INSET);
    }

    private static void gauge(GuiGraphicsExtractor g, int x, int y, float fraction, int color) {
        g.fill(x - 1, y - 1, x + GAUGE_W + 1, y + GAUGE_H + 1, EDGE_DARK);
        g.fill(x, y, x + GAUGE_W, y + GAUGE_H, INSET);
        int filled = Math.round(GAUGE_H * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) g.fill(x, y + GAUGE_H - filled, x + GAUGE_W, y + GAUGE_H, color);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, ACCENT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        Component hint = menu.suit().isEmpty() ? Component.translatable("gui.factoryascent.compressor.insert")
                : SuitItems.oxygen(menu.suit()) >= SpaceConfig.suitOxygen() ? Component.translatable("gui.factoryascent.compressor.full")
                : menu.energy() <= 0 ? Component.translatable("gui.factoryascent.compressor.no_power")
                : Component.translatable("gui.factoryascent.compressor.filling");
        g.centeredText(font, hint, imageWidth / 2, OxygenCompressorMenu.SLOT_Y + 22, TEXT);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (isHovering(ENERGY_X - 1, GAUGE_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.factoryascent.energy",
                    EnergyUtil.format(menu.energy()), EnergyUtil.format(OxygenCompressorBlockEntity.CAPACITY))), mouseX, mouseY);
        } else if (isHovering(AIR_X - 1, GAUGE_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(SuitItems.oxygenLine(SuitItems.oxygen(menu.suit()))), mouseX, mouseY);
        } else if (menu.suit().isEmpty() && isHovering(OxygenCompressorMenu.SLOT_X - 1, OxygenCompressorMenu.SLOT_Y - 1, 18, 18, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.factoryascent.compressor.slot_tip"), mouseX, mouseY);
        }
    }
}
