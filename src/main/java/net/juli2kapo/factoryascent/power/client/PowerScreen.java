package net.juli2kapo.factoryascent.power.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.power.Generator;
import net.juli2kapo.factoryascent.power.PowerBlock;
import net.juli2kapo.factoryascent.power.PowerBlockEntity;
import net.juli2kapo.factoryascent.power.PowerData;
import net.juli2kapo.factoryascent.power.PowerMenu;
import net.juli2kapo.factoryascent.power.SteamEngineBlockEntity;
import net.juli2kapo.factoryascent.power.WindTurbineBlockEntity;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The screen of every power-ladder generator, in the machine screens' style (bevelled grey
 * panel, age accent stripe, the energy bar on the left, status line under the machine area),
 * with each generator's own gauges: the dynamo's armature, the steam engine's thermometer and
 * water tank, the turbine's wind meter, the digester's gas tank, the lava tank, the sun meter and
 * the RTG's decay bar.
 */
public class PowerScreen extends AbstractContainerScreen<PowerMenu> {
    private static final int[] AGE_COLORS = {0xFF9A9A9A, 0xFFD08A3A, 0xFFE0C040, 0xFF4CB050, 0xFF4A78D8, 0xFFB060E0, 0xFF2FD5CF};
    private static final int ENERGY_X = 8, ENERGY_Y = 17, ENERGY_W = 12, ENERGY_H = 54;
    private static final int TEXT = 0xFF404040;

