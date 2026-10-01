package net.juli2kapo.factoryascent.phone.client.apps;

import java.util.List;
import net.juli2kapo.factoryascent.phone.client.PhoneAppView;
import net.juli2kapo.factoryascent.phone.client.PhoneClient;
import net.juli2kapo.factoryascent.phone.client.PhoneUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** Team app: members (online dots), the team chat with bubbles, a message field and the Team screen button. */
public final class TeamView extends PhoneAppView {
    private static final int CHAT_TOP = 26, CHAT_BOTTOM = 148, BUBBLE_W = 96;
    private static final int FX = 4, FY = 152, FW = W - 38, FH = 16, SBX = W - 32, SBW = 28;
    private static final int TBX = W - 50, TBY = 3, TBW = 46, TBH = 11;
    private static final int MAX = 120;

    private final StringBuilder text = new StringBuilder();
    /** The field has focus from the start, like a messenger: type and press Enter. */
    private boolean focused = true;
    private int scroll;
    private int contentHeight;

    public TeamView(String app) {
        super(app);
    }

    @Override
    protected void dataChanged() {
        scroll = 0; // jump to the newest message
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, float partial) {
        Font font = PhoneUi.font();
        String team = data.getStringOr("team", "");
        Component title = team.isEmpty() ? Component.translatable("gui.factoryascent.phone.team.solo")
                : Component.translatable("gui.factoryascent.phone.team.name", team);
        PhoneUi.text(g, title, 4, 3, W - 58, team.isEmpty() ? PhoneUi.MUTED : 0xFFFFD27A);
        PhoneUi.button(g, TBX, TBY, TBW, TBH, Component.translatable("gui.factoryascent.phone.team.screen"), 0xFF3C6FD8,
                PhoneUi.inside(mx, my, TBX, TBY, TBW, TBH), true);
        // members: a dot each
        int x = 4;
        for (CompoundTag m : PhoneClient.compounds(data, "members")) {
            String name = m.getStringOr("name", "?");
            int w = font.width(name) + 9;
            if (x + w > W - 4) break;
            PhoneUi.dot(g, x, 16, m.getBooleanOr("online", false) ? PhoneUi.GOOD : PhoneUi.OFF);
            g.text(font, name, x + 7, 15, 0xFFB8C2D4, false);
            x += w + 3;
        }
        g.fill(0, CHAT_TOP - 2, W, CHAT_TOP - 1, PhoneUi.LINE);
        // chat, newest at the bottom
        List<CompoundTag> messages = PhoneClient.compounds(data, "messages");
        g.enableScissor(0, CHAT_TOP, W, CHAT_BOTTOM);
        int y = CHAT_BOTTOM - 2 + scroll;
        int total = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            CompoundTag m = messages.get(i);
            boolean mine = m.getBooleanOr("mine", false);
            List<FormattedCharSequence> lines = font.split(Component.literal(m.getStringOr("text", "")), BUBBLE_W - 6);
            int bw = 0;
            for (FormattedCharSequence l : lines) bw = Math.max(bw, font.width(l));
            bw += 6;
            int bh = lines.size() * 9 + 4;
            int header = 9;
            int h = bh + header + 3;
            total += h;
            y -= h;
            if (y + h < CHAT_TOP || y > CHAT_BOTTOM) continue;
            String stamp = PhoneUi.clock(m.getLongOr("time", 0));
            if (mine) {
                g.text(font, stamp, W - 4 - font.width(stamp), y, PhoneUi.FAINT, false);
                int bx = W - 4 - bw;
                PhoneUi.round(g, bx, y + header, bw, bh, 0xFF2F62C8);
                for (int k = 0; k < lines.size(); k++) g.text(font, lines.get(k), bx + 3, y + header + 2 + k * 9, 0xFFFFFFFF, false);
            } else {
                Component who = Component.literal(m.getStringOr("name", "?")).append(Component.literal(" " + stamp).withColor(PhoneUi.FAINT));
                PhoneUi.text(g, who, 4, y, W - 8, 0xFF8FB8FF);
                PhoneUi.round(g, 4, y + header, bw, bh, 0xFF283044);
                for (int k = 0; k < lines.size(); k++) g.text(font, lines.get(k), 7, y + header + 2 + k * 9, PhoneUi.TEXT, false);
            }
        }
        contentHeight = total;
        if (messages.isEmpty()) {
            PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.team.empty"), W / 2, 70, W - 8, PhoneUi.FAINT);
        }
        g.disableScissor();
        // the message field
        g.fill(0, CHAT_BOTTOM + 1, W, CHAT_BOTTOM + 2, PhoneUi.LINE);
        PhoneUi.round(g, FX, FY, FW, FH, focused ? 0xFF2A3448 : PhoneUi.CARD);
        if (text.isEmpty() && !focused) {
            PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.team.hint"), FX + 4, FY + 4, FW - 8, PhoneUi.FAINT);
        } else {
            String shown = text.toString();
            while (font.width(shown) > FW - 10 && !shown.isEmpty()) shown = shown.substring(1);
            g.text(font, shown, FX + 4, FY + 4, PhoneUi.TEXT, false);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = FX + 4 + font.width(shown);
                g.fill(cx, FY + 3, cx + 1, FY + 13, 0xFFFFFFFF);
            }
        }
        PhoneUi.button(g, SBX, FY, SBW, FH, Component.translatable("gui.factoryascent.phone.team.send"), 0xFF3C6FD8,
                PhoneUi.inside(mx, my, SBX, FY, SBW, FH), !text.isEmpty());
    }

    private void sendMessage() {
        String msg = text.toString().trim();
        if (msg.isEmpty()) return;
        CompoundTag args = new CompoundTag();
        args.putString("text", msg);
        send("send", args);
        text.setLength(0);
    }

    @Override
    public boolean click(double mx, double my, int button) {
        if (PhoneUi.inside(mx, my, TBX, TBY, TBW, TBH)) {
            sendLeaving("screen");
            return true;
        }
        if (PhoneUi.inside(mx, my, SBX, FY, SBW, FH)) {
            sendMessage();
            return true;
        }
        boolean was = focused;
        focused = PhoneUi.inside(mx, my, FX, FY, FW, FH);
        return focused != was;
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        int max = Math.max(0, contentHeight - (CHAT_BOTTOM - CHAT_TOP) + 4);
        scroll = Math.max(0, Math.min(max, scroll + (int) Math.signum(amount) * 12));
        return true;
    }

    @Override
    public boolean capturesKeys() {
        return focused;
    }

    @Override
    public boolean key(KeyEvent event) {
        if (!focused) return false;
        if (event.isEscape()) {
            focused = false;
            return true;
        }
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            sendMessage();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE && !text.isEmpty()) {
            text.deleteCharAt(text.length() - 1);
            return true;
        }
        return true;
    }

    @Override
    public boolean character(CharacterEvent event) {
        if (!focused || !event.isAllowedChatCharacter() || text.length() >= MAX) return focused;
        text.append(event.codepointAsString());
        return true;
    }
}
