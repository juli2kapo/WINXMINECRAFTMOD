package net.juli2kapo.factoryascent.ships.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.juli2kapo.factoryascent.space.planet.Navigation;
import net.juli2kapo.factoryascent.space.planet.Planet;
import net.juli2kapo.factoryascent.ships.BronzeCog;
import net.juli2kapo.factoryascent.ships.MotorShip;
import net.juli2kapo.factoryascent.ships.OrbitTransfer;
import net.juli2kapo.factoryascent.ships.ShipConfig;
import net.juli2kapo.factoryascent.ships.ShipMath;
import net.juli2kapo.factoryascent.ships.ShipMenu;
import net.juli2kapo.factoryascent.ships.Shuttle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Helm (sea ships) / cockpit (shuttle) screen: the hold on the left, and on the right the
 * instrument panel: the power or fuel slot with its gauge, speed, heading, state, and per ship the
 * wind and sail pull (cog), battery (motor ship) or altitude, climb and destination (shuttle); horn
 * and lights buttons.
 */
public class ShipScreen extends AbstractContainerScreen<ShipMenu> {
    private static final int ACCENT_SEA = 0xFF3F8FC5, ACCENT_SPACE = 0xFFB060E0;
    private final int panelX;
    private final int navX = 176 + ShipMenu.PANEL_W;
    private final List<Button> destButtons = new ArrayList<>();
    private Button engage;

