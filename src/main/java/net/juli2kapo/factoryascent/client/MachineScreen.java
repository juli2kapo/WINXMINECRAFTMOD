package net.juli2kapo.factoryascent.client;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.item.ItemStack;

/**
 * One screen for every machine, drawn entirely from rectangles (no GUI textures), in the
 * vanilla bevelled style with an accent colour per age.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int BG = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int BAD = 0xFFD03030;
    /** Accent per age: stone, bronze, electric, automation, industrial, orbital, quantum. */
    private static final int[] AGE_COLORS = {0xFF9A9A9A, 0xFFD08A3A, 0xFFE0C040, 0xFF4CB050, 0xFF4A78D8, 0xFFB060E0, 0xFF2FD5CF};

    private static final int ENERGY_X = 8, ENERGY_Y = 17, ENERGY_W = 12, ENERGY_H = 54;
    private static final int BTN_W = 14, BTN_H = 11, BTN_Y = 6;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MachineLayout.WIDTH, MachineLayout.HEIGHT);
        this.inventoryLabelY = MachineLayout.PLAYER_INV_Y - 11;
    }

    private MachineType type() {
        return menu.machine().type();
    }

    private MachineData data() {
        return menu.data();
    }

    private int accent() {
        return AGE_COLORS[type().age().ordinal()];
    }

    private boolean hasEjectButton() {
        return menu.machine().inventory().slots().outputs() > 0;
    }

    private boolean hasRecipeButton() {
        return type().recipeKind() != null;
    }

    private int ejectX() {
        return imageWidth - BTN_W - 6;
    }

    private int recipeX() {
        return imageWidth - 2 * BTN_W - 9;
    }

    private static final int CRANK_X = 8, CRANK_Y = 58, CRANK_W = 24, CRANK_H = 13;

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        panel(g, x, y, imageWidth, imageHeight);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + 5, accent());

        MachineSlots s = menu.machine().inventory().slots();
        MachineType type = type();
        for (int i = 0; i < s.inputs(); i++) {
            MachineLayout.Pos p = MachineLayout.input(type, i);
            ItemStack stack = menu.machine().inventory().stack(s.firstInput() + i);
            slot(g, p, false, type.category() == MachineType.Category.PROCESSOR && !ClientRecipes.accepts(type, stack));
        }
        for (int i = 0; i < s.mold(); i++) slot(g, MachineLayout.mold(type), false, false);
        for (int i = 0; i < s.fuel(); i++) slot(g, MachineLayout.fuel(type), false, false);
        for (int i = 0; i < s.outputs(); i++) slot(g, MachineLayout.output(type, i), true, false);
        for (int i = 0; i < s.upgrades(); i++) slot(g, MachineLayout.upgrade(i), false, false);
        for (int i = 0; i < 27; i++) slot(g, new MachineLayout.Pos(8 + (i % 9) * 18, MachineLayout.PLAYER_INV_Y + (i / 9) * 18), false, false);
        for (int i = 0; i < 9; i++) slot(g, new MachineLayout.Pos(8 + i * 18, MachineLayout.PLAYER_INV_Y + 58), false, false);

        if (type.usesEnergy()) energyBar(g, x + ENERGY_X, y + ENERGY_Y);
        if (type.power() == MachineType.Power.MANUAL) {
            crankGauge(g, x + ENERGY_X, y + ENERGY_Y);
            button(g, x + CRANK_X, y + CRANK_Y, CRANK_W, CRANK_H, mouseX, mouseY);
        }
        if (MachineLayout.hasFlame(type)) flame(g, x + MachineLayout.FLAME_X, y + MachineLayout.FLAME_Y, data().extraA() / 1000f);
        switch (type.category()) {
            case PROCESSOR -> arrow(g, x + MachineLayout.arrowX(type), y + MachineLayout.ARROW_Y, data().progress() / 1000f);
            case MINER, FARMER -> depthGauge(g, x + 62, y + 22);
            case STORAGE -> bolt(g, x + 100, y + 36);
            default -> {
                if (type == MachineType.COMBUSTION_GENERATOR) flame(g, x + 81, y + 30, data().progress() / 1000f);
            }
        }
        if (hasEjectButton()) {
            int bx = x + ejectX(), by = y + BTN_Y;
            button(g, bx, by, BTN_W, BTN_H, mouseX, mouseY);
            int c = data().autoEject() ? 0xFF3FD13F : 0xFF6E2A2A;
            g.fill(bx + 3, by + 5, bx + 9, by + 6, c);
            g.fill(bx + 8, by + 3, bx + 9, by + 8, c);
            g.fill(bx + 9, by + 4, bx + 10, by + 7, c);
            g.fill(bx + 10, by + 5, bx + 11, by + 6, c);
        }
        if (hasRecipeButton()) {
            int bx = x + recipeX(), by = y + BTN_Y;
            button(g, bx, by, BTN_W, BTN_H, mouseX, mouseY);
        }
    }

    private void button(GuiGraphicsExtractor g, int bx, int by, int w, int h, int mouseX, int mouseY) {
        boolean hover = mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + h;
        g.fill(bx, by, bx + w, by + h, OUTLINE);
        g.fill(bx + 1, by + 1, bx + w - 1, by + h - 1, hover ? 0xFFA0A0A0 : 0xFF8B8B8B);
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

    private void slot(GuiGraphicsExtractor g, MachineLayout.Pos p, boolean output, boolean bad) {
        int x = leftPos + p.x() - 1, y = topPos + p.y() - 1;
        if (output) g.fill(x - 2, y - 2, x + 20, y + 20, accent());
        if (bad) g.fill(x - 1, y - 1, x + 19, y + 19, BAD);
        g.fill(x, y, x + 18, y + 18, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 18, y + 18, LIGHT);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FILL);
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
            int gr = Math.min(255, (int) (0x30 + 0xB0 * t));
            int yy = y + ENERGY_H - 2 - i;
            g.fill(x + 1, yy, x + ENERGY_W - 1, yy + 1, 0xFF000000 | (0xD0 << 16) | (gr << 8) | 0x20);
        }
    }

    private void crankGauge(GuiGraphicsExtractor g, int x, int y) {
        int h = 38;
        g.fill(x, y, x + ENERGY_W, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + ENERGY_W - 1, y + h - 1, 0xFF3A3A3A);
        int filled = Math.round((h - 2) * Math.min(1000, data().extraA()) / 1000f);
        g.fill(x + 1, y + h - 1 - filled, x + ENERGY_W - 1, y + h - 1, 0xFFB0B0B0);
    }

    private static void arrow(GuiGraphicsExtractor g, int x, int y, float fraction) {
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

    private static void flame(GuiGraphicsExtractor g, int x, int y, float fraction) {
        g.fill(x, y, x + 14, y + 14, 0xFF6B6B6B);
        int h = Math.round(14 * fraction);
        if (h > 0) {
            g.fill(x + 3, y + 14 - h, x + 11, y + 14, 0xFFFF9A1F);
            g.fill(x + 5, y + 14 - Math.max(1, h * 2 / 3), x + 9, y + 14, 0xFFFFE066);
        }
    }

    private static void bolt(GuiGraphicsExtractor g, int x, int y) {
        int c = 0xFFFFD23F;
        g.fill(x + 4, y, x + 8, y + 7, c);
        g.fill(x + 2, y + 6, x + 10, y + 8, c);
        g.fill(x + 4, y + 8, x + 8, y + 15, c);
    }

    private void depthGauge(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 8, y + 44, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 7, y + 43, 0xFF3A3A3A);
        int h = Math.round(42 * data().progress() / 1000f);
        g.fill(x + 1, y + 1, x + 7, y + 1 + h, 0xFF7FB2E5);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 7, TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);
        Component status = statusLine();
        g.text(font, status, (imageWidth - font.width(status)) / 2, 76, TEXT, false);
        String info = infoLine();
        if (!info.isEmpty()) g.text(font, info, (imageWidth - font.width(info)) / 2, 64, TEXT, false);
        if (type().power() == MachineType.Power.MANUAL) {
            g.text(font, Component.translatable("gui.factoryascent.crank"), CRANK_X + 2, CRANK_Y + 2, 0xFFFFFFFF, false);
        }
        if (hasRecipeButton()) g.text(font, "?", recipeX() + 5, BTN_Y + 2, 0xFFFFFFFF, false);
    }

    private Component statusLine() {
        int st = data().status();
        String key = switch (st) {
            case AbstractMachineBlockEntity.STATUS_WORKING -> "working";
            case AbstractMachineBlockEntity.STATUS_NO_POWER -> "no_power";
            case AbstractMachineBlockEntity.STATUS_OUTPUT_FULL -> "output_full";
            case AbstractMachineBlockEntity.STATUS_TIER_TOO_LOW -> "tier_too_low";
            case AbstractMachineBlockEntity.STATUS_NO_FUEL -> "no_fuel";
            case AbstractMachineBlockEntity.STATUS_FULL -> "full";
            case AbstractMachineBlockEntity.STATUS_INCOMPLETE -> "incomplete";
            case AbstractMachineBlockEntity.STATUS_NEEDS_CRANK -> "needs_crank";
            case AbstractMachineBlockEntity.STATUS_LOADING -> "loading";
            default -> type().category() == MachineType.Category.MINER && data().progress() >= 1000 ? "finished" : "idle";
        };
        ChatFormatting color = switch (st) {
            case AbstractMachineBlockEntity.STATUS_WORKING -> ChatFormatting.DARK_GREEN;
            case AbstractMachineBlockEntity.STATUS_NO_POWER, AbstractMachineBlockEntity.STATUS_TIER_TOO_LOW,
                 AbstractMachineBlockEntity.STATUS_OUTPUT_FULL, AbstractMachineBlockEntity.STATUS_INCOMPLETE,
                 AbstractMachineBlockEntity.STATUS_NO_FUEL -> ChatFormatting.DARK_RED;
            default -> ChatFormatting.DARK_GRAY;
        };
        if (st == AbstractMachineBlockEntity.STATUS_INCOMPLETE) {
            return Component.translatable("status.factoryascent.incomplete", data().extraB()).withStyle(color);
        }
        return Component.translatable("status.factoryascent." + key).withStyle(color);
    }

    private String infoLine() {
        MachineData d = data();
        return switch (type()) {
            case SOLAR_PANEL -> Component.translatable("gui.factoryascent.sunlight", d.extraA()).getString();
            case GEOTHERMAL_GENERATOR -> Component.translatable("gui.factoryascent.lava", d.extraA()).getString();
            case MINER -> Component.translatable("gui.factoryascent.miner_info", d.extraA(), d.extraB()).getString();
            case AUTO_FARMER -> Component.translatable("gui.factoryascent.farm_info", d.extraA()).getString();
            default -> "";
        };
    }

    // ---------------------------------------------------------------- tooltips & input

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        MachineType type = type();
        if (type.usesEnergy() && isHovering(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, energyTooltip(), mouseX, mouseY);
        } else if (type.category() == MachineType.Category.PROCESSOR
                && isHovering(MachineLayout.arrowX(type), MachineLayout.ARROW_Y, 24, 16, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.factoryascent.progress", data().progress() / 10),
                    Component.translatable("gui.factoryascent.rate", format(data().itemRateX10())),
                    Component.translatable("gui.factoryascent.speed", data().speedPercent())), mouseX, mouseY);
        } else if (hasEjectButton() && isHovering(ejectX(), BTN_Y, BTN_W, BTN_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable(
                    data().autoEject() ? "gui.factoryascent.eject_on" : "gui.factoryascent.eject_off")), mouseX, mouseY);
        } else if (hasRecipeButton() && isHovering(recipeX(), BTN_Y, BTN_W, BTN_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.factoryascent.recipes")), mouseX, mouseY);
        } else if (type.power() == MachineType.Power.MANUAL && isHovering(CRANK_X, CRANK_Y, CRANK_W, CRANK_H, mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.factoryascent.crank_hint")), mouseX, mouseY);
        } else if (type.isMultiblock() && data().status() == AbstractMachineBlockEntity.STATUS_INCOMPLETE
                && mouseY - topPos > 72 && mouseY - topPos < 86) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("tooltip.factoryascent.multiblock." + type.id())), mouseX, mouseY);
        } else if (type.category() == MachineType.Category.PROCESSOR) {
            MachineSlots s = menu.machine().inventory().slots();
            for (int i = 0; i < s.inputs(); i++) {
                MachineLayout.Pos p = MachineLayout.input(type, i);
                ItemStack stack = menu.machine().inventory().stack(s.firstInput() + i);
                if (!stack.isEmpty() && !ClientRecipes.accepts(type, stack) && isHovering(p.x(), p.y(), 16, 16, mouseX, mouseY)) {
                    g.setComponentTooltipForNextFrame(font, List.of(
                            Component.translatable("gui.factoryascent.not_accepted").withStyle(ChatFormatting.RED),
                            Component.translatable("gui.factoryascent.see_recipes").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                }
            }
        }
    }

    private List<Component> energyTooltip() {
        MachineData d = data();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.factoryascent.energy", EnergyUtil.format(d.energy()), EnergyUtil.format(d.capacity())));
        switch (type().category()) {
            case GENERATOR -> lines.add(Component.translatable("gui.factoryascent.generating", d.energyRate()).withStyle(ChatFormatting.GREEN));
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
        if (event.button() == 0 && minecraft != null && minecraft.gameMode != null) {
            if (hasEjectButton() && isHovering(ejectX(), BTN_Y, BTN_W, BTN_H, event.x(), event.y())) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_TOGGLE_EJECT);
                return true;
            }
            if (type().power() == MachineType.Power.MANUAL && isHovering(CRANK_X, CRANK_Y, CRANK_W, CRANK_H, event.x(), event.y())) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_CRANK);
                return true;
            }
            if (hasRecipeButton() && isHovering(recipeX(), BTN_Y, BTN_W, BTN_H, event.x(), event.y())) {
                minecraft.gui.pushScreenLayer(new RecipeListScreen(type()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
