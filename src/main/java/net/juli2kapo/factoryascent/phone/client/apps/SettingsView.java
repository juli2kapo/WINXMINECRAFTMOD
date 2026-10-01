package net.juli2kapo.factoryascent.phone.client.apps;

import java.util.List;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.juli2kapo.factoryascent.phone.PhoneSounds;
import net.juli2kapo.factoryascent.phone.client.PhoneAppView;
import net.juli2kapo.factoryascent.phone.client.PhoneClient;
import net.juli2kapo.factoryascent.phone.client.PhoneToasts;
import net.juli2kapo.factoryascent.phone.client.PhoneUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Settings app: notifications, ringtone, wallpaper, battery, and the linked devices with Unlink. */
public final class SettingsView extends PhoneAppView {
    private static final int ROW = 16, LIST_Y = 98, LIST_ROW = 17;
    private int scroll;
    private int confirm = -1;

    public SettingsView(String app) {
        super(app);
    }

    private List<CompoundTag> links() {
        return PhoneClient.compounds(data, "links");
    }

    private int listRows() {
        return (H - LIST_Y - 2) / LIST_ROW;
    }

    @Override
    protected void dataChanged() {
        confirm = -1;
        scroll = Math.max(0, Math.min(scroll, links().size() - listRows()));
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, float partial) {
        int y = 3;
        row(g, mx, my, y, Component.translatable("gui.factoryascent.phone.settings.alerts"));
        PhoneUi.toggle(g, W - 24, y + 3, data.getBooleanOr("alerts", true));
        y += ROW;
        row(g, mx, my, y, Component.translatable("gui.factoryascent.phone.settings.sound"));
        PhoneUi.toggle(g, W - 24, y + 3, data.getBooleanOr("sound", true));
        y += ROW;
        int ringtone = Math.floorMod(data.getIntOr("ringtone", 0), PhoneSounds.COUNT);
        row(g, mx, my, y, Component.translatable("gui.factoryascent.phone.settings.ringtone"));
        PhoneUi.textRight(g, Component.translatable("gui.factoryascent.phone.ringtone." + PhoneSounds.KEYS[ringtone]), W - 6, y + 4, 0xFF9AC8FF);
        y += ROW;
        int wp = Math.floorMod(data.getIntOr("wallpaper", 0), PhoneMemory.WALLPAPERS);
        row(g, mx, my, y, Component.translatable("gui.factoryascent.phone.settings.wallpaper"));
        g.blit(RenderPipelines.GUI_TEXTURED, PhoneUi.tex("wallpaper_" + wp), W - 18, y + 1, 0f, 0f, 10, 14, 132, 196, 132, 196);
        PhoneUi.textRight(g, Component.translatable("gui.factoryascent.phone.wallpaper." + wp), W - 22, y + 4, 0xFF9AC8FF);
        y += ROW;
        // battery
        int battery = state == null ? 0 : state.battery(), capacity = state == null ? 1 : Math.max(1, state.capacity());
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.settings.battery", PhoneUi.compact(battery), PhoneUi.compact(capacity)),
                6, y + 3, W - 10, PhoneUi.MUTED);
        PhoneUi.bar(g, 6, y + 13, W - 12, 5, battery / (float) capacity, battery < capacity / 10 ? PhoneUi.BAD : PhoneUi.GOOD);
        // linked devices
        List<CompoundTag> links = links();
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.settings.linked", links.size()), 4, LIST_Y - 11, W - 40, PhoneUi.TEXT);
        g.fill(0, LIST_Y - 2, W, LIST_Y - 1, PhoneUi.LINE);
        if (links.isEmpty()) {
            PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.settings.no_links"), 6, LIST_Y + 4, W - 12, 6, PhoneUi.FAINT);
        }
        for (int i = 0; i < listRows() && scroll + i < links.size(); i++) {
            int index = scroll + i;
            CompoundTag l = links.get(index);
            int ry = LIST_Y + i * LIST_ROW;
            int kind = l.getIntOr("kind", 1);
            int kc = kind == PhoneMemory.STORAGE ? 0xFF2D8C9A : kind == PhoneMemory.POWER ? 0xFFC8A020 : 0xFFD07A28;
            String letter = kind == PhoneMemory.STORAGE ? "S" : kind == PhoneMemory.POWER ? "P" : "M";
            PhoneUi.round(g, 3, ry + 2, 11, 11, kc);
            g.text(PhoneUi.font(), letter, 6, ry + 4, 0xFFFFFFFF, false);
            PhoneUi.text(g, Component.translatable(l.getStringOr("name", "")), 17, ry + 1, W - 44, PhoneUi.TEXT);
            g.pose().pushMatrix();
            g.pose().translate(17, ry + 10);
            g.pose().scale(0.5f, 0.5f);
            g.text(PhoneUi.font(), l.getIntOr("x", 0) + ", " + l.getIntOr("y", 0) + ", " + l.getIntOr("z", 0), 0, 0, PhoneUi.MUTED, false);
            g.pose().popMatrix();
            boolean asking = confirm == index;
            PhoneUi.button(g, W - 26, ry + 2, 22, 12, Component.literal(asking ? "?" : "×"), asking ? 0xFFC03838 : 0xFF4A5268,
                    PhoneUi.inside(mx, my, W - 26, ry + 2, 22, 12), true);
        }
        if (links.size() > listRows()) {
            String more = (scroll + 1) + "-" + Math.min(links.size(), scroll + listRows()) + "/" + links.size();
            g.text(PhoneUi.font(), more, W - 4 - PhoneUi.font().width(more), LIST_Y - 11, PhoneUi.FAINT, false);
        }
    }

    private static void row(GuiGraphicsExtractor g, int mx, int my, int y, Component label) {
        PhoneUi.card(g, 2, y, W - 4, ROW - 1, PhoneUi.inside(mx, my, 2, y, W - 4, ROW - 1));
        PhoneUi.text(g, label, 6, y + 4, W - 50, PhoneUi.TEXT);
    }

    @Override
    public boolean click(double mx, double my, int button) {
        int row = (int) ((my - 3) / ROW);
        if (my >= 3 && my < 3 + 4 * ROW && mx >= 2 && mx < W - 2) {
            switch (row) {
                case 0 -> send("alerts");
                case 1 -> send("sound");
                case 2 -> {
                    send("ringtone");
                    PhoneToasts.jingle(Math.floorMod(data.getIntOr("ringtone", 0) + 1, PhoneSounds.COUNT), false);
                }
                case 3 -> send("wallpaper");
                default -> {
                    return false;
                }
            }
            return true;
        }
        List<CompoundTag> links = links();
        for (int i = 0; i < listRows() && scroll + i < links.size(); i++) {
            int ry = LIST_Y + i * LIST_ROW;
            if (!PhoneUi.inside(mx, my, W - 26, ry + 2, 22, 12)) continue;
            int index = scroll + i;
            if (confirm != index) {
                confirm = index;
                return true;
            }
            CompoundTag l = links.get(index);
            CompoundTag args = new CompoundTag();
            args.putInt("index", index);
            args.putInt("x", l.getIntOr("x", 0));
            args.putInt("z", l.getIntOr("z", 0));
            send("unlink", args);
            confirm = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        int max = Math.max(0, links().size() - listRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount)));
        confirm = -1;
        return true;
    }
}