    public ShipScreen(ShipMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.imageWidth(), menu.imageHeight());
        this.inventoryLabelY = menu.playerInvY() - 11;
        this.panelX = 176;
    }

    private boolean shuttle() {
        return menu.ship() instanceof Shuttle;
    }

    @Override
    protected void init() {
        super.init();
        int bx = leftPos + panelX + 6, by = topPos + imageHeight - 24, bw = (ShipMenu.PANEL_W - 16) / 2;
        if (!shuttle()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.ship.horn"), b -> click(ShipMenu.BUTTON_HORN))
                    .bounds(bx, by, bw, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.ship.toggle_lights"), b -> click(ShipMenu.BUTTON_LIGHTS))
                .bounds(shuttle() ? bx : bx + bw + 4, by, shuttle() ? ShipMenu.PANEL_W - 12 : bw, 16).build());
        destButtons.clear();
        if (shuttle()) {
            int nx = leftPos + navX + 6, nw = ShipMenu.NAV_W - 14;
            for (Navigation.Destination d : Navigation.Destination.values()) {
                int id = ShipMenu.BUTTON_DEST + d.ordinal();
                destButtons.add(addRenderableWidget(Button.builder(d.displayName(), b -> click(id))
                        .bounds(nx, topPos + 32 + d.ordinal() * 20, nw, 18).build()));
            }
            engage = addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.nav.engage"), b -> click(ShipMenu.BUTTON_ENGAGE))
                    .bounds(nx, topPos + imageHeight - 24, nw, 16).build());
            updateNav();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (shuttle()) updateNav();
    }

    /** Destination labels (with cost and time), the selection marker and whether Engage can work. */
    private void updateNav() {
        if (engage == null) return;
        int here = menu.get(ShipMenu.D_NAV_HERE), selected = menu.get(ShipMenu.D_NAV_SELECTED);
        boolean ion = menu.get(ShipMenu.D_NAV_ION) != 0, cruising = menu.get(ShipMenu.D_NAV_CRUISE) >= 0;
        var costs = ShipConfig.navCosts();
        Navigation.Destination from = here >= 0 ? Navigation.Destination.byIndex(here) : Navigation.Destination.EARTH_ORBIT;
        for (Navigation.Destination d : Navigation.Destination.values()) {
            Button b = destButtons.get(d.ordinal());
            var label = Component.empty();
            if (d.ordinal() == selected) label.append(Component.literal("\u25B6 ").withStyle(ChatFormatting.GOLD));
            label.append(d.displayName());
            if (here >= 0 && d.ordinal() != here) {
                label.append(Component.literal("  " + Navigation.fuelCost(from, d, costs, ion)).withStyle(ChatFormatting.GRAY));
            } else if (d.ordinal() == here) {
                label.append(Component.translatable("gui.factoryascent.nav.here").withStyle(ChatFormatting.DARK_GRAY));
            }
            b.setMessage(label);
            b.active = !cruising;
        }
        engage.active = !cruising && here == Navigation.Destination.EARTH_ORBIT.ordinal() && selected != here;
    }

    /** One line under the destinations: what Engage would do, or why not. */
    private Component navStatus() {
        int here = menu.get(ShipMenu.D_NAV_HERE), selected = menu.get(ShipMenu.D_NAV_SELECTED);
        int cruise = menu.get(ShipMenu.D_NAV_CRUISE);
        var costs = ShipConfig.navCosts();
        boolean ion = menu.get(ShipMenu.D_NAV_ION) != 0;
        Navigation.Destination to = Navigation.Destination.byIndex(selected);
        if (cruise >= 0) {
            int target = menu.get(ShipMenu.D_NAV_TARGET);
            return Component.translatable("gui.factoryascent.nav.cruising", Navigation.Destination.byIndex(Math.max(0, target)).displayName(), cruise)
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        if (here < 0) return Component.translatable("gui.factoryascent.nav.not_in_space").withStyle(ChatFormatting.GRAY);
        Navigation.Destination from = Navigation.Destination.byIndex(here);
        if (from.planet != null) {
            Navigation.Destination leave = Navigation.leavingPlanet(from, to, menu.get(ShipMenu.D_FUEL) * Math.max(1, menu.get(ShipMenu.D_FUEL_SCALE)), costs, ion);
            if (leave == null) return Component.translatable("gui.factoryascent.nav.stranded").withStyle(ChatFormatting.RED);
            return Component.translatable("gui.factoryascent.nav.climb", Planet.ORBIT_LINE, leave.displayName()).withStyle(ChatFormatting.AQUA);
        }
        if (to == from) return Component.translatable("gui.factoryascent.nav.pick").withStyle(ChatFormatting.GRAY);
        int fuel = menu.get(ShipMenu.D_FUEL) * Math.max(1, menu.get(ShipMenu.D_FUEL_SCALE));
        int cost = Navigation.fuelCost(from, to, costs, ion);
        int secs = (Navigation.travelTicks(from, to, costs, ion) + 19) / 20;
        if (fuel < cost) return Component.translatable("gui.factoryascent.nav.need_fuel", cost).withStyle(ChatFormatting.RED);
        return Component.translatable("gui.factoryascent.nav.ready", cost, secs).withStyle(ChatFormatting.GREEN);
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        int accent = shuttle() ? ACCENT_SPACE : ACCENT_SEA;
        FactoryGui.panel(g, x, y, panelX + 2, imageHeight, accent);
        FactoryGui.panel(g, x + panelX - 2, y, ShipMenu.PANEL_W + 2, imageHeight, accent);
        if (shuttle()) {
            FactoryGui.panel(g, x + navX - 2, y, ShipMenu.NAV_W + 2, imageHeight, accent);
            FactoryGui.display(g, x + navX + 5, y + 16, ShipMenu.NAV_W - 12, 13);
            FactoryGui.display(g, x + navX + 5, y + 113, ShipMenu.NAV_W - 12, 13);
        }
        int cargo = menu.ship().cargoSize();
        for (int i = 0; i < cargo; i++) FactoryGui.slot(g, x + ShipMenu.GRID_X + (i % 9) * 18, y + ShipMenu.GRID_Y + (i / 9) * 18);
        FactoryGui.playerInventory(g, x, y, menu.playerInvY());
        int px = x + panelX + 6, top = y + ShipMenu.GRID_Y;
        if (menu.hasFuelSlot()) {
            FactoryGui.slot(g, x + menu.fuelSlotX(), y + menu.fuelSlotY());
            if (menu.getSlot(cargo).getItem().isEmpty()) {
                ItemStack ghost = shuttle() ? new ItemStack(OrbitalContent.ROCKET_FUEL.get()) : new ItemStack(Items.COAL);
                g.fakeItem(ghost, x + menu.fuelSlotX(), y + menu.fuelSlotY());
                g.fill(x + menu.fuelSlotX(), y + menu.fuelSlotY(), x + menu.fuelSlotX() + 16, y + menu.fuelSlotY() + 16, 0xA08B8B8B);
            }
            int scale = Math.max(1, menu.get(ShipMenu.D_FUEL_SCALE));
            float fraction = menu.get(ShipMenu.D_FUEL_MAX) <= 0 ? 0 : menu.get(ShipMenu.D_FUEL) / (float) menu.get(ShipMenu.D_FUEL_MAX);
            if (shuttle()) {
                FactoryGui.bar(g, px + 24, top + 10, ShipMenu.PANEL_W - 36, 7, fraction, 0xFFE08030);
            } else {
                FactoryGui.energyBar(g, px + 24, top + 10, ShipMenu.PANEL_W - 36, 7, fraction);
            }
        }
        FactoryGui.display(g, px - 1, top + 22, ShipMenu.PANEL_W - 10, displayHeight());
    }

    private int displayHeight() {
        return imageHeight - ShipMenu.GRID_Y - 22 - 30;
    }

    private List<Component> readout() {
        List<Component> lines = new ArrayList<>();
        boolean sh = shuttle();
        lines.add(ShipText.state(sh, menu.get(ShipMenu.D_STATE)));
        lines.add(ShipText.speed(menu.get(ShipMenu.D_SPEED) / 10.0));
        lines.add(ShipText.heading(menu.get(ShipMenu.D_HEADING)));
        if (menu.ship() instanceof BronzeCog) {
            lines.add(ShipText.wind(menu.get(ShipMenu.D_WIND), menu.get(ShipMenu.D_WIND_STRENGTH) / 100f));
            lines.add(ShipText.sails(menu.get(ShipMenu.D_AUX) / 100f));
        } else if (menu.ship() instanceof MotorShip) {
            int scale = Math.max(1, menu.get(ShipMenu.D_FUEL_SCALE));
            lines.add(ShipText.battery(menu.get(ShipMenu.D_FUEL) * scale, menu.get(ShipMenu.D_FUEL_MAX) * scale));
        } else {
            lines.add(ShipText.altitude(menu.get(ShipMenu.D_ALT)));
            lines.add(ShipText.climb(menu.get(ShipMenu.D_AUX) / 10.0));
            int scale = Math.max(1, menu.get(ShipMenu.D_FUEL_SCALE));
            lines.add(ShipText.tank(menu.get(ShipMenu.D_FUEL) * scale, menu.get(ShipMenu.D_FUEL_MAX) * scale));
            var realm = OrbitTransfer.realm(menu.ship().level());
            lines.add(ShipText.destination(realm, menu.ship().level().getMaxY() + 1));
        }
        lines.add(ShipText.lights(menu.get(ShipMenu.D_LIGHTS) != 0));
        return lines;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.translatable("gui.factoryascent.ship.hold"), titleLabelX, titleLabelY, FactoryGui.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, FactoryGui.TEXT, false);
        int px = panelX + 6;
        Component head = Component.translatable(shuttle() ? "gui.factoryascent.ship.cockpit" : "gui.factoryascent.ship.helm");
        g.text(font, head, px, 6, FactoryGui.TEXT, false);
        if (menu.hasFuelSlot()) {
            g.text(font, Component.translatable(shuttle() ? "gui.factoryascent.ship.fuel_slot" : "gui.factoryascent.ship.power"),
                    px + 24, ShipMenu.GRID_Y + 1, FactoryGui.MUTED, false);
        }
        if (shuttle()) {
            int nx = navX + 6;
            g.text(font, Component.translatable("gui.factoryascent.nav.title"), nx, 6, FactoryGui.TEXT, false);
            int here = menu.get(ShipMenu.D_NAV_HERE);
            Component at = here >= 0 ? Navigation.Destination.byIndex(here).displayName() : Component.translatable("gui.factoryascent.nav.atmosphere");
            g.text(font, font.substrByWidth(Component.translatable("gui.factoryascent.nav.at", at), ShipMenu.NAV_W - 16).getString(),
                    nx + 2, 19, FactoryGui.DISPLAY_TEXT, false);
            var status = font.split(navStatus(), ShipMenu.NAV_W - 16);
            if (!status.isEmpty()) g.text(font, status.get(0), nx + 2, 116, FactoryGui.DISPLAY_TEXT, false);
        }
        int y = ShipMenu.GRID_Y + 25;
        int w = ShipMenu.PANEL_W - 16;
        for (Component line : readout()) {
            if (y + 9 > ShipMenu.GRID_Y + 22 + displayHeight()) break;
            var lines = font.split(line, w);
            for (var l : lines) {
                g.text(font, l, px + 2, y, FactoryGui.DISPLAY_TEXT, false);
                y += 9;
            }
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (menu.hasFuelSlot() && menu.getCarried().isEmpty() && menu.getSlot(menu.ship().cargoSize()).getItem().isEmpty()
                && isHovering(menu.fuelSlotX() - 1, menu.fuelSlotY() - 1, 18, 18, mouseX, mouseY)) {
            Component tip = shuttle()
                    ? Component.translatable("gui.factoryascent.ship.fuel_tip", ShipConfig.fuelPerItem())
                    : Component.translatable("gui.factoryascent.ship.power_tip");
            g.setTooltipForNextFrame(font, tip.copy().withStyle(ChatFormatting.GRAY), mouseX, mouseY);
        }
    }
}
