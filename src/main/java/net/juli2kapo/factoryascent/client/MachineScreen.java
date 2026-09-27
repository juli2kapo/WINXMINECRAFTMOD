package net.juli2kapo.factoryascent.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineData;
import net.juli2kapo.factoryascent.machine.MachineLayout;
import net.juli2kapo.factoryascent.machine.MachineMenu;
import net.juli2kapo.factoryascent.machine.MachineSlots;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * One screen for every machine, drawn entirely from rectangles (no GUI textures), in the
 * vanilla bevelled style with a tier-coloured accent.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int BG = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int[] TIER_COLORS = {0xFFB8B8B8, 0xFF5A6B8C, 0xFFD9822B, 0xFF9B59D0, 0xFF2FD5CF};

    private static final int ENERGY_X = 8, ENERGY_Y = 17, ENERGY_W = 12, ENERGY_H = 54;
    private static final int EJECT_W = 14, EJECT_H = 11;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MachineLayout.WIDTH, MachineLayout.HEIGHT);
        this.inventoryLabelY = MachineLayout.PLAYER_INV_Y - 11;
    }

    private MachineType type() {
        return menu.machine().type();
    }

    private Tier tier() {
        return menu.machine().tier();
    }

    private MachineData data() {
        return menu.data();
    }

    private boolean hasEjectButton() {
        return menu.machine().inventory().slots().outputs() > 0;
    }

    private int ejectX() {
        return imageWidth - EJECT_W - 6;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        panel(g, x, y, imageWidth, imageHeight);
        int accent = TIER_COLORS[tier().ordinal()];
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + 5, accent);

        MachineSlots s = menu.machine().inventory().slots();
        MachineType type = type();
        for (int i = 0; i < s.inputs(); i++) slot(g, MachineLayout.input(type, i), false);
        for (int i = 0; i < s.mold(); i++) slot(g, MachineLayout.mold(i), false);
        for (int i = 0; i < s.fuel(); i++) slot(g, MachineLayout.fuel(i), false);
        for (int i = 0; i < s.outputs(); i++) slot(g, MachineLayout.output(type, i), true);
        for (int i = 0; i < s.upgrades(); i++) {
            MachineLayout.Pos p = MachineLayout.upgrade(i);
            slot(g, p, false);
            if (i >= tier().upgradeSlots()) lockedSlot(g, p);
        }
        for (int i = 0; i < 27; i++) slot(g, new MachineLayout.Pos(8 + (i % 9) * 18, MachineLayout.PLAYER_INV_Y + (i / 9) * 18), false);
        for (int i = 0; i < 9; i++) slot(g, new MachineLayout.Pos(8 + i * 18, MachineLayout.PLAYER_INV_Y + 58), false);

        energyBar(g, x + ENERGY_X, y + ENERGY_Y);
        switch (type.category()) {
            case PROCESSOR -> arrow(g, x + MachineLayout.arrowX(type), y + MachineLayout.ARROW_Y, data().progress() / 1000f);
            case MINER -> depthGauge(g, x + 62, y + 22);
            case GENERATOR -> {
                if (type == MachineType.COMBUSTION_GENERATOR) flame(g, x + 81, y + 30, data().progress() / 1000f);
            }
            default -> {}
        }
        if (hasEjectButton()) {
            boolean on = data().autoEject();
            int bx = x + ejectX(), by = y + 6;
            boolean hover = mouseX >= bx && mouseX < bx + EJECT_W && mouseY >= by && mouseY < by + EJECT_H;
            g.fill(bx, by, bx + EJECT_W, by + EJECT_H, OUTLINE);
            g.fill(bx + 1, by + 1, bx + EJECT_W - 1, by + EJECT_H - 1, hover ? 0xFFA0A0A0 : 0xFF8B8B8B);
            int c = on ? 0xFF3FD13F : 0xFF6E2A2A;
            // an arrow pointing out of a box
            g.fill(bx + 3, by + 5, bx + 9, by + 6, c);
            g.fill(bx + 8, by + 3, bx + 9, by + 8, c);
            g.fill(bx + 9, by + 4, bx + 10, by + 7, c);
            g.fill(bx + 10, by + 5, bx + 11, by + 6, c);
        }
    }

    private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BG);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + h - 2, LIGHT);
        g.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADOW);
    }

    private void slot(GuiGraphicsExtractor g, MachineLayout.Pos p, boolean output) {
        int x = leftPos + p.x() - 1, y = topPos + p.y() - 1;
        if (output) {
            g.fill(x - 2, y - 2, x + 20, y + 20, TIER_COLORS[tier().ordinal()]);
        }
        g.fill(x, y, x + 18, y + 18, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 18, y + 18, LIGHT);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
    }

    private void lockedSlot(GuiGraphicsExtractor g, MachineLayout.Pos p) {
        int x = leftPos + p.x(), y = topPos + p.y();
        g.fill(x, y, x + 16, y + 16, 0xAA3A3A3A);
        for (int i = 2; i < 14; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFF8A2A2A);
            g.fill(x + 15 - i, y + i, x + 16 - i, y + i + 1, 0xFF8A2A2A);
        }
    }

    private void energyBar(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + ENERGY_W, y + ENERGY_H, SLOT_DARK);
        g.fill(x + 1, y + 1, x + ENERGY_W, y + ENERGY_H, LIGHT);
        g.fill(x + 1, y + 1, x + ENERGY_W - 1, y + ENERGY_H - 1, 0xFF2A1010);
        int cap = data().capacity();
        if (cap <= 0) return;
        int inner = ENERGY_H - 2;
        int filled = (int) Math.round((double) Math.max(0, data().energy()) / cap * inner);
        for (int i = 0; i < filled; i++) {
            float t = (float) i / inner;
            int r = 0xD0, gr = (int) (0x30 + 0xB0 * t), b = 0x20;
            int color = 0xFF000000 | (r << 16) | (Math.min(255, gr) << 8) | b;
            int yy = y + ENERGY_H - 2 - i;
            g.fill(x + 1, yy, x + ENERGY_W - 1, yy + 1, color);
        }
        for (int i = 1; i < 6; i++) {
            int yy = y + 1 + i * inner / 6;
            g.fill(x + 1, yy, x + 4, yy + 1, 0x66000000);
        }
    }

    private static void arrow(GuiGraphicsExtractor g, int x, int y, float fraction) {
        // A 24x16 arrow: shaft 16px wide, head 8px. Grey outline, white fill as progress.
        drawArrow(g, x, y, 24, 0xFF8B8B8B);
        if (fraction > 0) {
            g.enableScissor(x, y, x + Math.round(24 * fraction), y + 16);
            drawArrow(g, x, y, 24, 0xFFFFFFFF);
            g.disableScissor();
        }
    }

    private static void drawArrow(GuiGraphicsExtractor g, int x, int y, int w, int color) {
        g.fill(x, y + 6, x + w - 8, y + 10, color);
        for (int i = 0; i < 8; i++) {
            g.fill(x + w - 8 + i, y + 1 + i, x + w - 7 + i, y + 15 - i, color);
        }
    }

    private void flame(GuiGraphicsExtractor g, int x, int y, float fraction) {
        g.fill(x, y, x + 14, y + 14, 0xFF6B6B6B);
        int h = Math.round(14 * fraction);
        if (h > 0) {
            g.fill(x + 3, y + 14 - h, x + 11, y + 14, 0xFFFF9A1F);
            g.fill(x + 5, y + 14 - Math.max(1, h * 2 / 3), x + 9, y + 14, 0xFFFFE066);
        }
    }

    private void depthGauge(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 8, y + 44, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 7, y + 43, 0xFF3A3A3A);
        int h = Math.round(42 * data().progress() / 1000f);
        g.fill(x + 1, y + 1, x + 7, y + 1 + h, 0xFF7FB2E5);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Component name = title.copy().withStyle(tier().color());
        g.text(font, name, 8, 7, TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        Component status = statusLine();
        g.text(font, status, (imageWidth - font.width(status)) / 2, 76, TEXT, false);
        String info = infoLine();
        if (!info.isEmpty()) {
            if (type().category() == MachineType.Category.MINER) {
                g.text(font, info, 90, 64, TEXT, false);
            } else if (type().category() != MachineType.Category.PROCESSOR) {
                g.text(font, info, (imageWidth - font.width(info)) / 2, 22, TEXT, false);
            }
        }
    }

    private Component statusLine() {
        String key = switch (data().status()) {
            case AbstractMachineBlockEntity.STATUS_WORKING -> "working";
            case AbstractMachineBlockEntity.STATUS_NO_POWER -> "no_power";
            case AbstractMachineBlockEntity.STATUS_OUTPUT_FULL -> "output_full";
            case AbstractMachineBlockEntity.STATUS_TIER_TOO_LOW -> "tier_too_low";
            case AbstractMachineBlockEntity.STATUS_NO_FUEL -> "no_fuel";
            case AbstractMachineBlockEntity.STATUS_FULL -> "full";
            default -> type().category() == MachineType.Category.MINER && data().progress() >= 1000 ? "finished" : "idle";
        };
        ChatFormatting color = switch (data().status()) {
            case AbstractMachineBlockEntity.STATUS_WORKING -> ChatFormatting.DARK_GREEN;
            case AbstractMachineBlockEntity.STATUS_NO_POWER, AbstractMachineBlockEntity.STATUS_TIER_TOO_LOW,
                 AbstractMachineBlockEntity.STATUS_OUTPUT_FULL -> ChatFormatting.DARK_RED;
            default -> ChatFormatting.DARK_GRAY;
        };
        return Component.translatable("status.factoryascent." + key).withStyle(color);
    }

    private String infoLine() {
        MachineData d = data();
        return switch (type()) {
            case SOLAR_PANEL -> Component.translatable("gui.factoryascent.sunlight", d.extraA()).getString();
            case GEOTHERMAL_GENERATOR -> Component.translatable("gui.factoryascent.lava", d.extraA()).getString();
            case COMBUSTION_GENERATOR, ENERGY_CELL -> "";
            case MINER -> Component.translatable("gui.factoryascent.miner_info", d.extraA(), d.extraB()).getString();
            default -> "";
        };
    }

    // ---------------------------------------------------------------- tooltips & input

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, energyTooltip(), mouseX, mouseY);
        } else if (type().category() == MachineType.Category.PROCESSOR
                && isHovering(MachineLayout.arrowX(type()), MachineLayout.ARROW_Y, 24, 16, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.factoryascent.progress", data().progress() / 10),
                    Component.translatable("gui.factoryascent.rate", format(data().itemRateX10())),
                    Component.translatable("gui.factoryascent.speed", data().speedPercent())), mouseX, mouseY);
        } else if (type().category() == MachineType.Category.MINER && isHovering(62, 22, 8, 44, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.factoryascent.miner_progress", data().progress() / 10),
                    Component.translatable("gui.factoryascent.miner_rate", format(data().itemRateX10()))), mouseX, mouseY);
        } else if (hasEjectButton() && isHovering(ejectX(), 6, EJECT_W, EJECT_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable(
                    data().autoEject() ? "gui.factoryascent.eject_on" : "gui.factoryascent.eject_off")), mouseX, mouseY);
        } else {
            MachineSlots s = menu.machine().inventory().slots();
            for (int i = tier().upgradeSlots(); i < s.upgrades(); i++) {
                MachineLayout.Pos p = MachineLayout.upgrade(i);
                if (isHovering(p.x(), p.y(), 16, 16, mouseX, mouseY)) {
                    Tier unlock = i == 1 ? Tier.REINFORCED : Tier.ADVANCED;
                    g.setComponentTooltipForNextFrame(font, List.of(
                            Component.translatable("gui.factoryascent.slot_locked", unlock.displayName())), mouseX, mouseY);
                }
            }
        }
    }

    private List<Component> energyTooltip() {
        MachineData d = data();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.factoryascent.energy",
                EnergyUtil.format(d.energy()), EnergyUtil.format(d.capacity())));
        switch (type().category()) {
            case GENERATOR -> lines.add(Component.translatable("gui.factoryascent.generating", d.energyRate())
                    .withStyle(ChatFormatting.GREEN));
            case STORAGE -> {
                lines.add(Component.translatable("gui.factoryascent.cell_in", EnergyUtil.format(d.extraA())).withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("gui.factoryascent.cell_out", EnergyUtil.format(d.extraB())).withStyle(ChatFormatting.GOLD));
            }
            default -> lines.add(Component.translatable("gui.factoryascent.using", d.energyRate()).withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    private static String format(int x10) {
        return x10 % 10 == 0 ? Integer.toString(x10 / 10) : String.format("%.1f", x10 / 10.0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (hasEjectButton() && event.button() == 0 && isHovering(ejectX(), 6, EJECT_W, EJECT_H, event.x(), event.y())) {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_TOGGLE_EJECT);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
