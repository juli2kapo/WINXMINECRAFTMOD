package net.juli2kapo.factoryascent.trains.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.trains.CargoWagon;
import net.juli2kapo.factoryascent.trains.DieselLocomotive;
import net.juli2kapo.factoryascent.trains.HopperWagon;
import net.juli2kapo.factoryascent.trains.Locomotive;
import net.juli2kapo.factoryascent.trains.RollingStock;
import net.juli2kapo.factoryascent.trains.SteamLocomotive;
import net.juli2kapo.factoryascent.trains.TankWagon;
import net.juli2kapo.factoryascent.trains.TrainMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The cab (locomotives: bunker or power slot, gauges, whistle/horn, lights, close throttle) and the
 * wagons' screens (the hold, or the tank gauge), with an instrument panel on the right.
 */
public class TrainScreen extends AbstractContainerScreen<TrainMenu> {
    private static final int ACCENT = 0xFFB0703A;

    public TrainScreen(TrainMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.imageWidth(), menu.imageHeight());
        this.inventoryLabelY = menu.playerInvY() - 11;
    }

    private RollingStock stock() {
        return menu.stock();
    }

    @Override
    protected void init() {
        super.init();
        if (!(stock() instanceof Locomotive)) return;
        int bx = leftPos + 176 + 6, bw = (TrainMenu.PANEL_W - 16) / 2, by = topPos + imageHeight - 42;
        Component horn = Component.translatable(stock() instanceof SteamLocomotive ? "gui.factoryascent.train.whistle" : "gui.factoryascent.train.horn");
        addRenderableWidget(Button.builder(horn, b -> click(TrainMenu.BUTTON_HORN)).bounds(bx, by, bw, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.train.toggle_lights"), b -> click(TrainMenu.BUTTON_LIGHTS))
                .bounds(bx + bw + 4, by, bw, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.train.stop"), b -> click(TrainMenu.BUTTON_STOP))
                .bounds(bx, by + 19, TrainMenu.PANEL_W - 12, 16).build());
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        FactoryGui.panel(g, x, y, 178, imageHeight, ACCENT);
        FactoryGui.panel(g, x + 174, y, TrainMenu.PANEL_W + 2, imageHeight, ACCENT);
        int n = menu.slotCount();
        if (n >= 9) {
            for (int i = 0; i < n; i++) FactoryGui.slot(g, x + TrainMenu.GRID_X + (i % 9) * 18, y + TrainMenu.GRID_Y + (i / 9) * 18);
        } else {
            for (int i = 0; i < n; i++) {
                int sx = x + menu.smallSlotX(i), sy = y + menu.smallSlotY();
                FactoryGui.slot(g, sx, sy);
                if (menu.getSlot(i).getItem().isEmpty()) {
                    ItemStack ghost = new ItemStack(stock() instanceof DieselLocomotive ? Items.REDSTONE : Items.COAL);
                    g.fakeItem(ghost, sx, sy);
                    g.fill(sx, sy, sx + 16, sy + 16, 0xA08B8B8B);
                }
            }
            if (stock() instanceof TankWagon tank) tankGauge(g, x + 8, y + TrainMenu.GRID_Y + 4, 160, 46, tank);
        }
        FactoryGui.display(g, x + 176 + 5, y + 16, TrainMenu.PANEL_W - 12, displayHeight());
    }

    private int displayHeight() {
        return imageHeight - 16 - (stock() instanceof Locomotive ? 50 : 10);
    }

    private void tankGauge(GuiGraphicsExtractor g, int x, int y, int w, int h, TankWagon tank) {
        FactoryGui.display(g, x, y, w, h);
        float f = tank.syncedAmount() / (float) TankWagon.CAPACITY;
        int fill = Math.round((h - 2) * Math.min(1, f));
        if (fill > 0) {
            var fluid = tank.syncedFluid();
            int color = net.juli2kapo.factoryascent.fluid.ModFluids.all().stream().filter(d -> d.source() == fluid).map(d -> d.color)
                    .findFirst().orElse(fluid == net.minecraft.world.level.material.Fluids.LAVA ? 0xFFE06010 : 0xFF3F76E4);
            g.fill(x + 1, y + h - 1 - fill, x + w - 1, y + h - 1, color | 0xFF000000);
            g.fill(x + 1, y + h - 1 - fill, x + w - 1, y + h - fill, 0x60FFFFFF);
        }
        for (int i = 1; i < 4; i++) g.fill(x + w - 8, y + h * i / 4, x + w - 1, y + h * i / 4 + 1, 0xFF8C95A3);
    }

    private List<Component> readout() {
        List<Component> lines = new ArrayList<>();
        RollingStock s = stock();
        if (s instanceof Locomotive loco) {
            lines.add(TrainText.status(loco.status()));
            lines.add(TrainText.speed(loco.syncedSpeed()));
            lines.add(TrainText.throttle(loco.throttle()));
            for (TrainText.Gauge gauge : TrainText.gauges(loco)) lines.add(gauge.text());
            if (loco instanceof DieselLocomotive && loco.gaugeC() > 0) {
                lines.add(Component.translatable("gui.factoryascent.train.generator").withStyle(ChatFormatting.GREEN));
            }
            lines.add(Component.translatable("gui.factoryascent.train.cars", loco.cars()));
            lines.add(TrainText.lights(loco.lightsOn()));
        } else {
            lines.add(TrainText.speed(s.syncedSpeed()));
            if (s instanceof TankWagon t) {
                lines.add(Component.translatable("message.factoryascent.train.tank", t.syncedAmount(), TankWagon.CAPACITY,
                        t.syncedAmount() <= 0 ? Component.translatable("gui.factoryascent.train.empty") : t.syncedFluid().getFluidType().getDescription()));
            } else if (s instanceof CargoWagon || s instanceof HopperWagon) {
                lines.add(Component.translatable("gui.factoryascent.train.fill", Math.round((s.fill() & 0xFFFF) / 10f)));
            }
        }
        return lines;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        RollingStock s = stock();
        String head = s instanceof Locomotive ? "gui.factoryascent.train.cab" : s instanceof TankWagon ? "gui.factoryascent.train.tank_title"
                : "gui.factoryascent.train.hold";
        g.text(font, Component.translatable(head), titleLabelX, titleLabelY, FactoryGui.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, FactoryGui.TEXT, false);
        g.text(font, FactoryGui.fit(font, s.getDisplayName(), TrainMenu.PANEL_W - 12), 176 + 6, 6, FactoryGui.TEXT, false);
        if (s instanceof Locomotive) {
            String label = s instanceof SteamLocomotive ? "gui.factoryascent.train.bunker" : "gui.factoryascent.train.power_slot";
            Component c = Component.translatable(label);
            g.text(font, c, 88 - font.width(c) / 2, menu.smallSlotY() - 11, FactoryGui.MUTED, false);
        }
        int y = 16 + 4, w = TrainMenu.PANEL_W - 16;
        for (Component line : readout()) {
            for (var l : font.split(line, w)) {
                if (y + 9 > 16 + displayHeight()) return;
                g.text(font, l, 176 + 8, y, FactoryGui.DISPLAY_TEXT, false);
                y += 10;
            }
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (stock() instanceof DieselLocomotive && menu.getCarried().isEmpty() && menu.getSlot(0).getItem().isEmpty()
                && isHovering(menu.smallSlotX(0) - 1, menu.smallSlotY() - 1, 18, 18, mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.factoryascent.train.power_tip").withStyle(ChatFormatting.GRAY), mouseX, mouseY);
        }
    }
}
