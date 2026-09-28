package net.juli2kapo.factoryascent.orbital.client;

import net.juli2kapo.factoryascent.orbital.OrbitalPayloads;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.RadarAction;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.RadarView;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.Row;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * An Orbital Radar's screen: every satellite over the dimension. Own satellites are listed by name
 * with a Target button right away (a team may shoot down its own satellites); foreign ones are
 * unidentified contacts with a Track button until the radar locks them, then a Target button
 * picks the one Anti-Satellite missiles get programmed with. The server sends the
 * list ({@link RadarView}); the screen asks for a fresh one twice a second.
 */
public class RadarScreen extends Screen {
    private static final int W = 276, H = 214;
    private static final int ROW_H = 14, ROWS = 9;
    private static final int LIST_Y = 44;

    private RadarView view;
    private int scroll;
    private int ticks;

    public RadarScreen(RadarView view) {
        super(Component.translatable("block.factoryascent.orbital_radar"));
        this.view = view;
    }

    public BlockPos pos() {
        return view.pos();
    }

    public void update(RadarView view) {
        boolean relayout = !layout(view).equals(layout(this.view));
        this.view = view;
        scroll = Math.min(scroll, maxScroll());
        if (relayout) rebuildWidgets();
    }

    /** What the buttons depend on: which contact is on which row, in which state. */
    private static java.util.List<String> layout(RadarView view) {
        return view.contacts().stream().map(r -> r.id() + ":" + r.state()).toList();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int maxScroll() {
        return Math.max(0, view.contacts().size() - ROWS);
    }

    private void send(int action, java.util.UUID id) {
        ClientPacketDistributor.sendToServer(new RadarAction(view.pos(), action, id));
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        for (int i = 0; i < ROWS && scroll + i < view.contacts().size(); i++) {
            Row row = view.contacts().get(scroll + i);
            int by = y + LIST_Y + 1 + i * ROW_H;
            switch (row.state()) {
                case OrbitalPayloads.CONTACT_UNKNOWN -> {
                    Button b = Button.builder(Component.translatable("gui.factoryascent.radar.track"),
                            btn -> send(RadarAction.TRACK, row.id())).bounds(x + W - 62, by, 54, 12).build();
                    b.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.radar.track_tip")));
                    addRenderableWidget(b);
                }
                case OrbitalPayloads.CONTACT_TRACKING -> addRenderableWidget(Button.builder(
                        Component.translatable("gui.factoryascent.radar.stop"),
                        btn -> send(RadarAction.STOP, row.id())).bounds(x + W - 62, by, 54, 12).build());
                case OrbitalPayloads.CONTACT_OWN -> {
                    Button b = Button.builder(Component.translatable("gui.factoryascent.radar.target"),
                            btn -> send(RadarAction.DESIGNATE, row.id())).bounds(x + W - 62, by, 54, 12).build();
                    b.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.radar.target_own_tip")));
                    addRenderableWidget(b);
                }
                case OrbitalPayloads.CONTACT_LOCKED -> {
                    Button b = Button.builder(Component.translatable("gui.factoryascent.radar.target"),
                            btn -> send(RadarAction.DESIGNATE, row.id())).bounds(x + W - 62, by, 54, 12).build();
                    b.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.radar.target_tip")));
                    addRenderableWidget(b);
                }
                default -> {}
            }
        }
    }

    @Override
    public void tick() {
        if (++ticks % 10 == 0) send(RadarAction.REFRESH, new java.util.UUID(0, 0));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        OrbitalGui.panel(g, x, y, W, H);
        OrbitalGui.inset(g, x + 6, y + LIST_Y - 1, W - 12, ROWS * ROW_H + 2);
        OrbitalGui.bar(g, x + W - 70, y + 9, 62, 7, view.capacity() == 0 ? 0 : view.energy() / (float) view.capacity(), 0xFFE04040);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x = left(), y = top();
        g.text(font, title, x + 9, y + 9, OrbitalGui.ACCENT, false);
        g.text(font, OrbitalGui.fit(font, view.header(), W - 18), x + 9, y + 21, OrbitalGui.MUTED, false);
        g.text(font, Component.translatable("gui.factoryascent.radar.contacts", view.contacts().size()), x + 9, y + LIST_Y - 11,
                OrbitalGui.TEXT, false);
        if (view.contacts().isEmpty()) {
            g.text(font, Component.translatable("gui.factoryascent.radar.none"), x + 12, y + LIST_Y + 4, OrbitalGui.MUTED, false);
        }
        for (int i = 0; i < ROWS && scroll + i < view.contacts().size(); i++) {
            Row row = view.contacts().get(scroll + i);
            int ry = y + LIST_Y + 3 + i * ROW_H;
            boolean hasButton = row.state() != OrbitalPayloads.CONTACT_TARGET;
            int textW = W - (hasButton ? 76 : 24) - (row.state() == OrbitalPayloads.CONTACT_TRACKING ? 44 : 0)
                    - (row.state() == OrbitalPayloads.CONTACT_TARGET ? 44 : 0);
            g.text(font, OrbitalGui.fit(font, row.line(), textW), x + 10, ry, OrbitalGui.TEXT, false);
            if (row.state() == OrbitalPayloads.CONTACT_TRACKING) {
                OrbitalGui.bar(g, x + W - 106, ry, 40, 8, row.progress() / 100f, 0xFFE0C040);
            } else if (row.state() == OrbitalPayloads.CONTACT_TARGET) {
                Component t = Component.translatable("gui.factoryascent.radar.targeted").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                g.text(font, t, x + W - 10 - font.width(t), ry, OrbitalGui.TEXT, false);
            }
        }
        if (maxScroll() > 0) {
            String more = (scroll + 1) + "-" + Math.min(view.contacts().size(), scroll + ROWS) + "/" + view.contacts().size();
            g.text(font, more, x + W - 8 - font.width(more), y + LIST_Y - 11, OrbitalGui.MUTED, false);
        }
        int hintY = y + LIST_Y + ROWS * ROW_H + 5;
        g.textWithWordWrap(font, Component.translatable("gui.factoryascent.radar.hint"), x + 9, hintY, W - 18, OrbitalGui.MUTED, false);
        g.text(font, OrbitalGui.fit(font, view.message(), W - 18), x + 9, y + H - 12, OrbitalGui.TEXT, false);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int next = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
