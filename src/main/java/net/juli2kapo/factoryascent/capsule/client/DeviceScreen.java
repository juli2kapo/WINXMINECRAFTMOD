package net.juli2kapo.factoryascent.capsule.client;

import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.capsule.DeviceMenu;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jspecify.annotations.Nullable;

/**
 * The machine-style screen of a capsule or phone device: the bevelled panel with an accent
 * stripe, slot frames, the player inventory, and flat buttons that send menu button ids.
 */
public abstract class DeviceScreen<M extends DeviceMenu<?>> extends AbstractContainerScreen<M> {
    /** A flat button (coordinates relative to the panel). */
    public record Btn(int x, int y, int w, int h, Component label, int id, @Nullable Component tip) {}

    private final int accent;
    private final int invY;
    private final int invX;
    protected final List<Btn> buttons = new ArrayList<>();

    protected DeviceScreen(M menu, Inventory inventory, Component title, int width, int height, int invY, int accent) {
        this(menu, inventory, title, width, height, 8, invY, accent);
    }

    protected DeviceScreen(M menu, Inventory inventory, Component title, int width, int height, int invX, int invY, int accent) {
        super(menu, inventory, title, width, height);
        this.accent = accent;
        this.invY = invY;
        this.invX = invX;
        this.inventoryLabelX = invX;
        this.inventoryLabelY = invY - 11;
    }

    /** Rebuilt every frame: which buttons exist and what they say. */
    protected abstract void layoutButtons();

    protected boolean enabled(Btn b) {
        return true;
    }

    /** Draws the device-specific parts of the background (panel-relative origin at x, y). */
    protected abstract void background(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY);

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        FactoryGui.panel(g, x, y, imageWidth, imageHeight, accent);
        for (int i = 0; i < menu.slots.size() - 36; i++) {
            var slot = menu.slots.get(i);
            FactoryGui.slot(g, x + slot.x, y + slot.y);
        }
        FactoryGui.playerInventory(g, x + invX - 8, y, invY);
        background(g, x, y, mouseX, mouseY);
        buttons.clear();
        layoutButtons();
        for (Btn b : buttons) {
            boolean hover = FactoryGui.inside(mouseX - x, mouseY - y, b.x(), b.y(), b.w(), b.h());
            boolean on = enabled(b);
            FactoryGui.button(g, x + b.x(), y + b.y(), b.w(), b.h(), hover && on, false, accent);
            int tw = font.width(b.label());
            g.text(font, b.label(), x + b.x() + (b.w() - tw) / 2, y + b.y() + (b.h() - 8) / 2, on ? FactoryGui.TEXT : FactoryGui.MUTED, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        for (Btn b : buttons) {
            if (b.tip() != null && FactoryGui.inside(mouseX - leftPos, mouseY - topPos, b.x(), b.y(), b.w(), b.h())) {
                g.setComponentTooltipForNextFrame(font, List.of(b.tip()), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (Btn b : buttons) {
            if (enabled(b) && FactoryGui.inside(event.x() - leftPos, event.y() - topPos, b.x(), b.y(), b.w(), b.h())) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, b.id());
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, FactoryGui.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, FactoryGui.TEXT, false);
        labels(g, mouseX, mouseY);
    }

    /** Device-specific text (panel-relative coordinates). */
    protected abstract void labels(GuiGraphicsExtractor g, int mouseX, int mouseY);
}
