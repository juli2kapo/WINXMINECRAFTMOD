package net.juli2kapo.factoryascent.nuclear.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.nuclear.ReactorControllerBlockEntity;
import net.juli2kapo.factoryascent.nuclear.ReactorData;
import net.juli2kapo.factoryascent.nuclear.ReactorMenu;
import net.juli2kapo.factoryascent.nuclear.ReactorStructure;
import net.juli2kapo.factoryascent.power.PowerConfig;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The fission reactor's control room: the core seen from above (fuel channels glowing with the
 * core temperature, control rods grey), the temperature column with its alarm and meltdown marks,
 * the coolant and energy gauges, the numbers, the control-rod slider with fine buttons, and SCRAM.
 */
public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {
    private static final int ACCENT = 0xFF4A78D8; // industrial age
    private static final int MAP_X = 8, MAP_Y = 18, CELL = 14, MAP_SIZE = 76;
    private static final int TEMP_X = 90, COOL_X = 106, ENERGY_X = 122, GAUGE_Y = 18, GAUGE_W = 12, GAUGE_H = 76;
    private static final int STATS_X = 140, STATS_W = 100;
    private static final int SLIDER_X = 140, SLIDER_Y = 84, SLIDER_W = 100, SLIDER_H = 8;
    private static final int BTN_Y = 96, BTN_H = 12;
    private static final int[][] BUTTONS = {{140, 16}, {157, 13}, {171, 13}, {185, 16}}; // -10 -1 +1 +10
    private static final String[] BUTTON_TEXT = {"-10", "-1", "+1", "+10"};
    private static final int SCRAM_X = 204, SCRAM_W = 36;

    public ReactorScreen(ReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ReactorMenu.WIDTH, ReactorMenu.HEIGHT);
        this.inventoryLabelX = ReactorMenu.INV_X;
        this.inventoryLabelY = ReactorMenu.INV_Y - 11;
    }

    private ReactorData d() {
        return menu.data();
    }

    private float temp() {
        return d().i(ReactorData.TEMP) / 10f;
    }

    private static int meltdown() {
        return PowerConfig.get(PowerConfig.MELTDOWN_TEMPERATURE);
    }

    private static int alarm() {
        return PowerConfig.get(PowerConfig.ALARM_TEMPERATURE);
    }

    private long ticks() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    private boolean alarming() {
        return temp() >= alarm() && !d().flag(ReactorData.F_MELTED);
    }

    // ---------------------------------------------------------------- background

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        boolean flash = alarming() && ticks() / 8 % 2 == 0;
        FactoryGui.panel(g, x, y, imageWidth, imageHeight, flash ? FactoryGui.BAD : ACCENT);
        coreMap(g, x + MAP_X, y + MAP_Y);
        thermometer(g, x + TEMP_X, y + GAUGE_Y);
        gauge(g, x + COOL_X, y + GAUGE_Y, d().i(ReactorData.COOLANT) / (float) Math.max(1, d().i(ReactorData.COOLANT_CAP)), 0xFF3F76E4, 0xFF9FD0FF);
        energy(g, x + ENERGY_X, y + GAUGE_Y);
        FactoryGui.display(g, x + STATS_X, y + 18, STATS_W, 52);
        slider(g, x + SLIDER_X, y + SLIDER_Y, mouseX, mouseY);
        for (int i = 0; i < 4; i++) {
            int bx = x + BUTTONS[i][0];
            FactoryGui.button(g, bx, y + BTN_Y, BUTTONS[i][1], BTN_H, FactoryGui.inside(mouseX, mouseY, bx, y + BTN_Y, BUTTONS[i][1], BTN_H), false, ACCENT);
        }
        boolean scram = d().flag(ReactorData.F_SCRAM_BUTTON);
        boolean hover = FactoryGui.inside(mouseX, mouseY, x + SCRAM_X, y + BTN_Y - 2, SCRAM_W, BTN_H + 4);
        g.fill(x + SCRAM_X, y + BTN_Y - 2, x + SCRAM_X + SCRAM_W, y + BTN_Y + BTN_H + 2, FactoryGui.OUTLINE);
        g.fill(x + SCRAM_X + 1, y + BTN_Y - 1, x + SCRAM_X + SCRAM_W - 1, y + BTN_Y + BTN_H + 1,
                scram ? 0xFF7A1010 : hover ? 0xFFE04040 : 0xFFC02020);
        g.fill(x + SCRAM_X + 1, y + BTN_Y - 1, x + SCRAM_X + SCRAM_W - 1, y + BTN_Y, scram ? 0xFF501010 : 0xFFFF8080);
        for (int i = 0; i < ReactorControllerBlockEntity.SLOTS; i++) {
            int sx = x + ReactorMenu.slotX(i), sy = y + ReactorMenu.SLOT_Y;
            if (ReactorControllerBlockEntity.isOutputSlot(i)) g.fill(sx - 3, sy - 3, sx + 19, sy + 19, ACCENT);
            FactoryGui.slot(g, sx, sy);
        }
        // coolant in -> out
        int ax = x + ReactorMenu.slotX(ReactorControllerBlockEntity.COOLANT_IN) + 19, ay = y + ReactorMenu.SLOT_Y + 7;
        g.fill(ax, ay, ax + 10, ay + 2, 0xFF8B8B8B);
        for (int i = 0; i < 3; i++) g.fill(ax + 10 + i, ay - 2 + i, ax + 11 + i, ay + 4 - i, 0xFF8B8B8B);
        FactoryGui.playerInventory(g, x + ReactorMenu.INV_X - 8, y, ReactorMenu.INV_Y);
    }

    /** The core from above: one cell per interior column. */
    private void coreMap(GuiGraphicsExtractor g, int x, int y) {
        FactoryGui.display(g, x, y, MAP_SIZE, MAP_SIZE);
        int dims = d().i(ReactorData.DIMS);
        int w = dims / 100 - 2, depth = dims % 10 - 2, h = Math.max(1, dims / 10 % 10 - 2);
        if (!d().flag(ReactorData.F_VALID) || w <= 0 || depth <= 0) {
            // an X over an empty map
            for (int i = 4; i < MAP_SIZE - 4; i++) {
                g.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFF803030);
                g.fill(x + MAP_SIZE - 1 - i, y + i, x + MAP_SIZE - i, y + i + 1, 0xFF803030);
            }
            return;
        }
        int ox = x + (MAP_SIZE - w * CELL) / 2, oy = y + (MAP_SIZE - depth * CELL) / 2;
        float heat = Math.max(0f, Math.min(1f, (temp() - 20f) / (meltdown() - 20f)));
        boolean melted = d().flag(ReactorData.F_MELTED);
        float insertion = d().i(ReactorData.INSERTION) / 100f;
        if (d().flag(ReactorData.F_SCRAM_BUTTON) || d().flag(ReactorData.F_REDSTONE) || d().flag(ReactorData.F_WASTE)) insertion = 1f;
        for (int a = 0; a < w; a++) {
            for (int c = 0; c < depth; c++) {
                int cell = d().cell(a, c);
                int cx = ox + a * CELL, cy = oy + c * CELL;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF262B33);
                if (cell <= 0) continue;
                int channels = cell / 8, rods = cell % 8;
                if (channels > 0) {
                    int glow = melted ? 0xFFFF7A10 : heatColor(heat, channels / (float) h);
                    g.fill(cx + 1, cy + 1, cx + CELL - 2, cy + CELL - 2, 0xFF1A3A1A);
                    g.fill(cx + 3, cy + 3, cx + CELL - 4, cy + CELL - 4, glow);
                    if (channels > 1) g.fill(cx + 1, cy + CELL - 3, cx + 1 + Math.min(CELL - 3, channels * 2), cy + CELL - 2, 0xFFB0FFB0);
                }
                if (rods > 0) {
                    g.fill(cx + 1, cy + 1, cx + CELL - 2, cy + CELL - 2, channels > 0 ? 0x80303030 : 0xFF3A3F48);
                    int inset = Math.round(5 * (1 - insertion));
                    g.fill(cx + 4 + inset / 2, cy + 4 + inset / 2, cx + CELL - 5 - inset / 2, cy + CELL - 5 - inset / 2, 0xFFB8BEC8);
                }
            }
        }
    }

    private static int heatColor(float heat, float fill) {
        int r = (int) (60 + 195 * heat), gr = (int) (200 - 150 * heat), b = (int) (60 - 40 * heat);
        float k = 0.55f + 0.45f * Math.min(1f, fill);
        return 0xFF000000 | ((int) (r * k) << 16) | ((int) (gr * k) << 8) | Math.max(0, (int) (b * k));
    }

    private void thermometer(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + GAUGE_W, y + GAUGE_H, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + GAUGE_W, y + GAUGE_H, FactoryGui.LIGHT);
        g.fill(x + 1, y + 1, x + GAUGE_W - 1, y + GAUGE_H - 1, 0xFF22262C);
        float max = meltdown();
        int inner = GAUGE_H - 2;
        int filled = Math.round(inner * Math.max(0, Math.min(1, (temp() - 20f) / (max - 20f))));
        for (int i = 0; i < filled; i++) {
            float t = (float) i / inner;
            int yy = y + GAUGE_H - 2 - i;
            int c = t < 0.5f ? 0xFFE0C040 : t < alarm() / max ? 0xFFE08030 : 0xFFE03020;
            g.fill(x + 2, yy, x + GAUGE_W - 2, yy + 1, c);
        }
        int alarmY = y + GAUGE_H - 2 - Math.round(inner * (alarm() - 20f) / (max - 20f));
        g.fill(x - 2, alarmY, x + GAUGE_W + 2, alarmY + 1, 0xFFE0C040);
        g.fill(x - 2, y + 1, x + GAUGE_W + 2, y + 2, 0xFFE03020);
        int boil = y + GAUGE_H - 2 - Math.round(inner * 80f / (max - 20f));
        g.fill(x - 2, boil, x, boil + 1, 0xFF3F76E4);
    }

    private static void gauge(GuiGraphicsExtractor g, int x, int y, float fraction, int color, int light) {
        g.fill(x, y, x + GAUGE_W, y + GAUGE_H, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + GAUGE_W, y + GAUGE_H, FactoryGui.LIGHT);
        g.fill(x + 1, y + 1, x + GAUGE_W - 1, y + GAUGE_H - 1, 0xFF22262C);
        int filled = Math.round((GAUGE_H - 2) * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x + 1, y + GAUGE_H - 1 - filled, x + GAUGE_W - 1, y + GAUGE_H - 1, color);
            g.fill(x + 1, y + GAUGE_H - 1 - filled, x + GAUGE_W - 1, y + GAUGE_H - filled, light);
        }
    }

    private void energy(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + GAUGE_W, y + GAUGE_H, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + GAUGE_W, y + GAUGE_H, FactoryGui.LIGHT);
        g.fill(x + 1, y + 1, x + GAUGE_W - 1, y + GAUGE_H - 1, 0xFF2A1010);
        int cap = d().i(ReactorData.CAPACITY);
        if (cap <= 0) return;
        int inner = GAUGE_H - 2;
        int filled = (int) Math.round((double) Math.max(0, d().i(ReactorData.ENERGY)) / cap * inner);
        for (int i = 0; i < filled; i++) {
            float t = (float) i / inner;
            int gr = Math.min(255, (int) (0x30 + 0xB0 * t));
            g.fill(x + 1, y + GAUGE_H - 2 - i, x + GAUGE_W - 1, y + GAUGE_H - 1 - i, 0xFF000000 | (0xD0 << 16) | (gr << 8) | 0x20);
        }
    }

    private void slider(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        g.fill(x, y, x + SLIDER_W, y + SLIDER_H, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + SLIDER_W - 1, y + SLIDER_H - 1, 0xFF2A2A2A);
        int ins = d().i(ReactorData.INSERTION);
        int fill = Math.round((SLIDER_W - 2) * ins / 100f);
        g.fill(x + 1, y + 1, x + 1 + fill, y + SLIDER_H - 1, 0xFF8E96A2);
        int kx = x + fill;
        g.fill(kx - 1, y - 2, kx + 2, y + SLIDER_H + 2, FactoryGui.OUTLINE);
        g.fill(kx, y - 1, kx + 1, y + SLIDER_H + 1, FactoryGui.LIGHT);
    }

    // ---------------------------------------------------------------- labels

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, FactoryGui.TEXT, false);
        Component status = status();
        g.text(font, status, imageWidth - 8 - font.width(status), 6, FactoryGui.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, FactoryGui.TEXT, false);
        ReactorData d = d();
        int sx = STATS_X + 4, right = STATS_X + STATS_W - 4, sy = 21;
        boolean valid = d.flag(ReactorData.F_VALID);
        if (valid) {
            FactoryGui.row(g, font, Component.translatable("gui.factoryascent.reactor.output"),
                    Component.literal(EnergyUtil.format(d.i(ReactorData.RATE)) + " FE/t"), sx, right, sy, FactoryGui.DISPLAY_MUTED, FactoryGui.GOOD);
            FactoryGui.row(g, font, Component.translatable("gui.factoryascent.reactor.temp"),
                    Component.literal(Math.round(temp()) + " °C"), sx, right, sy + 10, FactoryGui.DISPLAY_MUTED,
                    temp() >= alarm() ? FactoryGui.BAD : temp() >= 100 ? FactoryGui.WARN : FactoryGui.DISPLAY_TEXT);
            FactoryGui.row(g, font, Component.translatable("gui.factoryascent.reactor.heat"),
                    Component.literal(String.format("%.1f H/t", d.i(ReactorData.HEAT) / 10f)), sx, right, sy + 20,
                    FactoryGui.DISPLAY_MUTED, FactoryGui.DISPLAY_TEXT);
            FactoryGui.row(g, font, Component.translatable("gui.factoryascent.reactor.rods"),
                    Component.literal(d.i(ReactorData.RODS) + "/" + d.i(ReactorData.CHANNELS)), sx, right, sy + 30,
                    FactoryGui.DISPLAY_MUTED, FactoryGui.DISPLAY_TEXT);
            FactoryGui.row(g, font, Component.translatable("gui.factoryascent.reactor.bonus"),
                    Component.literal(String.format("x%.2f · %d%%", d.i(ReactorData.BONUS) / 100f, d.i(ReactorData.AUTHORITY))), sx, right, sy + 40,
                    FactoryGui.DISPLAY_MUTED, d.i(ReactorData.AUTHORITY) < 100 ? FactoryGui.WARN : FactoryGui.DISPLAY_TEXT);
        } else {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(errorText(), STATS_W - 8);
            for (int i = 0; i < Math.min(5, lines.size()); i++) g.text(font, lines.get(i), sx, sy + i * 10, FactoryGui.BAD, false);
        }
        Component rods = Component.translatable("gui.factoryascent.reactor.insertion", d.i(ReactorData.INSERTION));
        g.text(font, FactoryGui.fit(font, rods, STATS_W), SLIDER_X, 73, FactoryGui.TEXT, false);
        for (int i = 0; i < 4; i++) {
            String t = BUTTON_TEXT[i];
            g.text(font, t, BUTTONS[i][0] + (BUTTONS[i][1] - font.width(t)) / 2 + 1, BTN_Y + 2, 0xFFFFFFFF, false);
        }
        Component scram = Component.translatable("gui.factoryascent.reactor.scram");
        g.text(font, FactoryGui.fit(font, scram, SCRAM_W - 2), SCRAM_X + (SCRAM_W - Math.min(SCRAM_W - 2, font.width(scram))) / 2, BTN_Y + 2,
                0xFFFFFFFF, false);
        g.text(font, Component.translatable("gui.factoryascent.reactor.fuel"), 8, ReactorMenu.SLOT_Y - 10, FactoryGui.TEXT, false);
        g.text(font, Component.translatable("gui.factoryascent.reactor.spent"), 124, ReactorMenu.SLOT_Y - 10, FactoryGui.TEXT, false);
        g.text(font, Component.translatable("gui.factoryascent.reactor.coolant"), 186, ReactorMenu.SLOT_Y - 10, FactoryGui.TEXT, false);
        String dims = d.flag(ReactorData.F_VALID) ? (d.i(ReactorData.DIMS) / 100) + "×" + (d.i(ReactorData.DIMS) / 10 % 10) + "×" + (d.i(ReactorData.DIMS) % 10) : "";
        if (!dims.isEmpty()) g.text(font, dims, MAP_X + 2, MAP_Y + MAP_SIZE + 2, FactoryGui.MUTED, false);
    }

    private Component status() {
        ReactorData d = d();
        if (d.flag(ReactorData.F_MELTED)) return Component.translatable("gui.factoryascent.reactor.melted").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
        if (!d.flag(ReactorData.F_VALID)) return Component.translatable("gui.factoryascent.reactor.incomplete").withStyle(ChatFormatting.DARK_RED);
        if (alarming()) return Component.translatable("gui.factoryascent.reactor.alarm").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        if (d.flag(ReactorData.F_WASTE)) return Component.translatable("gui.factoryascent.reactor.scram_waste").withStyle(ChatFormatting.GOLD);
        if (d.flag(ReactorData.F_REDSTONE)) return Component.translatable("gui.factoryascent.reactor.scram_redstone").withStyle(ChatFormatting.GOLD);
        if (d.flag(ReactorData.F_SCRAM_BUTTON)) return Component.translatable("gui.factoryascent.reactor.scrammed").withStyle(ChatFormatting.GOLD);
        if (d.i(ReactorData.RODS) == 0) return Component.translatable("gui.factoryascent.reactor.no_fuel").withStyle(ChatFormatting.DARK_GRAY);
        if (d.i(ReactorData.COOLANT) <= 0) return Component.translatable("gui.factoryascent.reactor.no_coolant").withStyle(ChatFormatting.RED);
        if (d.i(ReactorData.RATE) > 0) return Component.translatable("gui.factoryascent.reactor.online").withStyle(ChatFormatting.DARK_GREEN);
        return Component.translatable("gui.factoryascent.reactor.heating").withStyle(ChatFormatting.GOLD);
    }

    private Component errorText() {
        ReactorData d = d();
        var error = ReactorStructure.Error.values()[Math.max(0, Math.min(ReactorStructure.Error.values().length - 1, d.i(ReactorData.ERROR)))];
        String key = "gui.factoryascent.reactor.error." + error.name().toLowerCase(java.util.Locale.ROOT);
        return Component.translatable(key, d.i(ReactorData.BAD_X), d.i(ReactorData.BAD_Y), d.i(ReactorData.BAD_Z));
    }

    // ---------------------------------------------------------------- tooltips & input

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        ReactorData d = d();
        List<Component> lines = new ArrayList<>();
        if (isHovering(TEMP_X, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.reactor.temp_tip", Math.round(temp())));
            lines.add(Component.translatable("gui.factoryascent.reactor.temp_marks", alarm(), meltdown()).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(COOL_X, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.reactor.coolant_tip", d.i(ReactorData.COOLANT), d.i(ReactorData.COOLANT_CAP)));
            lines.add(Component.translatable("gui.factoryascent.reactor.coolant_use", String.format("%.1f", d.i(ReactorData.REMOVED) / 40f))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.factoryascent.reactor.coolant_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else if (isHovering(ENERGY_X, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.energy", EnergyUtil.format(d.i(ReactorData.ENERGY)), EnergyUtil.format(d.i(ReactorData.CAPACITY))));
            lines.add(Component.translatable("gui.factoryascent.generating", d.i(ReactorData.RATE)).withStyle(ChatFormatting.GREEN));
        } else if (isHovering(MAP_X, MAP_Y, MAP_SIZE, MAP_SIZE, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.reactor.map_tip"));
            lines.add(Component.translatable("gui.factoryascent.reactor.map_counts", d.i(ReactorData.CHANNELS), d.i(ReactorData.CONTROL_RODS))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.factoryascent.reactor.burn", d.i(ReactorData.BURN) / 10).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(STATS_X, 18, STATS_W, 52, mouseX, mouseY) && d.flag(ReactorData.F_VALID)) {
            lines.add(Component.translatable("gui.factoryascent.reactor.bonus_tip").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.factoryascent.reactor.authority_tip").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(SLIDER_X, SLIDER_Y - 2, SLIDER_W, SLIDER_H + 4, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.reactor.slider_tip"));
        } else if (isHovering(SCRAM_X, BTN_Y - 2, SCRAM_W, BTN_H + 4, mouseX, mouseY)) {
            lines.add(Component.translatable(d.flag(ReactorData.F_SCRAM_BUTTON) ? "gui.factoryascent.reactor.scram_release" : "gui.factoryascent.reactor.scram_tip"));
            lines.add(Component.translatable("gui.factoryascent.reactor.scram_redstone_tip").withStyle(ChatFormatting.GRAY));
        }
        if (!lines.isEmpty()) g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    private void button(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x(), my = event.y();
            for (int i = 0; i < 4; i++) {
                if (isHovering(BUTTONS[i][0], BTN_Y, BUTTONS[i][1], BTN_H, mx, my)) {
                    button(i);
                    return true;
                }
            }
            if (isHovering(SCRAM_X, BTN_Y - 2, SCRAM_W, BTN_H + 4, mx, my)) {
                button(ReactorMenu.BTN_SCRAM);
                return true;
            }
            if (isHovering(SLIDER_X, SLIDER_Y - 2, SLIDER_W, SLIDER_H + 4, mx, my)) {
                int pct = (int) Math.round((mx - leftPos - SLIDER_X - 1) * 100.0 / (SLIDER_W - 2));
                button(ReactorMenu.BTN_SET + Math.max(0, Math.min(100, pct)));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
