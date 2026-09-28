package net.juli2kapo.factoryascent.ender.client;

import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.ender.ChamberMenus;
import net.juli2kapo.factoryascent.ender.EnderAnchorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Ender Anchor: the chamber with its pearl slot on the left, a map of the chunks it keeps
 * loaded in the middle (loaded area lit, its own chunk marked), owner/area/anchor-count read-outs
 * and an on/off switch on the right.
 */
public class EnderAnchorScreen extends AbstractContainerScreen<EnderAnchorMenu> {
    private static final int GRID_X = 50, GRID_Y = 17, GRID = 64;
    private static final int INFO_X = 120, INFO_Y = 16, INFO_W = 104, INFO_H = 52;

    private @Nullable Button toggle;

    public EnderAnchorScreen(EnderAnchorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ChamberMenus.WIDTH, ChamberMenus.HEIGHT);
        this.inventoryLabelX = ChamberMenus.INV_X;
        this.inventoryLabelY = ChamberMenus.PLAYER_INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(Component.empty(), b -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, EnderAnchorMenu.BUTTON_TOGGLE);
            }
        }).bounds(leftPos + INFO_X, topPos + INFO_Y + INFO_H + 4, INFO_W, 16).build());
        updateToggle();
    }

    private void updateToggle() {
        if (toggle == null) return;
        boolean on = menu.enabled();
        toggle.setMessage(Component.translatable(on ? "gui.factoryascent.anchor.turn_off" : "gui.factoryascent.anchor.turn_on")
                .withStyle(on ? ChatFormatting.WHITE : ChatFormatting.GREEN));
        toggle.active = menu.mayControl();
        toggle.setTooltip(Tooltip.create(Component.translatable(menu.mayControl()
                ? "gui.factoryascent.anchor.toggle_tip" : "gui.factoryascent.anchor.not_owner")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateToggle();
    }

    private long time() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    private int cells() {
        return 2 * Math.max(0, menu.radius()) + 3;
    }

    private int cellSize() {
        return Math.max(3, Math.min(14, (GRID - 2) / cells()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        FactoryGui.panel(g, x, y, imageWidth, imageHeight, FactoryGui.ENDER);
        // chamber + pearl slot
        ChamberGui.chamber(g, x + 8, y + 15, 36, 68, menu.running(), false, time());
        FactoryGui.slot(g, x + ChamberMenus.PEARL_X, y + ChamberMenus.PEARL_Y);
        // chunk map
        FactoryGui.display(g, x + GRID_X - 2, y + GRID_Y - 2, GRID + 4, GRID + 4);
        int n = cells(), s = cellSize(), r = menu.radius();
        int ox = x + GRID_X + (GRID - n * s) / 2, oy = y + GRID_Y + (GRID - n * s) / 2;
        boolean running = menu.running();
        long t = time();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int dx = i - (r + 1), dz = j - (r + 1);
                boolean inside = Math.abs(dx) <= r && Math.abs(dz) <= r;
                int color;
                if (!inside) color = 0xFF262A32;
                else if (running) {
                    float pulse = 0.85f + 0.15f * (float) Math.sin((t + (dx * 3 + dz * 5)) / 8.0);
                    color = scale(FactoryGui.ENDER, pulse);
                } else color = 0xFF4A4658;
                int cx = ox + i * s, cy = oy + j * s;
                g.fill(cx, cy, cx + s - 1, cy + s - 1, color);
                if (inside && running) g.fill(cx, cy, cx + s - 1, cy + 1, FactoryGui.brighter(color));
            }
        }
        // the anchor's own chunk
        int mx = ox + (r + 1) * s + s / 2, my = oy + (r + 1) * s + s / 2;
        g.fill(mx - 2, my - 2, mx + 2, my + 2, 0xFF000000);
        g.fill(mx - 1, my - 1, mx + 1, my + 1, running ? 0xFF3FF0C8 : 0xFF9A9A9A);
        // read-outs
        FactoryGui.display(g, x + INFO_X, y + INFO_Y, INFO_W, INFO_H);
        FactoryGui.playerInventory(g, x + ChamberMenus.INV_X - 8, y, ChamberMenus.PLAYER_INV_Y);
    }

    private static int scale(int argb, float f) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
        int gg = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, (int) ((argb & 0xFF) * f));
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, FactoryGui.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, FactoryGui.TEXT, false);
        int tx = INFO_X + 4, w = INFO_W - 8, ly = INFO_Y + 4;
        Component status;
        int statusColor;
        if (!menu.hasPearl()) {
            status = Component.translatable("gui.factoryascent.anchor.status_empty");
            statusColor = FactoryGui.WARN;
        } else if (!menu.enabled()) {
            status = Component.translatable("gui.factoryascent.anchor.status_off");
            statusColor = FactoryGui.BAD;
        } else {
            status = Component.translatable("gui.factoryascent.anchor.status_on");
            statusColor = FactoryGui.GOOD;
        }
        FactoryGui.lamp(g, tx, ly, statusColor);
        g.text(font, FactoryGui.fit(font, status, w - 10), tx + 10, ly, statusColor, false);
        ly += 12;
        String owner = menu.ownerName().isEmpty() ? "-" : menu.ownerName();
        g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.owner", owner), w), tx, ly, FactoryGui.DISPLAY_TEXT, false);
        ly += 12;
        int side = 2 * menu.radius() + 1;
        g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.anchor.area", side, side), w), tx, ly,
                FactoryGui.DISPLAY_TEXT, false);
        ly += 12;
        Component count = menu.anchorsAllowed() > 0
                ? Component.translatable("gui.factoryascent.anchor.count", menu.anchorsUsed(), menu.anchorsAllowed())
                : Component.translatable("gui.factoryascent.anchor.count_unlimited", menu.anchorsUsed());
        g.text(font, FactoryGui.fit(font, count, w), tx, ly,
                menu.anchorsAllowed() > 0 && menu.anchorsUsed() >= menu.anchorsAllowed() ? FactoryGui.WARN : FactoryGui.DISPLAY_MUTED, false);
        // hint under the chamber and map
        Component hint = !menu.hasPearl() ? Component.translatable("gui.factoryascent.anchor.hint_empty")
                : menu.running() ? Component.translatable("gui.factoryascent.anchor.hint_on", side * side)
                : Component.translatable("gui.factoryascent.anchor.hint_off");
        g.text(font, FactoryGui.fit(font, hint, imageWidth - 16), 8, 86, FactoryGui.TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (!menu.hasPearl() && isHovering(ChamberMenus.PEARL_X, ChamberMenus.PEARL_Y, 16, 16, mouseX, mouseY)
                && menu.getCarried().isEmpty()) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.factoryascent.chamber.drop_pearl"),
                    Component.translatable("gui.factoryascent.chamber.pearl_lost").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        int n = cells(), s = cellSize(), r = menu.radius();
        int ox = GRID_X + (GRID - n * s) / 2, oy = GRID_Y + (GRID - n * s) / 2;
        if (isHovering(ox, oy, n * s, n * s, mouseX, mouseY)) {
            int i = (mouseX - leftPos - ox) / s, j = (mouseY - topPos - oy) / s;
            int dx = i - (r + 1), dz = j - (r + 1);
            ChunkPos center = ChunkPos.containing(menu.pos());
            boolean inside = Math.abs(dx) <= r && Math.abs(dz) <= r;
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.factoryascent.anchor.chunk", center.x() + dx, center.z() + dz),
                    Component.translatable(!inside ? "gui.factoryascent.anchor.chunk_outside"
                            : menu.running() ? "gui.factoryascent.anchor.chunk_loaded" : "gui.factoryascent.anchor.chunk_idle")
                            .withStyle(inside && menu.running() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }
}