    public PowerScreen(PowerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PowerMenu.WIDTH, PowerMenu.HEIGHT);
        this.inventoryLabelY = PowerMenu.PLAYER_INV_Y - 11;
    }

    private Generator generator() {
        return menu.machine().getBlockState().getBlock() instanceof PowerBlock b ? b.generator() : Generator.KINETIC_DYNAMO;
    }

    private PowerData data() {
        return menu.data();
    }

    private int accent() {
        return AGE_COLORS[generator().age().ordinal()];
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
        for (PowerBlockEntity.SlotSpec s : menu.machine().slotLayout()) {
            if (s.output()) g.fill(x + s.x() - 3, y + s.y() - 3, x + s.x() + 19, y + s.y() + 19, accent());
            FactoryGui.slot(g, x + s.x(), y + s.y());
        }
        FactoryGui.playerInventory(g, x, y, PowerMenu.PLAYER_INV_Y);
        energyBar(g, x + ENERGY_X, y + ENERGY_Y);
        PowerData d = data();
        switch (generator()) {
            case KINETIC_DYNAMO -> dynamo(g, x + 32, y + 22, d.extra(0) / 1000f);
            case STEAM_ENGINE -> {
                flame(g, x + 45, y + 36, d.extra(3) / 1000f);
                thermometer(g, x + 70, y + 17, d.extra(0) / 10f);
                tank(g, x + 90, y + 17, 18, 54, d.extra(1) / (float) Math.max(1, d.extra(2)), 0xFF3F76E4, 0xFF7FB2FF);
                if (d.extra(6) > 0) FactoryGui.bar(g, x + 90, y + 72, 18, 3, d.extra(6) / (float) Math.max(1, d.extra(7)), 0xFFE6ECEF);
                downArrow(g, x + 120, y + 37);
            }
            case WIND_TURBINE -> wind(g, x + 30, y + 20, d.extra(0) / 1000f);
            case BIOGAS_GENERATOR -> {
                arrow(g, x + 66, y + 35, d.extra(2) / 1000f);
                tank(g, x + 96, y + 17, 20, 54, d.extra(0) / (float) Math.max(1, d.extra(1)), 0xFF5E9E32, 0xFFA8E070);
                if (d.status() == PowerBlockEntity.ST_RUNNING) flame(g, x + 124, y + 57, 0.6f + 0.4f * (float) Math.abs(Math.sin(ticks() * 0.3)));
            }
            case MAGMATIC_GENERATOR -> {
                downArrow(g, x + 48, y + 37);
                tank(g, x + 76, y + 17, 26, 54, d.extra(0) / (float) Math.max(1, d.extra(1)), 0xFFD04A10, 0xFFFFA030);
            }
            case SOLAR_ARRAY -> sun(g, x + 30, y + 20, d.extra(0));
            case RTG -> {
                FactoryGui.bar(g, x + 60, y + 56, 56, 6, d.extra(0) / 1000f, 0xFF7FD13B);
                trefoil(g, x + 132, y + 30, 0xFF7FD13B);
            }
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

    /** A vertical tank gauge with a light surface line and tick marks. */
    static void tank(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color, int light) {
        g.fill(x, y, x + w, y + h, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, FactoryGui.LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF22262C);
        int filled = Math.round((h - 2) * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - 1, color);
            g.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - filled, light);
            g.fill(x + 2, y + h - 1 - filled, x + 3, y + h - 1, light);
        }
        for (int i = 1; i < 4; i++) g.fill(x + w - 5, y + i * h / 4, x + w - 1, y + i * h / 4 + 1, 0x90FFFFFF);
    }

    /** A thermometer from 20 to 200 C with the boiling point marked. */
    private static void thermometer(GuiGraphicsExtractor g, int x, int y, float temp) {
        int h = 54, w = 10;
        g.fill(x, y, x + w, y + h, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, FactoryGui.LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF22262C);
        float f = (temp - SteamEngineBlockEntity.AMBIENT) / (SteamEngineBlockEntity.MAX_TEMP - SteamEngineBlockEntity.AMBIENT);
        int filled = Math.round((h - 2) * Math.max(0, Math.min(1, f)));
        int color = temp >= SteamEngineBlockEntity.BOILING ? 0xFFE04A2A : 0xFFE09A3A;
        if (filled > 0) g.fill(x + 2, y + h - 1 - filled, x + w - 2, y + h - 1, color);
        int boil = y + h - 1 - Math.round((h - 2) * (SteamEngineBlockEntity.BOILING - 20) / 180f);
        g.fill(x - 2, boil, x, boil + 1, 0xFF3F76E4);
        int full = y + h - 1 - Math.round((h - 2) * (SteamEngineBlockEntity.FULL_PRESSURE - 20) / 180f);
        g.fill(x - 2, full, x, full + 1, 0xFF40A040);
    }

    /** Kinetic Dynamo: the armature turning in its field coils and how hard it is driven. */
    private void dynamo(GuiGraphicsExtractor g, int x, int y, float points) {
        FactoryGui.display(g, x, y, 112, 34);
        int cx = x + 17, cy = y + 17;
        g.fill(cx - 13, cy - 13, cx - 9, cy + 13, 0xFFB86A2A);
        g.fill(cx + 9, cy - 13, cx + 13, cy + 13, 0xFFB86A2A);
        double a = Math.toRadians(menu.machine().spinAngle);
        for (int spoke = 0; spoke < 3; spoke++) {
            double t = a + spoke * Math.PI / 3;
            for (int r = -7; r <= 7; r++) {
                int px = cx + (int) Math.round(Math.cos(t) * r), py = cy + (int) Math.round(Math.sin(t) * r);
                g.fill(px, py, px + 2, py + 2, 0xFFE0A050);
            }
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF3A2A1A);
        FactoryGui.bar(g, x + 36, y + 13, 70, 8, Math.min(1f, points / 1.2f), points > 0 ? accent() : 0xFF555555);
    }

    /** Wind Turbine: a turning three-blade rotor and the wind meter. */
    private void wind(GuiGraphicsExtractor g, int x, int y, float fraction) {
        FactoryGui.display(g, x, y, 136, 40);
        int cx = x + 20, cy = y + 20;
        double a = Math.toRadians(menu.machine().spinAngle);
        for (int blade = 0; blade < 3; blade++) {
            double t = a + blade * 2 * Math.PI / 3;
            for (int r = 2; r <= 16; r++) {
                int px = cx + (int) Math.round(Math.cos(t) * r), py = cy + (int) Math.round(Math.sin(t) * r);
                g.fill(px, py, px + (r < 10 ? 2 : 1), py + (r < 10 ? 2 : 1), 0xFFE8ECF0);
            }
        }
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF8E96A2);
        FactoryGui.bar(g, x + 42, y + 16, 88, 8, fraction / 1.8f, fraction > 0 ? 0xFF7FB2E5 : 0xFF555555);
    }

    /** Solar Array: a sun that shines as bright as the output. */
    private static void sun(GuiGraphicsExtractor g, int x, int y, int percent) {
        FactoryGui.display(g, x, y, 136, 40);
        int cx = x + 20, cy = y + 20;
        int c = percent <= 0 ? 0xFF404652 : percent < 100 ? 0xFFB0A040 : 0xFFFFE066;
        g.fill(cx - 6, cy - 6, cx + 7, cy + 7, c);
        for (int i = 0; i < 8; i++) {
            double t = i * Math.PI / 4;
            int px = cx + (int) Math.round(Math.cos(t) * 11), py = cy + (int) Math.round(Math.sin(t) * 11);
            g.fill(px - 1, py - 1, px + 2, py + 2, c);
        }
        FactoryGui.bar(g, x + 42, y + 16, 88, 8, percent / 150f, percent > 0 ? 0xFFFFD23F : 0xFF555555);
    }

    /** The radiation trefoil (16x16). */
    static void trefoil(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 6, y + 6, x + 10, y + 10, color);
        for (int blade = 0; blade < 3; blade++) {
            double t = Math.toRadians(-90 + blade * 120);
            for (int r = 4; r <= 8; r++) {
                for (int s = -r / 2; s <= r / 2; s++) {
                    double u = t + s * 0.13;
                    int px = x + 8 + (int) Math.round(Math.cos(u) * r), py = y + 8 + (int) Math.round(Math.sin(u) * r);
                    g.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    // ---------------------------------------------------------------- labels

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, FactoryGui.fit(font, title, imageWidth - 16), 8, 7, TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        Component status = statusLine();
        g.text(font, status, (imageWidth - font.width(status)) / 2, 76, TEXT, false);
        PowerData d = data();
        Component info = switch (generator()) {
            case KINETIC_DYNAMO -> Component.translatable("gui.factoryascent.power.dynamo_info", d.extra(0) / 10, d.extra(1));
            case STEAM_ENGINE -> Component.translatable("gui.factoryascent.power.steam_info", d.extra(0) / 10, d.extra(4));
            case WIND_TURBINE -> Component.translatable("gui.factoryascent.power.wind_info", d.extra(1), WindTurbineBlockEntity.MIN_MAST, d.extra(2));
            case BIOGAS_GENERATOR -> Component.translatable("gui.factoryascent.power.gas_info", d.extra(0), d.extra(1));
            case MAGMATIC_GENERATOR -> Component.translatable("gui.factoryascent.power.lava_info", d.extra(0), d.extra(1));
            case SOLAR_ARRAY -> Component.translatable("gui.factoryascent.sunlight", d.extra(0));
            case RTG -> Component.translatable("gui.factoryascent.power.rtg_info", d.extra(1) / 60, d.extra(1) % 60);
        };
        int left = 26, right = imageWidth - 4;
        var line = FactoryGui.fit(font, info, right - left);
        int infoY = switch (generator()) {
            case KINETIC_DYNAMO -> 60;
            case WIND_TURBINE, SOLAR_ARRAY -> 64;
            default -> 65;
        };
        if (generator() == Generator.STEAM_ENGINE || generator() == Generator.BIOGAS_GENERATOR
                || generator() == Generator.MAGMATIC_GENERATOR) {
            return; // their gauges fill the row: the numbers are in the tooltips
        }
        g.text(font, line, left + (right - left - font.width(line)) / 2, infoY, TEXT, false);
    }

    private Component statusLine() {
        int st = Math.max(0, Math.min(PowerBlockEntity.STATUS_KEYS.length - 1, data().status()));
        ChatFormatting color = switch (st) {
            case PowerBlockEntity.ST_RUNNING -> ChatFormatting.DARK_GREEN;
            case PowerBlockEntity.ST_HEATING, PowerBlockEntity.ST_DIGESTING, PowerBlockEntity.ST_FULL, PowerBlockEntity.ST_NIGHT -> ChatFormatting.GOLD;
            case PowerBlockEntity.ST_IDLE -> ChatFormatting.DARK_GRAY;
            default -> ChatFormatting.DARK_RED;
        };
        Component s = Component.translatable("status.factoryascent.power." + PowerBlockEntity.STATUS_KEYS[st]).withStyle(color);
        if (data().rate() > 0) s = Component.translatable("gui.factoryascent.power.status_rate", s, data().rate()).withStyle(color);
        return s;
    }

    // ---------------------------------------------------------------- tooltips

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        PowerData d = data();
        List<Component> lines = new ArrayList<>();
        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.energy", EnergyUtil.format(d.energy()), EnergyUtil.format(d.capacity())));
            lines.add(Component.translatable("gui.factoryascent.generating", d.rate()).withStyle(ChatFormatting.GREEN));
        } else {
            switch (generator()) {
                case STEAM_ENGINE -> {
                    if (isHovering(68, 17, 14, 54, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.factoryascent.power.temperature", d.extra(0) / 10));
                        lines.add(Component.translatable("gui.factoryascent.power.pressure", d.extra(4)).withStyle(ChatFormatting.GRAY));
                        lines.add(Component.translatable("gui.factoryascent.power.steam_hint").withStyle(ChatFormatting.DARK_GRAY));
                    } else if (isHovering(90, 17, 18, 54, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.factoryascent.power.water", d.extra(1), d.extra(2)));
                        lines.add(Component.translatable("gui.factoryascent.power.water_sources", d.extra(5)).withStyle(ChatFormatting.GRAY));
                        if (d.extra(6) > 0) {
                            lines.add(Component.translatable("gui.factoryascent.power.steam_chest", d.extra(6), d.extra(7)).withStyle(ChatFormatting.AQUA));
                        }
                    }
                }
                case BIOGAS_GENERATOR -> {
                    if (isHovering(96, 17, 20, 54, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.factoryascent.power.gas_info", d.extra(0), d.extra(1)));
                        lines.add(Component.translatable("gui.factoryascent.power.gas_hint").withStyle(ChatFormatting.GRAY));
                    }
                }
                case MAGMATIC_GENERATOR -> {
                    if (isHovering(76, 17, 26, 54, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.factoryascent.power.lava_info", d.extra(0), d.extra(1)));
                        lines.add(Component.translatable("gui.factoryascent.power.lava_hint").withStyle(ChatFormatting.GRAY));
                    }
                }
                case WIND_TURBINE -> {
                    if (isHovering(30, 20, 136, 40, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.factoryascent.power.wind_hint", WindTurbineBlockEntity.MIN_MAST).withStyle(ChatFormatting.GRAY));
                    }
                }
                case RTG -> {
                    if (isHovering(60, 54, 56, 10, mouseX, mouseY)) {
                        lines.add(Component.translatable("gui.factoryascent.power.rtg_decay", d.extra(0) / 10));
                    }
                }
                default -> { }
            }
        }
        if (!lines.isEmpty()) g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }
}
