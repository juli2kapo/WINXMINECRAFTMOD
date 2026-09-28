package net.juli2kapo.factoryascent.ui.client;

import java.util.List;
import net.juli2kapo.factoryascent.client.FactoryGui;
import net.juli2kapo.factoryascent.ui.ScreenPayloads.CableView;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Power cable: its network's shared buffer, a producers → cables → consumers diagram with counts,
 * smoothed energy in/out per tick, and the throughput limit (the slowest cable's rate).
 */
public class CableScreen extends NetScreen {
    private static final int W = 220, H = 162;

    private CableView view;

    public CableScreen(CableView view, Component title) {
        super(title);
        this.view = view;
    }

    @Override
    BlockPos pos() {
        return view.pos();
    }

    public void update(CableView view) {
        this.view = view;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left() + W - 60, top() + H - 22, 52, 14).build());
    }

    private static String fe(long v) {
        return EnergyUtil.format((int) Math.min(Integer.MAX_VALUE, v));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        FactoryGui.panel(g, x, y, W, H, FactoryGui.ELECTRIC);
        FactoryGui.energyBar(g, x + 8, y + 30, W - 16, 10, view.capacity() <= 0 ? 0 : view.energy() / (float) view.capacity());
        // flow diagram
        FactoryGui.display(g, x + 8, y + 46, W - 16, 44);
        int boxY = y + 58, boxW = 50, boxH = 28;
        int px = x + 16, cx = x + (W - boxW) / 2, mx = x + W - 16 - boxW;
        node(g, px, boxY, boxW, boxH, 0xFF3A5A2A);
        node(g, cx, boxY, boxW, boxH, 0xFF4A4A2A);
        node(g, mx, boxY, boxW, boxH, 0xFF5A3A2A);
        long t = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
        flow(g, px + boxW, cx, boxY + boxH / 2, view.in() > 0, t);
        flow(g, cx + boxW, mx, boxY + boxH / 2, view.out() > 0, t);
        FactoryGui.bolt(g, px + 4, boxY + 7, 0xFFFFD23F);
        // the cable: a short line with a joint
        g.fill(cx + 6, boxY + 13, cx + 20, boxY + 16, 0xFFB07030);
        g.fill(cx + 11, boxY + 11, cx + 15, boxY + 18, 0xFF6A6A6A);
        // a machine: a small grey box with a slot
        g.fill(mx + 4, boxY + 8, mx + 16, boxY + 20, 0xFF8A8A8A);
        g.fill(mx + 7, boxY + 11, mx + 13, boxY + 17, 0xFF3A3A3A);
    }

    private static void node(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, FactoryGui.brighter(color));
    }

    /** An arrow between two nodes; moving dots when energy flows. */
    private static void flow(GuiGraphicsExtractor g, int x0, int x1, int y, boolean active, long t) {
        int c = active ? 0xFFFFD23F : 0xFF4A4A4A;
        g.fill(x0 + 2, y - 1, x1 - 4, y + 1, c);
        for (int i = 0; i < 3; i++) g.fill(x1 - 4 - i, y - 3 + i, x1 - 3 - i, y + 3 - i, c);
        if (active && x1 - x0 > 10) {
            int span = x1 - x0 - 8;
            int dx = (int) ((t * 2) % span);
            g.fill(x0 + 2 + dx, y - 2, x0 + 4 + dx, y + 2, 0xFFFFFFFF);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x = left(), y = top();
        g.text(font, title, x + 8, y + 8, FactoryGui.TEXT, false);
        Component stored = Component.translatable("gui.factoryascent.energy", fe(view.energy()), fe(view.capacity()));
        g.text(font, Component.translatable("gui.factoryascent.cable.buffer"), x + 8, y + 20, FactoryGui.TEXT, false);
        g.text(font, stored, x + W - 8 - font.width(stored), y + 20, FactoryGui.TEXT, false);
        int boxY = y + 58, boxW = 50;
        int px = x + 16, cx = x + (W - boxW) / 2, mx = x + W - 16 - boxW;
        String producers = Integer.toString(view.producers());
        g.text(font, producers, px + boxW - 6 - font.width(producers), boxY + 10, 0xFFFFFFFF, true);
        String cables = Integer.toString(view.cables());
        g.text(font, cables, cx + boxW - 6 - font.width(cables), boxY + 10, 0xFFFFFFFF, true);
        String consumers = Integer.toString(view.consumers());
        g.text(font, consumers, mx + boxW - 6 - font.width(consumers), boxY + 10, 0xFFFFFFFF, true);
        small(g, Component.translatable("gui.factoryascent.cable.producers"), px + boxW / 2, boxY - 9);
        small(g, Component.translatable("gui.factoryascent.cable.cables"), cx + boxW / 2, boxY - 9);
        small(g, Component.translatable("gui.factoryascent.cable.consumers"), mx + boxW / 2, boxY - 9);
        // numbers
        int ly = y + 96, right = x + W - 8;
        FactoryGui.row(g, font, Component.translatable("gui.factoryascent.cable.in"),
                Component.literal(fe(view.in()) + " FE/t").withStyle(ChatFormatting.DARK_GREEN), x + 8, right, ly, FactoryGui.TEXT, FactoryGui.TEXT);
        FactoryGui.row(g, font, Component.translatable("gui.factoryascent.cable.out"),
                Component.literal(fe(view.out()) + " FE/t").withStyle(ChatFormatting.GOLD), x + 8, right, ly + 11, FactoryGui.TEXT, FactoryGui.TEXT);
        FactoryGui.row(g, font, Component.translatable("gui.factoryascent.cable.limit"),
                Component.literal(fe(view.rate()) + " FE/t"), x + 8, right, ly + 22, FactoryGui.TEXT, FactoryGui.TEXT);
        Component extra = view.storage() > 0
                ? Component.translatable("gui.factoryascent.cable.storage", view.storage())
                : Component.translatable("gui.factoryascent.cable.this_cable", fe(view.cableRate()));
        g.text(font, FactoryGui.fit(font, extra, W - 76), x + 8, y + H - 19, FactoryGui.MUTED, false);
        super.extractRenderState(g, mouseX, mouseY, partial);
        if (FactoryGui.inside(mouseX, mouseY, x + 8, y + 30, W - 16, 10)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.factoryascent.cable.buffer_tip")
                    .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        } else if (FactoryGui.inside(mouseX, mouseY, x + 8, y + 118, W - 16, 9)) {
            g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("tooltip.factoryascent.cable_bottleneck")
                    .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    private void small(GuiGraphicsExtractor g, Component text, int centerX, int y) {
        g.text(font, FactoryGui.fit(font, text, 60), centerX - Math.min(60, font.width(text)) / 2, y, FactoryGui.DISPLAY_MUTED, false);
    }
}
