package net.juli2kapo.factoryascent.trains.client;

import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.trains.StationMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Train Station's settings: stop rule, waiting time, load/unload, and a status display. */
public class StationScreen extends AbstractContainerScreen<StationMenu> {
    private static final int W = 196, H = 160, ACCENT = 0xFFE8C22A;
    private Button stop, dwell, mode;

    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 8, y = topPos + 20;
        stop = addRenderableWidget(Button.builder(Component.empty(), b -> click(StationMenu.BUTTON_STOP)).bounds(x, y, W - 16, 18).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> click(StationMenu.BUTTON_DWELL_DOWN)).bounds(x, y + 22, 20, 18).build());
        dwell = addRenderableWidget(Button.builder(Component.empty(), b -> {}).bounds(x + 22, y + 22, W - 16 - 44, 18).build());
        dwell.active = false;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> click(StationMenu.BUTTON_DWELL_UP)).bounds(x + W - 16 - 20, y + 22, 20, 18).build());
        mode = addRenderableWidget(Button.builder(Component.empty(), b -> click(StationMenu.BUTTON_MODE)).bounds(x, y + 44, W - 16, 18).build());
        refresh();
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void refresh() {
        if (stop == null) return;
        int s = menu.get(0);
        stop.setMessage(Component.translatable("gui.factoryascent.station.stop", Component.translatable("gui.factoryascent.station.stop." + s)));
        int d = menu.get(1);
        dwell.setMessage(d <= 0 ? Component.translatable("gui.factoryascent.station.dwell_done")
                : Component.translatable("gui.factoryascent.station.dwell", d));
        mode.setMessage(Component.translatable("gui.factoryascent.station.mode." + menu.get(2)));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        FactoryGui.panel(g, leftPos, topPos, W, H, ACCENT);
        FactoryGui.display(g, leftPos + 7, topPos + 88, W - 14, 64);
        boolean holding = menu.get(3) == 1;
        FactoryGui.lamp(g, leftPos + W - 18, topPos + 6, holding ? FactoryGui.GOOD : 0xFF803030);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 7, FactoryGui.TEXT, false);
        int y = 92;
        Component status;
        if (menu.get(6) == 0) {
            status = Component.translatable("gui.factoryascent.station.no_track").withStyle(ChatFormatting.RED);
        } else if (menu.get(3) == 2) {
            status = Component.translatable("gui.factoryascent.station.departing").withStyle(ChatFormatting.AQUA);
        } else if (menu.get(3) == 0) {
            status = Component.translatable("gui.factoryascent.station.empty").withStyle(ChatFormatting.GRAY);
        } else if (menu.get(4) == 0) {
            status = Component.translatable("gui.factoryascent.station.arriving").withStyle(ChatFormatting.YELLOW);
        } else {
            status = Component.translatable("gui.factoryascent.station.docked", menu.get(4)).withStyle(ChatFormatting.GREEN);
        }
        g.text(font, status, 12, y, FactoryGui.DISPLAY_TEXT, false);
        if (menu.get(3) == 1 && menu.get(4) > 0) {
            Component when = menu.get(0) == 1 ? Component.translatable("gui.factoryascent.station.waiting_signal")
                    : menu.get(5) >= 0 ? Component.translatable("gui.factoryascent.station.leaves", menu.get(5))
                    : Component.translatable("gui.factoryascent.station.waiting_done");
            g.text(font, when, 12, y + 11, FactoryGui.DISPLAY_TEXT, false);
        }
        Component power = Component.translatable("gui.factoryascent.station.powered",
                Component.translatable(menu.get(7) != 0 ? "gui.factoryascent.train.on" : "gui.factoryascent.train.off"));
        g.text(font, power, 12, y + 22, FactoryGui.DISPLAY_MUTED, false);
        var help = font.split(Component.translatable("gui.factoryascent.station.help"), W - 24);
        for (int i = 0; i < Math.min(3, help.size()); i++) g.text(font, help.get(i), 12, y + 33 + i * 9, FactoryGui.DISPLAY_MUTED, false);
    }
}
