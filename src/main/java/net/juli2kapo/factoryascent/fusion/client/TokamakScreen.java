package net.juli2kapo.factoryascent.fusion.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.fusion.TokamakCoreBlockEntity;
import net.juli2kapo.factoryascent.fusion.TokamakData;
import net.juli2kapo.factoryascent.fusion.TokamakMenu;
import net.juli2kapo.factoryascent.fusion.TokamakStructure;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The tokamak's screen: the plasma torus seen from the side (dark when cold, a swirling violet
 * ring while it burns), the magnet charge and output gauges, fuel slots with their burn bars, and
 * the start/stop switch.
 */
public class TokamakScreen extends AbstractContainerScreen<TokamakMenu> {
    private static final int ACCENT = 0xFF2FD5CF; // quantum age
    private static final int VIEW_X = 8, VIEW_Y = 18, VIEW_W = 108, VIEW_H = 44;
    private static final int CHARGE_X = 124, OUT_X = 144, BAR_Y = 18, BAR_W = 12, BAR_H = 44;
    private static final int BTN_X = 76, BTN_Y = 70, BTN_W = 66, BTN_H = 16;

    public TokamakScreen(TokamakMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TokamakMenu.WIDTH, TokamakMenu.HEIGHT);
        this.inventoryLabelY = TokamakMenu.INV_Y - 11;
    }

    private TokamakData d() {
        return menu.data();
    }

    private TokamakCoreBlockEntity.State state() {
        int s = d().i(TokamakData.STATE);
        return TokamakCoreBlockEntity.State.values()[Math.max(0, Math.min(TokamakCoreBlockEntity.State.values().length - 1, s))];
    }

    private long ticks() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        FactoryGui.panel(g, x, y, imageWidth, imageHeight, state() == TokamakCoreBlockEntity.State.DISRUPTED ? FactoryGui.BAD : ACCENT);
        plasma(g, x + VIEW_X, y + VIEW_Y, d().i(TokamakData.PLASMA) / 1000f, partial);
        float charge = d().i(TokamakData.CHARGE_K) / (float) Math.max(1, d().i(TokamakData.STARTUP_K));
        if (state() == TokamakCoreBlockEntity.State.RUNNING || state() == TokamakCoreBlockEntity.State.IGNITING) charge = 1f;
        vbar(g, x + CHARGE_X, y + BAR_Y, charge, 0xFFB060E0, 0xFFE0B0FF);
        vbar(g, x + OUT_X, y + BAR_Y, d().i(TokamakData.ENERGY) / (float) Math.max(1, d().i(TokamakData.CAPACITY)), 0xFFD0A020, 0xFFFFE070);
        for (int i = 0; i < TokamakCoreBlockEntity.SLOTS; i++) {
            int sx = x + TokamakMenu.SLOT_X[i], sy = y + TokamakMenu.SLOT_Y;
            if (i == TokamakCoreBlockEntity.EMPTY_OUT) g.fill(sx - 3, sy - 3, sx + 19, sy + 19, ACCENT);
            FactoryGui.slot(g, sx, sy);
        }
        FactoryGui.bar(g, x + TokamakMenu.SLOT_X[0] - 1, y + TokamakMenu.SLOT_Y + 19, 18, 4, d().i(TokamakData.D_BURN) / 1000f, 0xFF7FB2FF);
        FactoryGui.bar(g, x + TokamakMenu.SLOT_X[1] - 1, y + TokamakMenu.SLOT_Y + 19, 18, 4, d().i(TokamakData.H_BURN) / 1000f, 0xFFFFD0A0);
        boolean on = d().i(TokamakData.ENABLED) != 0;
        FactoryGui.button(g, x + BTN_X, y + BTN_Y, BTN_W, BTN_H, FactoryGui.inside(mouseX, mouseY, x + BTN_X, y + BTN_Y, BTN_W, BTN_H), on, ACCENT);
        FactoryGui.lamp(g, x + BTN_X + 4, y + BTN_Y + 4, on ? FactoryGui.GOOD : 0xFF6A2A2A);
        FactoryGui.playerInventory(g, x, y, TokamakMenu.INV_Y);
    }

    /** Side view of the torus: two plasma lobes either side of the central solenoid. */
    private void plasma(GuiGraphicsExtractor g, int x, int y, float level, float partial) {
        FactoryGui.display(g, x, y, VIEW_W, VIEW_H);
        int cx = x + VIEW_W / 2, cy = y + VIEW_H / 2;
        // vessel walls and magnets
        for (int side = -1; side <= 1; side += 2) {
            int lx = cx + side * 30;
            g.fill(lx - 14, cy - 14, lx + 14, cy - 12, 0xFF505866);
            g.fill(lx - 14, cy + 12, lx + 14, cy + 14, 0xFF505866);
            g.fill(lx - 16, cy - 14, lx - 14, cy + 14, 0xFFB86A2A);
            g.fill(lx + 14, cy - 14, lx + 16, cy + 14, 0xFFB86A2A);
        }
        g.fill(cx - 5, y + 4, cx + 5, y + VIEW_H - 4, 0xFFB86A2A); // central solenoid
        g.fill(cx - 3, y + 4, cx + 3, y + VIEW_H - 4, 0xFFD89050);
        if (level <= 0) return;
        float t = ticks() + partial;
        for (int side = -1; side <= 1; side += 2) {
            int lx = cx + side * 30;
            for (int r = 10; r >= 1; r--) {
                float f = r / 10f;
                int rx = Math.round(11 * f * level), ry = Math.round(9 * f * level);
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.4f + r + side);
                int a = (int) (70 + 150 * (1 - f)), rr = 180 + (int) (60 * (1 - f)), gg = (int) (80 + 120 * (1 - f) * pulse), bb = 255;
                g.fill(lx - rx, cy - ry, lx + rx + 1, cy + ry + 1, (a << 24) | (rr << 16) | (gg << 8) | bb);
            }
            // swirling filament
            for (int i = 0; i < 10; i++) {
                double ang = t * 0.25 * side + i * Math.PI / 5;
                int px = lx + (int) Math.round(Math.cos(ang) * 8 * level), py = cy + (int) Math.round(Math.sin(ang) * 6 * level);
                g.fill(px, py, px + 2, py + 2, 0xFFFFF0FF);
            }
        }
    }

    private static void vbar(GuiGraphicsExtractor g, int x, int y, float fraction, int color, int light) {
        g.fill(x, y, x + BAR_W, y + BAR_H, FactoryGui.SLOT_DARK);
        g.fill(x + 1, y + 1, x + BAR_W, y + BAR_H, FactoryGui.LIGHT);
        g.fill(x + 1, y + 1, x + BAR_W - 1, y + BAR_H - 1, 0xFF22262C);
        int filled = Math.round((BAR_H - 2) * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x + 1, y + BAR_H - 1 - filled, x + BAR_W - 1, y + BAR_H - 1, color);
            g.fill(x + 1, y + BAR_H - 1 - filled, x + BAR_W - 1, y + BAR_H - filled, light);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, FactoryGui.fit(font, title, 90), 8, 6, FactoryGui.TEXT, false);
        Component status = status();
        g.text(font, status, imageWidth - 8 - font.width(status), 6, FactoryGui.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, FactoryGui.TEXT, false);
        boolean on = d().i(TokamakData.ENABLED) != 0;
        Component label = Component.translatable(on ? "gui.factoryascent.tokamak.stop" : "gui.factoryascent.tokamak.start");
        g.text(font, FactoryGui.fit(font, label, BTN_W - 16), BTN_X + 14, BTN_Y + 4, on ? 0xFFFFFFFF : 0xFF202020, false);
        Component info = state() == TokamakCoreBlockEntity.State.RUNNING
                ? Component.translatable("gui.factoryascent.tokamak.output", EnergyUtil.format(d().i(TokamakData.RATE)))
                : Component.translatable("gui.factoryascent.tokamak.charge", EnergyUtil.format(d().i(TokamakData.CHARGE_K) * 1000L),
                EnergyUtil.format(d().i(TokamakData.STARTUP_K) * 1000L));
        g.text(font, FactoryGui.fit(font, info, 160), 8, 94, FactoryGui.TEXT, false);
    }

    private Component status() {
        TokamakData d = d();
        if (d.i(TokamakData.ERROR) != 0 && state() != TokamakCoreBlockEntity.State.DISRUPTED) {
            return Component.translatable("gui.factoryascent.tokamak.incomplete").withStyle(ChatFormatting.DARK_RED);
        }
        String key = "gui.factoryascent.tokamak.state." + state().name().toLowerCase(java.util.Locale.ROOT);
        ChatFormatting c = switch (state()) {
            case RUNNING -> ChatFormatting.DARK_GREEN;
            case IGNITING, CHARGING, READY -> ChatFormatting.GOLD;
            case DISRUPTED -> ChatFormatting.DARK_RED;
            default -> ChatFormatting.DARK_GRAY;
        };
        if (state() == TokamakCoreBlockEntity.State.RUNNING || state() == TokamakCoreBlockEntity.State.IGNITING) {
            int mk = Math.round(150 * d.i(TokamakData.PLASMA) / 1000f);
            return Component.translatable("gui.factoryascent.tokamak.state_temp", Component.translatable(key), mk).withStyle(c);
        }
        return Component.translatable(key).withStyle(c);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        TokamakData d = d();
        List<Component> lines = new ArrayList<>();
        if (isHovering(CHARGE_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.tokamak.charge", EnergyUtil.format(d.i(TokamakData.CHARGE_K) * 1000L),
                    EnergyUtil.format(d.i(TokamakData.STARTUP_K) * 1000L)));
            lines.add(Component.translatable("gui.factoryascent.tokamak.charge_hint").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(OUT_X, BAR_Y, BAR_W, BAR_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.energy", EnergyUtil.format(d.i(TokamakData.ENERGY)), EnergyUtil.format(d.i(TokamakData.CAPACITY))));
            lines.add(Component.translatable("gui.factoryascent.generating", d.i(TokamakData.RATE)).withStyle(ChatFormatting.GREEN));
        } else if (isHovering(VIEW_X, VIEW_Y, VIEW_W, VIEW_H, mouseX, mouseY)) {
            if (d.i(TokamakData.ERROR) != 0) {
                var e = TokamakStructure.Error.values()[Math.min(TokamakStructure.Error.values().length - 1, d.i(TokamakData.ERROR))];
                lines.add(Component.translatable("gui.factoryascent.tokamak.error." + e.name().toLowerCase(java.util.Locale.ROOT),
                        d.i(TokamakData.BAD_X), d.i(TokamakData.BAD_Y), d.i(TokamakData.BAD_Z)).withStyle(ChatFormatting.RED));
            }
            lines.add(Component.translatable("gui.factoryascent.tokamak.how").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(BTN_X, BTN_Y, BTN_W, BTN_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.factoryascent.tokamak.button_tip"));
        } else {
            for (int i = 0; i < 3; i++) {
                if (menu.getSlot(i).getItem().isEmpty() && isHovering(TokamakMenu.SLOT_X[i], TokamakMenu.SLOT_Y, 16, 16, mouseX, mouseY)) {
                    lines.add(Component.translatable("gui.factoryascent.tokamak.slot." + i));
                }
            }
        }
        if (!lines.isEmpty()) g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isHovering(BTN_X, BTN_Y, BTN_W, BTN_H, event.x(), event.y())
                && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TokamakMenu.BTN_TOGGLE);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
