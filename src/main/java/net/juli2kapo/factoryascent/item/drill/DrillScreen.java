package net.juli2kapo.factoryascent.item.drill;

import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.item.ElectricDrillItem;
import net.juli2kapo.factoryascent.ui.ScreenPayloads;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Electric Drill (sneak-use in the air): its charge and three cards, one per mining mode, each
 * with a little picture of what it digs. Clicking a card sends a {@link ScreenPayloads.DrillAction}.
 */
public class DrillScreen extends Screen {
    private static final int W = 222, H = 180;
    private static final int CARD_Y = 38, CARD_W = 66, CARD_H = 104, CARD_GAP = 6;

    private final InteractionHand hand;
    /** The mode just clicked, shown until the item's component catches up. */
    private @Nullable DrillMode pending;

    public DrillScreen(InteractionHand hand) {
        super(Component.translatable("item.factoryascent.electric_drill"));
        this.hand = hand;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private ItemStack drill() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getItemInHand(hand) : ItemStack.EMPTY;
    }

    private DrillMode shownMode() {
        DrillMode actual = ElectricDrillItem.mode(drill());
        if (pending == actual) pending = null;
        return pending != null ? pending : actual;
    }

    private int cardX(int i) {
        return left() + (W - 3 * CARD_W - 2 * CARD_GAP) / 2 + i * (CARD_W + CARD_GAP);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + W - 68, top() + H - 24, 60, 16).build());
    }

    @Override
    public void tick() {
        if (!(drill().getItem() instanceof ElectricDrillItem)) onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (DrillMode mode : DrillMode.values()) {
                if (FactoryGui.inside(event.x(), event.y(), cardX(mode.ordinal()), top() + CARD_Y, CARD_W, CARD_H)) {
                    if (mode != shownMode()) {
                        pending = mode;
                        ClientPacketDistributor.sendToServer(new ScreenPayloads.DrillAction(hand.ordinal(), mode.ordinal()));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        FactoryGui.panel(g, x, y, W, H, FactoryGui.ELECTRIC);
        ItemStack drill = drill();
        FactoryGui.energyBar(g, x + 30, y + 22, W - 38, 9, ElectricDrillItem.energy(drill) / (float) ElectricDrillItem.CAPACITY);
        DrillMode selected = shownMode();
        for (DrillMode mode : DrillMode.values()) {
            int cx = cardX(mode.ordinal()), cy = y + CARD_Y;
            boolean hover = FactoryGui.inside(mouseX, mouseY, cx, cy, CARD_W, CARD_H);
            boolean sel = mode == selected;
            g.fill(cx, cy, cx + CARD_W, cy + CARD_H, FactoryGui.OUTLINE);
            g.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + CARD_H - 1, sel ? 0xFF2B2A1C : hover ? 0xFF2A2F38 : FactoryGui.DISPLAY);
            if (sel) {
                g.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + 3, FactoryGui.ELECTRIC);
                g.fill(cx + 1, cy + CARD_H - 3, cx + CARD_W - 1, cy + CARD_H - 1, FactoryGui.ELECTRIC);
            }
            picture(g, mode, cx + CARD_W / 2, cy + 26, sel);
        }
    }

    /** A 5x5 block grid showing which blocks the mode digs (the one you mine in the middle). */
    private static void picture(GuiGraphicsExtractor g, DrillMode mode, int centerX, int centerY, boolean lit) {
        int s = 7, n = 5, ox = centerX - n * s / 2, oy = centerY - n * s / 2;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int dx = i - 2, dy = j - 2;
                boolean dug = switch (mode) {
                    case SINGLE -> dx == 0 && dy == 0;
                    case AREA -> Math.abs(dx) <= 1 && Math.abs(dy) <= 1;
                    case VEIN -> (dx == 0 && dy == 0) || (dx == 1 && dy == 0) || (dx == 1 && dy == -1) || (dx == 2 && dy == -1)
                            || (dx == -1 && dy == 1) || (dx == 0 && dy == 1) || (dx == -1 && dy == 2);
                };
                boolean ore = mode == DrillMode.VEIN && dug;
                int color = dug ? (ore ? (lit ? 0xFF6FD08A : 0xFF4E8A5E) : (lit ? 0xFFE0B040 : 0xFF9A8040)) : 0xFF5A5A5A;
                if (!dug && mode == DrillMode.VEIN && (i + j) % 3 == 0) color = 0xFF626262;
                int px = ox + i * s, py = oy + j * s;
                g.fill(px, py, px + s - 1, py + s - 1, color);
                if (dx == 0 && dy == 0) g.fill(px + 2, py + 2, px + s - 3, py + s - 3, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x = left(), y = top();
        ItemStack drill = drill();
        g.text(font, title, x + 8, y + 8, FactoryGui.TEXT, false);
        g.item(drill, x + 8, y + 18);
        int energy = ElectricDrillItem.energy(drill);
        Component charge = Component.translatable("gui.factoryascent.drill.charge", EnergyUtil.format(energy),
                EnergyUtil.format(ElectricDrillItem.CAPACITY), energy / ElectricDrillItem.COST_PER_BLOCK);
        int chargeW = Math.min(font.width(charge), W - 24 - font.width(title));
        g.text(font, FactoryGui.fit(font, charge, chargeW), x + W - 8 - chargeW, y + 8, FactoryGui.MUTED, false);
        DrillMode selected = shownMode();
        for (DrillMode mode : DrillMode.values()) {
            int cx = cardX(mode.ordinal()), cy = y + CARD_Y;
            Component name = mode.displayName();
            g.text(font, FactoryGui.fit(font, name, CARD_W - 6), cx + (CARD_W - Math.min(CARD_W - 6, font.width(name))) / 2, cy + 48,
                    0xFFFFFFFF, false);
            g.textWithWordWrap(font, Component.translatable("gui.factoryascent.drill.desc." + mode.getSerializedName(), DrillMining.MAX_VEIN),
                    cx + 4, cy + 60, CARD_W - 8, mode == selected ? FactoryGui.DISPLAY_TEXT : FactoryGui.DISPLAY_MUTED, false);
        }
        var tip = font.split(Component.translatable("gui.factoryascent.drill.tip"), W - 84);
        for (int i = 0; i < Math.min(2, tip.size()); i++) {
            g.text(font, tip.get(i), x + 8, y + H - (tip.size() > 1 ? 26 : 20) + i * 10, FactoryGui.MUTED, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (FactoryGui.inside(mouseX, mouseY, x + 30, y + 22, W - 38, 9)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.factoryascent.drill.charge_tip",
                    ElectricDrillItem.COST_PER_BLOCK).withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
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
