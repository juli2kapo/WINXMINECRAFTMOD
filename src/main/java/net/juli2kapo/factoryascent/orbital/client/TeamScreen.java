package net.juli2kapo.factoryascent.orbital.client;

import java.util.UUID;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.Row;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.TeamAction;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.TeamView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The Team screen: your team and its members, pending invites (join), a name field with Create
 * and Invite, Leave, and your team's satellites with a two-click Deorbit. Everything it shows
 * comes from the server ({@link TeamView}); every button sends a {@link TeamAction} and the server
 * answers with a fresh view.
 */
public class TeamScreen extends Screen {
    private static final int W = 276, H = 232;
    private static final int ROW_H = 14, ROWS = 6;
    private static final int LIST_Y = 118;

    private TeamView view;
    private @Nullable EditBox name;
    private String typed = "";
    private int scroll;
    /** Satellite whose Deorbit button was clicked once (the second click confirms). */
    private @Nullable UUID confirming;

    public TeamScreen(TeamView view) {
        super(Component.translatable("gui.factoryascent.team.title"));
        this.view = view;
    }

    /** A fresh view from the server: rebuild the buttons around it. */
    public void update(TeamView view) {
        this.view = view;
        this.confirming = null;
        scroll = Math.min(scroll, maxScroll());
        rebuildWidgets();
    }

    private boolean solo() {
        return view.teamName().isEmpty();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int maxScroll() {
        return Math.max(0, view.satellites().size() - ROWS);
    }

    private static void send(int action, String arg) {
        ClientPacketDistributor.sendToServer(new TeamAction(action, arg));
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        if (!solo()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.team.leave"),
                    b -> send(TeamAction.LEAVE, "")).bounds(x + W - 68, y + 9, 60, 14).build());
        }
        // pending invites
        int ix = x + 60;
        for (String team : view.invites().stream().limit(3).toList()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.team.join", team),
                    b -> send(TeamAction.JOIN, team)).bounds(ix, y + 48, 68, 14).build());
            ix += 70;
        }
        // name field + create / invite
        name = new EditBox(font, x + 9, y + 67, 128, 14, Component.translatable("gui.factoryascent.team.name"));
        name.setMaxLength(24);
        name.setValue(typed);
        name.setResponder(s -> typed = s);
        name.setHint(Component.translatable(solo() ? "gui.factoryascent.team.name_hint" : "gui.factoryascent.team.player_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(name);
        Button create = addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.team.create"),
                b -> send(TeamAction.CREATE, typed)).bounds(x + 142, y + 67, 62, 14).build());
        create.active = solo();
        create.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.team.create_tip")));
        Button invite = addRenderableWidget(Button.builder(Component.translatable("gui.factoryascent.team.invite"),
                b -> send(TeamAction.INVITE, typed)).bounds(x + 206, y + 67, 62, 14).build());
        invite.active = !solo();
        invite.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.team.invite_tip")));
        // online players: click to put the name in the field
        int cx = x + 60;
        for (String player : view.candidates()) {
            int w = Math.min(70, font.width(player) + 10);
            if (cx + w > x + W - 8) break;
            addRenderableWidget(Button.builder(Component.literal(player), b -> {
                typed = player;
                if (name != null) name.setValue(player);
            }).bounds(cx, y + 86, w, 12).build());
            cx += w + 2;
        }
        // satellites
        for (int i = 0; i < ROWS && scroll + i < view.satellites().size(); i++) {
            Row row = view.satellites().get(scroll + i);
            boolean asking = row.id().equals(confirming);
            Button deorbit = Button.builder(Component.translatable(asking ? "gui.factoryascent.team.deorbit_confirm"
                            : "gui.factoryascent.team.deorbit").withStyle(asking ? ChatFormatting.RED : ChatFormatting.WHITE), b -> {
                if (row.id().equals(confirming)) {
                    send(TeamAction.DEORBIT, row.id().toString());
                } else {
                    confirming = row.id();
                    rebuildWidgets();
                }
            }).bounds(x + W - 66, y + LIST_Y + 1 + i * ROW_H, 58, 12).build();
            deorbit.setTooltip(Tooltip.create(Component.translatable("gui.factoryascent.team.deorbit_tip")));
            addRenderableWidget(deorbit);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        int x = left(), y = top();
        OrbitalGui.panel(g, x, y, W, H);
        OrbitalGui.inset(g, x + 6, y + LIST_Y - 1, W - 12, ROWS * ROW_H + 2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        int x = left(), y = top();
        g.text(font, title, x + 9, y + 9, OrbitalGui.ACCENT, false);
        Component team = solo() ? Component.translatable("gui.factoryascent.team.solo").withStyle(ChatFormatting.GRAY)
                : Component.translatable("gui.factoryascent.team.team", view.teamName()).withStyle(ChatFormatting.GOLD);
        g.text(font, OrbitalGui.fit(font, team, W - 90), x + 9, y + 22, OrbitalGui.TEXT, false);
        Component members = Component.translatable("gui.factoryascent.team.members", String.join(", ", view.members()));
        g.text(font, OrbitalGui.fit(font, members, W - 18), x + 9, y + 34, OrbitalGui.MUTED, false);
        g.text(font, Component.translatable("gui.factoryascent.team.invites"), x + 9, y + 51, OrbitalGui.TEXT, false);
        if (view.invites().isEmpty()) {
            g.text(font, Component.translatable("gui.factoryascent.team.no_invites"), x + 60, y + 51, OrbitalGui.MUTED, false);
        }
        g.text(font, Component.translatable("gui.factoryascent.team.online"), x + 9, y + 88, OrbitalGui.TEXT, false);
        if (view.candidates().isEmpty()) {
            g.text(font, Component.translatable("gui.factoryascent.team.nobody"), x + 60, y + 88, OrbitalGui.MUTED, false);
        }
        g.text(font, Component.translatable("gui.factoryascent.team.satellites", view.satellites().size()),
                x + 9, y + LIST_Y - 12, OrbitalGui.TEXT, false);
        if (view.satellites().isEmpty()) {
            g.text(font, Component.translatable("gui.factoryascent.team.no_satellites"), x + 12, y + LIST_Y + 4, OrbitalGui.MUTED, false);
        }
        for (int i = 0; i < ROWS && scroll + i < view.satellites().size(); i++) {
            Row row = view.satellites().get(scroll + i);
            g.text(font, OrbitalGui.fit(font, row.line(), W - 84), x + 10, y + LIST_Y + 3 + i * ROW_H, OrbitalGui.TEXT, false);
        }
        if (maxScroll() > 0) {
            String more = (scroll + 1) + "-" + Math.min(view.satellites().size(), scroll + ROWS) + "/" + view.satellites().size();
            g.text(font, more, x + W - 8 - font.width(more), y + LIST_Y - 12, OrbitalGui.MUTED, false);
        }
        g.text(font, OrbitalGui.fit(font, view.message(), W - 18), x + 9, y + H - 14, OrbitalGui.TEXT, false);
        super.extractRenderState(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int next = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
        if (next != scroll) {
            scroll = next;
            confirming = null;
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
