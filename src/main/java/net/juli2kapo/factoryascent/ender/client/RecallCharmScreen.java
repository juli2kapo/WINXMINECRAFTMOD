package net.juli2kapo.factoryascent.ender.client;

import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.ender.EnderContent;
import net.juli2kapo.factoryascent.ui.ScreenPayloads;
import net.juli2kapo.factoryascent.ui.ScreenPayloads.CharmAction;
import net.juli2kapo.factoryascent.ui.ScreenPayloads.CharmView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Recall Charm (sneak-use in the air): the linked Ender Beacon (name, position, dimension,
 * whether its pearl is ready), the charm's cooldown, the recall rules, and Unlink.
 */
public class RecallCharmScreen extends Screen {
    private static final int W = 200, H = 150;
    private static final int INFO_Y = 20, INFO_H = 58;

    private CharmView view;
    private boolean confirmUnlink;

    public RecallCharmScreen(CharmView view) {
        super(Component.translatable("item.factoryascent.recall_charm"));
        this.view = view;
    }

    public void update(CharmView view) {
        boolean relayout = view.linked() != this.view.linked();
        this.view = view;
        if (relayout) {
            confirmUnlink = false;
            rebuildWidgets();
        }
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private InteractionHand hand() {
        InteractionHand hand = ScreenPayloads.hand(view.hand());
        return hand == null ? InteractionHand.MAIN_HAND : hand;
    }

    private ItemStack charm() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getItemInHand(hand()) : ItemStack.EMPTY;
    }

    private void send(int action) {
        ClientPacketDistributor.sendToServer(new CharmAction(view.hand(), action));
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        int by = y + H - 24;
        if (view.linked()) {
            Button unlink = addRenderableWidget(Button.builder(Component.translatable(confirmUnlink
                    ? "gui.factoryascent.charm.unlink_confirm" : "gui.factoryascent.charm.unlink")
                    .withStyle(confirmUnlink ? ChatFormatting.RED : ChatFormatting.WHITE), b -> {
                if (confirmUnlink) {
                    send(CharmAction.UNLINK);
                    confirmUnlink = false;
                } else {
                    confirmUnlink = true;
                }
                rebuildWidgets();
            }).bounds(x + 8, by, 60, 16).build());
            unlink.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.charm.unlink_tip")));
            addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.refresh"), b -> send(CharmAction.REFRESH))
                    .bounds(x + 72, by, 60, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + W - 68, by, 60, 16).build());
    }

    @Override
    public void tick() {
        // The charm left the hand (dropped, swapped): nothing to show any more.
        if (!charm().is(EnderContent.RECALL_CHARM.get())) onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        FactoryGui.panel(g, x, y, W, H, FactoryGui.ENDER);
        FactoryGui.display(g, x + 8, y + INFO_Y, W - 16, INFO_H);
        float cd = cooldown(partial);
        FactoryGui.bar(g, x + 8, y + INFO_Y + INFO_H + 14, W - 16, 7, cd > 0 ? cd : 1f, cd > 0 ? 0xFFB070E0 : 0xFF4CB050);
    }

    private float cooldown(float partial) {
        ItemStack stack = charm();
        return minecraft == null || minecraft.player == null || stack.isEmpty() ? 0
                : minecraft.player.getCooldowns().getCooldownPercent(stack, partial);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x = left(), y = top();
        g.item(new ItemStack(EnderContent.RECALL_CHARM.get()), x + W - 24, y + 1);
        g.text(font, title, x + 8, y + 7, FactoryGui.TEXT, false);
        int tx = x + 12, w = W - 24, ly = y + INFO_Y + 4;
        if (!view.linked()) {
            g.text(font, Component.translatable("gui.factoryascent.charm.not_linked"), tx, ly, FactoryGui.WARN, false);
            g.textWithWordWrap(font, Component.translatable("gui.factoryascent.charm.how_to_link"), tx, ly + 14, w,
                    FactoryGui.DISPLAY_MUTED, false);
        } else {
            Component name = view.name().isEmpty() ? Component.translatable("block.factoryascent.ender_beacon") : Component.literal(view.name());
            g.text(font, FactoryGui.fit(font, name, w), tx, ly, 0xFFD9B8FF, false);
            ly += 12;
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.charm.position",
                    view.pos().getX(), view.pos().getY(), view.pos().getZ()), w), tx, ly, FactoryGui.DISPLAY_TEXT, false);
            ly += 11;
            g.text(font, FactoryGui.fit(font, Component.translatable("gui.factoryascent.charm.dimension", view.dimension()), w), tx, ly,
                    FactoryGui.DISPLAY_MUTED, false);
            ly += 12;
            Component status;
            int color;
            if (!view.sameDimension() && !view.crossDimension()) {
                status = Component.translatable("gui.factoryascent.charm.other_dimension");
                color = FactoryGui.BAD;
            } else {
                switch (view.state()) {
                    case ScreenPayloads.BEACON_READY -> {
                        status = Component.translatable("gui.factoryascent.charm.ready");
                        color = FactoryGui.GOOD;
                    }
                    case ScreenPayloads.BEACON_NO_PEARL -> {
                        status = Component.translatable("gui.factoryascent.charm.no_pearl");
                        color = FactoryGui.WARN;
                    }
                    case ScreenPayloads.BEACON_GONE -> {
                        status = Component.translatable("gui.factoryascent.charm.gone");
                        color = FactoryGui.BAD;
                    }
                    default -> {
                        status = Component.translatable("gui.factoryascent.charm.unknown");
                        color = FactoryGui.DISPLAY_MUTED;
                    }
                }
            }
            FactoryGui.lamp(g, tx, ly, color);
            g.text(font, FactoryGui.fit(font, status, w - 10), tx + 10, ly, color, false);
        }
        // cooldown
        float cd = cooldown(partial);
        int barY = y + INFO_Y + INFO_H + 4;
        Component cdText = cd > 0
                ? Component.translatable("gui.factoryascent.charm.cooldown", Math.max(1, Math.round(cd * view.cooldownSeconds())))
                : Component.translatable("gui.factoryascent.charm.no_cooldown");
        g.text(font, cdText, x + 8, barY, FactoryGui.TEXT, false);
        var rules = font.split(Component.translatable("gui.factoryascent.charm.rules", view.recallSeconds(), view.cooldownSeconds()), W - 16);
        for (int i = 0; i < Math.min(2, rules.size()); i++) g.text(font, rules.get(i), x + 8, barY + 20 + i * 10, FactoryGui.MUTED, false);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
}
