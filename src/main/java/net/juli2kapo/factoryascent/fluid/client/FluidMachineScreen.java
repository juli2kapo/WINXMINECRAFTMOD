package net.juli2kapo.factoryascent.fluid.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachine;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachineBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachineData;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachineMenu;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The screen of every fluid machine, in the machine screens' style (bevelled grey panel, age
 * accent stripe, energy bar on the left, status line under the machine area). Tanks are drawn
 * as gauges filled with the fluid's own texture; each machine adds its own read-outs.
 */
public class FluidMachineScreen extends AbstractContainerScreen<FluidMachineMenu> {
    private static final int[] AGE_COLORS = {0xFF9A9A9A, 0xFFD08A3A, 0xFFE0C040, 0xFF4CB050, 0xFF4A78D8, 0xFFB060E0, 0xFF2FD5CF};
    private static final int ENERGY_X = 8, ENERGY_Y = 17, ENERGY_W = 12, ENERGY_H = 54;

    public FluidMachineScreen(FluidMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FluidMachineMenu.WIDTH, FluidMachineMenu.HEIGHT);
        this.inventoryLabelY = FluidMachineMenu.PLAYER_INV_Y - 11;
    }

    private FluidMachine machine() {
        return menu.machine().machine();
    }

    private FluidMachineData data() {
        return menu.data();
    }

    private int accent() {
        return AGE_COLORS[machine().age().ordinal()];
    }

    private boolean hasEnergy() {
        return machine().power() != FluidMachine.Power.NONE;
    }

    private long ticks() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    // ---------------------------------------------------------------- background

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        FactoryGui.panel(g, x, y, imageWidth, imageHeight, accent());
        for (FluidMachine.SlotSpec s : machine().slots()) {
            if (s.output()) g.fill(x + s.x() - 3, y + s.y() - 3, x + s.x() + 19, y + s.y() + 19, accent());
            FactoryGui.slot(g, x + s.x(), y + s.y());
        }
        FactoryGui.playerInventory(g, x, y, FluidMachineMenu.PLAYER_INV_Y);
        if (hasEnergy()) energyBar(g, x + ENERGY_X, y + ENERGY_Y);
        FluidMachineData d = data();
        var specs = machine().tankSpecs();
        for (int i = 0; i < specs.size(); i++) {
            var t = specs.get(i);
            tank(g, x + t.x(), y + t.y(), t.w(), t.h(), d.tankFluid(i), d.tankAmount(i), d.tankCapacity(i));
        }
        if (machine().hasArrow()) arrow(g, x + machine().arrowX(), y + machine().arrowY(), d.progress() / 1000f);
        switch (machine()) {
            case BOILER -> {
                flame(g, x + 45, y + 36, d.progress() / 1000f);
                thermometer(g, x + 64, y + 17, d.extra(0) / 10f);
                downArrow(g, x + 155, y + 38);
                rightArrow(g, x + 100, y + 40);
            }
            case PUMP -> downArrow(g, x + 123, y + 38);
            case DIESEL_GENERATOR -> {
                downArrow(g, x + 47, y + 38);
                if (d.status() == FluidMachineBlockEntity.ST_RUNNING) flame(g, x + 112, y + 36, 0.6f + 0.4f * (float) Math.abs(Math.sin(ticks() * 0.4)));
            }
            case STEAM_TURBINE -> rotor(g, x + 112, y + 17, d.extra(0) / 1000f);
            case OIL_DERRICK -> derrick(g, x + 26, y + 17, d);
            case REFINERY -> column(g, x + 76, y + 17, d.status() == FluidMachineBlockEntity.ST_RUNNING);
            case FUELLING_PORT -> {
                rightArrow(g, x + 66, y + 40);
                rightArrow(g, x + 112, y + 40);
            }
            default -> { }
        }
    }

    private void energyBar(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + ENERGY_W, y + ENERGY_H, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + ENERGY_W, y + ENERGY_H, FactoryGui.LIGHT);
        g.fill(x + 1, y + 1, x + ENERGY_W - 1, y + ENERGY_H - 1, 0xFF2A1010);
        int cap = data().capacity();
        if (cap <= 0) return;
        int inner = ENERGY_H - 2;
        int filled = (int) Math.round((double) Math.max(0, data().energy()) / cap * inner);
        for (int i = 0; i < filled; i++) {
            float t = (float) i / inner;
            int gr = Math.min(255, (int) (0x30 + 0xB0 * t));
            int yy = y + ENERGY_H - 2 - i;
            g.fill(x + 1, yy, x + ENERGY_W - 1, yy + 1, 0xFF000000 | (0xD0 << 16) | (gr << 8) | 0x20);
        }
    }

    /** A tank gauge: sunken well, the fluid's texture up to its level, a bright surface line and tick marks. */
    static void tank(GuiGraphicsExtractor g, int x, int y, int w, int h, Fluid fluid, int amount, int capacity) {
        g.fill(x, y, x + w, y + h, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, FactoryGui.LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF22262C);
        FluidRender.gauge(g, x + 1, y + 1, w - 2, h - 2, fluid, amount, capacity);
        if (fluid != Fluids.EMPTY && amount > 0 && capacity > 0) {
            int filled = Math.max(1, Math.round((h - 2) * Math.min(1f, (float) amount / capacity)));
            g.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - filled, 0x60FFFFFF);
        }
        for (int i = 1; i < 4; i++) g.fill(x + w - 5, y + i * h / 4, x + w - 1, y + i * h / 4 + 1, 0x90FFFFFF);
    }

    static void flame(GuiGraphicsExtractor g, int x, int y, float fraction) {
        g.fill(x, y, x + 14, y + 14, 0xFF6B6B6B);
        int h = Math.round(14 * Math.max(0, Math.min(1, fraction)));
        if (h > 0) {
            g.fill(x + 3, y + 14 - h, x + 11, y + 14, 0xFFFF9A1F);
            g.fill(x + 5, y + 14 - Math.max(1, h * 2 / 3), x + 9, y + 14, 0xFFFFE066);
        }
    }

    static void arrow(GuiGraphicsExtractor g, int x, int y, float fraction) {
        drawArrow(g, x, y, 0xFF8B8B8B);
        if (fraction > 0) {
            g.enableScissor(x, y, x + Math.round(24 * fraction), y + 16);
            drawArrow(g, x, y, 0xFFFFFFFF);
            g.disableScissor();
        }
    }

    private static void drawArrow(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y + 6, x + 16, y + 10, color);
        for (int i = 0; i < 8; i++) g.fill(x + 16 + i, y + 1 + i, x + 17 + i, y + 15 - i, color);
    }

    static void downArrow(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 3, y, x + 5, y + 6, 0xFF8B8B8B);
        for (int i = 0; i < 4; i++) g.fill(x + i, y + 6 + i, x + 8 - i, y + 7 + i, 0xFF8B8B8B);
    }

    static void rightArrow(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y + 3, x + 8, y + 5, 0xFF8B8B8B);
        for (int i = 0; i < 4; i++) g.fill(x + 8 + i, y + i, x + 9 + i, y + 8 - i, 0xFF8B8B8B);
    }

    private static void thermometer(GuiGraphicsExtractor g, int x, int y, float temp) {
        int h = 54, w = 10;
        g.fill(x, y, x + w, y + h, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, FactoryGui.LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF22262C);
        float f = (temp - 20f) / 180f;
        int filled = Math.round((h - 2) * Math.max(0, Math.min(1, f)));
        int color = temp >= 100 ? 0xFFE04A2A : 0xFFE09A3A;
        if (filled > 0) g.fill(x + 2, y + h - 1 - filled, x + w - 2, y + h - 1, color);
        int boil = y + h - 1 - Math.round((h - 2) * 80 / 180f);
        g.fill(x - 2, boil, x, boil + 1, 0xFF3F76E4);
        int full = y + h - 1 - Math.round((h - 2) * 160 / 180f);
        g.fill(x - 2, full, x, full + 1, 0xFF40A040);
    }

    /** Steam Turbine: blades turning as fast as the rotor, and its speed bar. */
    private void rotor(GuiGraphicsExtractor g, int x, int y, float speed) {
        FactoryGui.display(g, x, y, 54, 54);
        int cx = x + 27, cy = y + 24;
        double a = Math.toRadians(menu.machine().animAngle);
        for (int blade = 0; blade < 8; blade++) {
            double t = a + blade * Math.PI / 4;
            for (int r = 3; r <= 17; r++) {
                int px = cx + (int) Math.round(Math.cos(t + r * 0.03) * r), py = cy + (int) Math.round(Math.sin(t + r * 0.03) * r);
                g.fill(px, py, px + 2, py + 2, r < 8 ? 0xFFB0BAC6 : 0xFF8FA2B8);
            }
        }
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF5A6470);
        FactoryGui.bar(g, x + 4, y + 45, 46, 6, speed, speed > 0 ? 0xFF7FB2E5 : 0xFF555555);
    }

    /** Oil Derrick: the pocket under it (depth and size), or what's missing. */
    private void derrick(GuiGraphicsExtractor g, int x, int y, FluidMachineData d) {
        FactoryGui.display(g, x, y, 78, 54);
        int bottom = y + 52;
        // a little cross-section: ground, the drill string, the pocket
        g.fill(x + 1, y + 10, x + 77, bottom, 0xFF3A2E24);
        for (int i = 0; i < 6; i++) g.fill(x + 1, y + 16 + i * 6, x + 77, y + 17 + i * 6, 0xFF332820);
        boolean formed = d.extra(2) == 1;
        int pocket = d.extra(0);
        int stringEnd = pocket > 0 ? bottom - 14 : y + 26;
        g.fill(x + 38, y + 4, x + 40, stringEnd, formed ? 0xFFB0B4BA : 0xFF5A5E64);
        if (pocket > 0) {
            int w = Math.min(70, 16 + pocket / 6);
            g.fill(x + 39 - w / 2, bottom - 14, x + 39 + w / 2, bottom - 4, 0xFF15110D);
            g.fill(x + 39 - w / 2 + 2, bottom - 13, x + 39 + w / 2 - 2, bottom - 12, 0xFF4A4236);
        }
        if (d.status() == FluidMachineBlockEntity.ST_RUNNING) {
            int phase = (int) (ticks() % 20);
            g.fill(x + 37, y + 4 + phase, x + 41, y + 6 + phase, 0xFF1A1612);
        }
    }

    /** Refinery: the distillation column with its trays (warm while running). */
    private void column(GuiGraphicsExtractor g, int x, int y, boolean running) {
        g.fill(x, y, x + 18, y + 54, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + 17, y + 53, running ? 0xFF6E5A48 : 0xFF50545A);
        int[] colors = {0xFFE8562A, 0xFFD8A531, 0xFF7A6040, 0xFF2A2420};
        for (int i = 0; i < 4; i++) {
            int yy = y + 6 + i * 12;
            g.fill(x + 2, yy, x + 16, yy + 2, colors[i]);
        }
    }

    // ---------------------------------------------------------------- labels

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, FactoryGui.fit(font, title, imageWidth - 16), 8, 7, FactoryGui.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, FactoryGui.TEXT, false);
        Component status = statusLine();
        g.text(font, FactoryGui.fit(font, status, imageWidth - 12), Math.max(6, (imageWidth - font.width(status)) / 2), 76, FactoryGui.TEXT, false);
        FluidMachineData d = data();
        if (machine() == FluidMachine.OIL_DERRICK) {
            int lx = 30;
            g.text(font, FactoryGui.fit(font, d.extra(0) > 0 ? Component.translatable("gui.factoryascent.fluid.pocket", d.extra(0))
                    : Component.translatable("gui.factoryascent.fluid.no_pocket"), 72), lx, 21, FactoryGui.DISPLAY_TEXT, false);
            if (d.extra(1) > 0) g.text(font, Component.translatable("gui.factoryascent.fluid.depth", d.extra(1)), lx, 31, FactoryGui.DISPLAY_MUTED, false);
        }
    }

    private Component statusLine() {
        int st = Math.max(0, Math.min(FluidMachineBlockEntity.STATUS_KEYS.length - 1, data().status()));
        ChatFormatting color = switch (st) {
            case FluidMachineBlockEntity.ST_RUNNING, FluidMachineBlockEntity.ST_DIGESTING -> ChatFormatting.DARK_GREEN;
            case FluidMachineBlockEntity.ST_HEATING, FluidMachineBlockEntity.ST_FULL, FluidMachineBlockEntity.ST_SPINNING_UP -> ChatFormatting.GOLD;
            case FluidMachineBlockEntity.ST_IDLE -> ChatFormatting.DARK_GRAY;
            default -> ChatFormatting.DARK_RED;
        };
        Component s = Component.translatable("status.factoryascent.fluid." + FluidMachineBlockEntity.STATUS_KEYS[st]).withStyle(color);
        int rate = data().rate();
        if (rate > 0) {
            String key = switch (machine().power()) {
                case PRODUCER -> "gui.factoryascent.fluid.status_making";
                case CONSUMER -> "gui.factoryascent.fluid.status_using";
                case NONE -> machine() == FluidMachine.BOILER ? "gui.factoryascent.fluid.status_steam" : null;
            };
            if (key != null) s = Component.translatable(key, s, rate).withStyle(color);
        }
        return s;
    }

    // ---------------------------------------------------------------- tooltips

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        FluidMachineData d = data();
        List<Component> lines = new ArrayList<>();
        if (hasEnergy() && isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.energy", EnergyUtil.format(d.energy()), EnergyUtil.format(d.capacity())));
        }
        var specs = machine().tankSpecs();
        for (int i = 0; i < specs.size(); i++) {
            var t = specs.get(i);
            if (!isHovering(t.x(), t.y(), t.w(), t.h(), mouseX, mouseY)) continue;
            Fluid f = d.tankFluid(i);
            if (f == Fluids.EMPTY || d.tankAmount(i) <= 0) {
                lines.add(Component.translatable("gui.factoryascent.fluid.tank_empty", d.tankCapacity(i)).withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(f.getFluidType().getDescription(new FluidStack(f, 1)));
                lines.add(Component.translatable("gui.factoryascent.fluid.tank_amount", d.tankAmount(i), d.tankCapacity(i)).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("gui.factoryascent.fluid.tank." + machine().id() + "." + i).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (machine() == FluidMachine.BOILER && isHovering(62, 17, 14, 54, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.power.temperature", d.extra(0) / 10));
            lines.add(Component.translatable("gui.factoryascent.power.pressure", d.extra(1)).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.factoryascent.power.water_sources", d.extra(2)).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (machine() == FluidMachine.STEAM_TURBINE && isHovering(112, 17, 54, 54, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.fluid.rotor", d.extra(0) / 10));
        }
        if (machine() == FluidMachine.PUMP && isHovering(80, 17, 26, 54, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.fluid.pump_found", d.extra(0), d.extra(1)).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (machine() == FluidMachine.FUELLING_PORT && isHovering(80, 17, 26, 54, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.fluid.fuel_items", d.extra(0), d.extra(1)).withStyle(ChatFormatting.GOLD));
        }
        if (!lines.isEmpty()) g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }
}
