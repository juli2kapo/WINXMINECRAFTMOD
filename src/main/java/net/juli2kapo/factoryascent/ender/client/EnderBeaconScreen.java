package net.juli2kapo.factoryascent.ender.client;

import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.ender.ChamberMenus;
import net.juli2kapo.factoryascent.ender.EnderBeaconBlockEntity;
import net.juli2kapo.factoryascent.ender.EnderBeaconMenu;
import net.juli2kapo.factoryascent.ender.EnderContent;
import net.juli2kapo.factoryascent.ui.ScreenPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Ender Beacon: the chamber with its pearl slot, a name field (what linked Recall Charms show),
 * owner and pearl status, the server's recall rules, and how many charms in your inventory are
 * linked to this beacon.
 */
public class EnderBeaconScreen extends AbstractContainerScreen<EnderBeaconMenu> {
    private static final int FIELD_X = 50, FIELD_Y = 17, FIELD_W = 100;
    private static final int INFO_X = 50, INFO_Y = 36, INFO_W = 138, INFO_H = 46;

    private @Nullable EditBox name;
    private @Nullable Button save;
    private String typed;
    /** Last name sent, to show "saved" until the field changes again. */
    private @Nullable String sent;

    public EnderBeaconScreen(EnderBeaconMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ChamberMenus.WIDTH, ChamberMenus.HEIGHT);
        this.inventoryLabelX = ChamberMenus.INV_X;
        this.inventoryLabelY = ChamberMenus.PLAYER_INV_Y - 11;
        this.typed = menu.initialName();
    }

    @Override
    protected void init() {
        super.init();
        name = new EditBox(font, leftPos + FIELD_X, topPos + FIELD_Y, FIELD_W, 14, Component.translatable("gui.factoryascent.beacon.name"));
        name.setMaxLength(EnderBeaconBlockEntity.MAX_NAME);
        name.setValue(typed);
        name.setResponder(s -> typed = s);
        name.setHint(Component.translatable("block.factoryascent.ender_beacon").withStyle(ChatFormatting.DARK_GRAY));
        name.setEditable(menu.mayEdit());
        if (!menu.mayEdit()) name.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.beacon.not_owner")));
        addRenderableWidget(name);
        save = addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.beacon.rename"), b -> rename())
                .bounds(leftPos + FIELD_X + FIELD_W + 4, topPos + FIELD_Y - 1, imageWidth - 8 - (FIELD_X + FIELD_W + 4), 16).build());
        save.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.beacon.rename_tip")));
        save.active = menu.mayEdit();
    }

    private void rename() {
        if (!menu.mayEdit()) return;
        ClientPacketDistributor.sendToServer(new ScreenPayloads.BeaconRename(menu.pos(), typed));
        sent = typed.strip();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (name != null) name.setEditable(menu.mayEdit());
        if (save != null) save.active = menu.mayEdit();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) return super.keyPressed(event);
        if (name != null && name.isFocused()) {
            if (event.isConfirmation()) {
                rename();
                return true;
            }
            if (name.keyPressed(event) || name.canConsumeInput()) return true;
        }
        return super.keyPressed(event);
    }

    private long time() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    /** Recall Charms in the player's inventory linked to this beacon. */
    private int linkedCharms() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return 0;
        GlobalPos here = GlobalPos.of(minecraft.level.dimension(), menu.pos());
        Inventory inv = minecraft.player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(EnderContent.RECALL_CHARM.get()) && here.equals(stack.get(EnderContent.LINKED_BEACON.get()))) n++;
        }
        return n;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = leftPos, y = topPos;
        FactoryGui.panel(g, x, y, imageWidth, imageHeight, FactoryGui.ENDER);
        ChamberGui.chamber(g, x + 8, y + 15, 36, 68, menu.hasPearl(), true, time());
        FactoryGui.slot(g, x + ChamberMenus.PEARL_X, y + ChamberMenus.PEARL_Y);
        FactoryGui.display(g, x + INFO_X, y + INFO_Y, INFO_W, INFO_H);
        FactoryGui.playerInventory(g, x + ChamberMenus.INV_X - 8, y, ChamberMenus.PLAYER_INV_Y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, FactoryGui.TEXT, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, FactoryGui.TEXT, false);
        int tx = INFO_X + 4, w = INFO_W - 8, ly = INFO_Y + 4;
        boolean ready = menu.hasPearl();
        int statusColor = ready ? FactoryGui.GOOD : FactoryGui.WARN;
        FactoryGui.lamp(g, tx, ly, statusColor);
        g.text(font, FactoryGui.fit(font, Component.translatable(ready ? "gui.factoryascent.beacon.ready" : "gui.factoryascent.beacon.empty"),
                w - 10), tx + 10, ly, statusColor, false);
        ly += 11;
        String owner = menu.ownerName().isEmpty() ? "-" : menu.ownerName();
        g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.owner", owner), w), tx, ly, FactoryGui.DISPLAY_TEXT, false);
        ly += 11;
        g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.beacon.rules", menu.recallSeconds(),
                menu.cooldownSeconds()), w), tx, ly, FactoryGui.DISPLAY_MUTED, false);
        ly += 11;
        g.text(font, FactoryGui.fit(font, Component.translatable(menu.crossDimension()
                ? "gui.factoryascent.beacon.cross_yes" : "gui.factoryascent.beacon.cross_no"), w), tx, ly, FactoryGui.DISPLAY_MUTED, false);
        int linked = linkedCharms();
        Component hint = sent != null && sent.equals(typed.strip())
                ? Component.translatable("gui.factoryascent.beacon.renamed").withStyle(ChatFormatting.DARK_GREEN)
                : linked > 0 ? Component.translatable("gui.factoryascent.beacon.linked_charms", linked)
                : Component.translatable("gui.factoryascent.beacon.link_hint");
        g.text(font, FactoryGui.fit(font, hint, imageWidth - 16), 8, 86, FactoryGui.TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (!menu.hasPearl() && isHovering(ChamberMenus.PEARL_X, ChamberMenus.PEARL_Y, 16, 16, mouseX, mouseY)
                && menu.getCarried().isEmpty()) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.factoryascent.chamber.drop_pearl"),
                    Component.translatable("gui.factoryascent.beacon.pearl_used").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }
}
