package net.juli2kapo.factoryascent.phone.client;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.juli2kapo.factoryascent.phone.PhonePayloads;
import net.juli2kapo.factoryascent.phone.PhonePayloads.AppInfo;
import net.juli2kapo.factoryascent.phone.PhonePayloads.PhoneState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Factory Phone's screen: a phone drawn in the middle of the screen (shrunk if the window is
 * short) with a status bar (clock, signal bars or "No signal", battery), a home screen with the
 * clock, wallpaper and app icons, and the open app. Apps zoom out of their icon when opened and
 * back into it on Home/Back. The bezel's buttons: Back, Home, and Close.
 *
 * <p>Everything shown comes from the server ({@link PhoneState}, per-app data); the screen only
 * sends what was pressed.
 */
public class PhoneScreen extends Screen {
    /** The phone body, its screen and the parts of the screen (phone coordinates). */
    static final int W = 144, H = 232;
    static final int SX = 6, SY = 16, SW = 132, SH = 196;
    static final int BAR = 11, TITLE = 14;
    static final int APP_X = SX, APP_Y = SY + BAR + TITLE;
    private static final int COLS = 3, CELL_W = 44, CELL_H = 44, GRID_Y = SY + 64;
    private static final long ANIM_MS = 170;

    private PhoneState state;
    private @Nullable PhoneAppView view;
    private @Nullable PhoneAppView leaving;
    private final Set<String> noSignal = new HashSet<>();
    private long animStart;
    private boolean animOpening;
    private float iconCx, iconCy;
    private Component flash = Component.empty();
    private long flashUntil;
    private PhonePayloads.@Nullable Notify banner;
    private long bannerStart;

    public PhoneScreen(PhoneState state, @Nullable String app) {
        super(Component.translatable("item.factoryascent.factory_phone"));
        this.state = state;
        if (app != null && !app.isEmpty()) {
            float[] c = iconCentre(app);
            iconCx = c[0];
            iconCy = c[1];
            view = PhoneAppViews.create(app);
            view.setState(state);
            CompoundTag cached = PhoneClient.cachedData(app);
            if (cached != null) view.setData(cached);
            ClientPacketDistributor.sendToServer(new PhonePayloads.Action(app, "open", new CompoundTag()));
        }
    }

    public PhoneState state() {
        return state;
    }

    /** The open app's id, or null on the home screen. */
    public @Nullable String currentApp() {
        return view == null ? null : view.app();
    }

    public void update(PhoneState state) {
        this.state = state;
        if (view != null) view.setState(state);
    }

    public void appData(PhonePayloads.AppData data) {
        if (data.noSignal()) noSignal.add(data.app());
        else noSignal.remove(data.app());
        if (view != null && view.app().equals(data.app())) {
            if (!data.noSignal()) view.setData(data.data());
            if (!data.message().getString().isEmpty()) {
                flash = data.message();
                flashUntil = System.currentTimeMillis() + 3000;
            }
        }
    }

    /** A notification arrived while the phone is open: it drops down over the screen. */
    public void banner(PhonePayloads.Notify notify) {
        banner = notify;
        bannerStart = System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- geometry

    private float scale() {
        return Math.min(1f, Math.min((height - 6) / (float) H, (width - 6) / (float) W));
    }

    private float left() {
        return (width - W * scale()) / 2f;
    }

    private float top() {
        return (height - H * scale()) / 2f;
    }

    private double localX(double mx) {
        return (mx - left()) / scale();
    }

    private double localY(double my) {
        return (my - top()) / scale();
    }

    private List<AppInfo> apps() {
        return state.apps();
    }

    private int cellX(int i) {
        return SX + (SW - COLS * CELL_W) / 2 + (i % COLS) * CELL_W;
    }

    private int cellY(int i) {
        return GRID_Y + (i / COLS) * CELL_H;
    }

    private float[] iconCentre(String app) {
        List<AppInfo> list = apps();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(app)) return new float[] {cellX(i) + CELL_W / 2f, cellY(i) + 14};
        }
        return new float[] {SX + SW / 2f, SY + SH / 2f};
    }

    private boolean hasSignal() {
        return state.bars() > 0;
    }

    private float anim() {
        float t = Mth.clamp((System.currentTimeMillis() - animStart) / (float) ANIM_MS, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private boolean animating() {
        return System.currentTimeMillis() - animStart < ANIM_MS;
    }

    // ---------------------------------------------------------------- navigation

    private void openApp(String id) {
        if (!state.alive()) return;
        float[] c = iconCentre(id);
        iconCx = c[0];
        iconCy = c[1];
        view = PhoneAppViews.create(id);
        view.setState(state);
        CompoundTag cached = PhoneClient.cachedData(id);
        if (cached != null) view.setData(cached);
        leaving = null;
        animOpening = true;
        animStart = System.currentTimeMillis();
        flash = Component.empty();
        click(1.6f);
        ClientPacketDistributor.sendToServer(new PhonePayloads.Action(id, "open", new CompoundTag()));
    }

    private void goHome() {
        if (view == null) return;
        float[] c = iconCentre(view.app());
        iconCx = c[0];
        iconCy = c[1];
        leaving = view;
        view = null;
        animOpening = false;
        animStart = System.currentTimeMillis();
        click(1.2f);
        ClientPacketDistributor.sendToServer(new PhonePayloads.Action("", "home", new CompoundTag()));
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.25f));
    }

    @Override
    public void tick() {
        if (view != null) view.tick();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        // A light dim only: the world (and the phone glowing in your hand) stays visible.
        g.fillGradient(0, 0, width, height, 0x50080A10, 0x90080A10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        float s = scale();
        int mx = (int) Math.floor(localX(mouseX)), my = (int) Math.floor(localY(mouseY));
        g.pose().pushMatrix();
        g.pose().translate(left(), top());
        g.pose().scale(s, s);
        drawBody(g, mx, my);
        g.enableScissor(SX, SY, SX + SW, SY + SH);
        if (!state.alive()) {
            drawDead(g);
        } else {
            boolean anim = animating();
            PhoneAppView shown = view != null ? view : anim ? leaving : null;
            if (shown == null || anim) drawHome(g, shown == null ? mx : -1000, shown == null ? my : -1000);
            if (shown != null) {
                float e = anim();
                float k = animOpening ? e : 1f - e;
                if (!anim) k = 1f;
                g.pose().pushMatrix();
                float cx = SX + SW / 2f, cy = SY + SH / 2f;
                float zoom = 0.12f + 0.88f * k;
                g.pose().translate(iconCx + (cx - iconCx) * k, iconCy + (cy - iconCy) * k);
                g.pose().scale(zoom, zoom);
                g.pose().translate(-cx, -cy);
                drawApp(g, shown, anim ? -1000 : mx, anim ? -1000 : my, partial);
                g.pose().popMatrix();
            }
            drawStatusBar(g, shown == null && !anim);
            drawBanner(g);
        }
        g.disableScissor();
        // glass glare over the screen
        g.fill(SX, SY, SX + 2, SY + SH, 0x0CFFFFFF);
        g.pose().popMatrix();
    }

    private void drawBody(GuiGraphicsExtractor g, int mx, int my) {
        // graphite body with a lighter rim and darker inner bezel
        PhoneUi.round2(g, 0, 0, W, H, 0xFF0A0C10);
        PhoneUi.round2(g, 1, 1, W - 2, H - 2, 0xFF3A4150);
        PhoneUi.round2(g, 2, 2, W - 4, H - 4, 0xFF1C2028);
        g.fill(3, 3, W - 3, 4, 0xFF4A5262);
        // side buttons
        g.fill(-1, 40, 0, 56, 0xFF3A4150);
        g.fill(W, 46, W + 1, 70, 0xFF3A4150);
        // speaker slit and camera
        PhoneUi.round(g, W / 2 - 14, 7, 28, 3, 0xFF0B0D12);
        PhoneUi.round(g, W / 2 + 22, 6, 5, 5, 0xFF0B0D12);
        g.fill(W / 2 + 23, 7, W / 2 + 25, 9, 0xFF2A3A5A);
        // the screen's black border
        g.fill(SX - 1, SY - 1, SX + SW + 1, SY + SH + 1, 0xFF000000);
        g.fill(SX, SY, SX + SW, SY + SH, PhoneUi.BG);
        // bezel buttons: back, home, close
        int by = SY + SH + 3;
        boolean on = state.alive();
        int base = on ? 0xFF8A93A6 : 0xFF4A5060;
        int hiBack = inBack(mx, my) ? 0xFFFFFFFF : base;
        int hiHome = inHome(mx, my) ? 0xFFFFFFFF : base;
        int hiClose = inClose(mx, my) ? 0xFFFFFFFF : base;
        // back: a small triangle
        int bx = 28;
        for (int i = 0; i < 5; i++) g.fill(bx + i, by + 7 - i, bx + i + 1, by + 8 + i, hiBack);
        // home: a ring
        int hx = W / 2 - 6;
        PhoneUi.round2(g, hx, by + 2, 12, 12, hiHome);
        PhoneUi.round2(g, hx + 2, by + 4, 8, 8, 0xFF1C2028);
        // close: a cross
        int cx = W - 34;
        for (int i = 0; i < 7; i++) {
            g.fill(cx + i, by + 4 + i, cx + i + 1, by + 5 + i, hiClose);
            g.fill(cx + 6 - i, by + 4 + i, cx + 7 - i, by + 5 + i, hiClose);
        }
    }

    private static boolean inBack(double x, double y) {
        return PhoneUi.inside(x, y, 20, SY + SH + 1, 22, 16);
    }

    private static boolean inHome(double x, double y) {
        return PhoneUi.inside(x, y, W / 2 - 11, SY + SH + 1, 22, 16);
    }

    private static boolean inClose(double x, double y) {
        return PhoneUi.inside(x, y, W - 39, SY + SH + 1, 22, 16);
    }

    private void drawStatusBar(GuiGraphicsExtractor g, boolean overWallpaper) {
        g.fill(SX, SY, SX + SW, SY + BAR, overWallpaper ? 0x66000000 : 0xFF0A0D13);
        Font font = this.font;
        long time = minecraft != null && minecraft.level != null ? minecraft.level.getOverworldClockTime() : 0;
        g.text(font, PhoneUi.clock(time), SX + 3, SY + 2, 0xFFFFFFFF, false);
        // battery: outline, fill, percent
        int capacity = Math.max(1, state.capacity());
        float charge = Mth.clamp(state.battery() / (float) capacity, 0f, 1f);
        int bx = SX + SW - 16, by = SY + 3;
        g.fill(bx, by, bx + 12, by + 6, 0xFFE8EDF5);
        g.fill(bx + 1, by + 1, bx + 11, by + 5, 0xFF0A0D13);
        g.fill(bx + 12, by + 2, bx + 13, by + 4, 0xFFE8EDF5);
        int fill = Math.round(10 * charge);
        int bc = charge < 0.1f ? PhoneUi.BAD : charge < 0.25f ? PhoneUi.WARN : PhoneUi.GOOD;
        if (fill > 0) g.fill(bx + 1, by + 1, bx + 1 + fill, by + 5, bc);
        String pct = Math.round(charge * 100) + "%";
        int px = bx - 2 - font.width(pct);
        g.text(font, pct, px, SY + 2, 0xFFCCD3E0, false);
        // signal: four bars, or "No signal"
        int right = px - 4;
        if (hasSignal()) {
            for (int i = 0; i < 4; i++) {
                int h = 2 + i * 2;
                int x = right - 15 + i * 4;
                g.fill(x, SY + 9 - h, x + 3, SY + 9, i < state.bars() ? 0xFFFFFFFF : 0x55FFFFFF);
            }
        } else {
            Component none = Component.translatable("gui.factoryascent.phone.no_signal");
            g.text(font, none, right - font.width(none), SY + 2, 0xFFF08080, false);
        }
    }

    private void drawHome(GuiGraphicsExtractor g, int mx, int my) {
        int wp = Math.floorMod(state.memory().wallpaper(), net.juli2kapo.factoryascent.phone.PhoneMemory.WALLPAPERS);
        g.blit(RenderPipelines.GUI_TEXTURED, PhoneUi.tex("wallpaper_" + wp), SX, SY, 0f, 0f, SW, SH, SW, SH);
        // the clock widget
        long time = minecraft != null && minecraft.level != null ? minecraft.level.getOverworldClockTime() : 0;
        String clock = PhoneUi.clock(time);
        g.pose().pushMatrix();
        g.pose().translate(SX + SW / 2f, SY + 18);
        g.pose().scale(2.5f, 2.5f);
        g.text(font, clock, -font.width(clock) / 2, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
        Component day = Component.translatable("gui.factoryascent.phone.day", PhoneUi.day(time));
        if (!state.team().isEmpty()) day = Component.translatable("gui.factoryascent.phone.day_team", PhoneUi.day(time), state.team());
        PhoneUi.centered(g, day, SX + SW / 2, SY + 44, SW - 8, 0xFFE0E6F0);
        // the icons
        List<AppInfo> list = apps();
        for (int i = 0; i < list.size(); i++) {
            AppInfo app = list.get(i);
            int x = cellX(i), y = cellY(i);
            boolean hover = PhoneUi.inside(mx, my, x, y, CELL_W, CELL_H);
            boolean dim = app.needsSignal() && !hasSignal();
            int ix = x + (CELL_W - 24) / 2, iy = y + 2 - (hover ? 1 : 0);
            g.fill(ix + 1, iy + 25, ix + 23, iy + 26, 0x40000000);
            PhoneUi.icon(g, app.id(), ix, iy, 0xFFFFFFFF);
            if (dim) {
                // greyed out, with a small crossed-out antenna
                PhoneUi.round(g, ix, iy, 24, 24, 0x99101420);
                PhoneUi.round(g, ix + 15, iy + 15, 10, 10, 0xFF202634);
                for (int k = 0; k < 3; k++) g.fill(ix + 17 + k * 2, iy + 22 - k * 2, ix + 18 + k * 2, iy + 23, 0xFF8A93A6);
                for (int k = 0; k < 7; k++) g.fill(ix + 16 + k, iy + 16 + k, ix + 17 + k, iy + 17 + k, 0xFFF06060);
            }
            if (app.badge() > 0) {
                String n = app.badge() > 9 ? "9+" : Integer.toString(app.badge());
                int bw = Math.max(9, font.width(n) + 4);
                PhoneUi.round2(g, ix + 24 - bw + 3, iy - 3, bw, 9, 0xFFE03C3C);
                g.text(font, n, ix + 24 - bw + 3 + (bw - font.width(n)) / 2 + 1, iy - 2, 0xFFFFFFFF, false);
            }
            Component name = Component.translatable("gui.factoryascent.phone.app." + app.id());
            PhoneUi.centered(g, name, x + CELL_W / 2, y + 29, CELL_W + 2, hover ? 0xFFFFFFFF : 0xFFE8EDF5);
        }
        if (list.isEmpty()) PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.loading"), SX + SW / 2, SY + 100, SW, 0xFFFFFFFF);
    }

    private void drawApp(GuiGraphicsExtractor g, PhoneAppView app, int mx, int my, float partial) {
        g.fill(SX, SY, SX + SW, SY + SH, PhoneUi.BG);
        int accent = PhoneAppViews.accent(app.app());
        int ty = SY + BAR;
        g.fill(SX, ty, SX + SW, ty + TITLE, accent);
        g.fill(SX, ty + TITLE - 1, SX + SW, ty + TITLE, PhoneUi.brighter(accent));
        // the 24px icon at half size
        g.pose().pushMatrix();
        g.pose().translate(SX + 3, ty + 1);
        g.pose().scale(0.5f, 0.5f);
        PhoneUi.icon(g, app.app(), 0, 0, 0xFFFFFFFF);
        g.pose().popMatrix();
        Component sub = noSignal.contains(app.app()) ? Component.empty() : app.subtitle();
        int subW = font.width(sub);
        PhoneUi.text(g, Component.translatable("gui.factoryascent.phone.app." + app.app()), SX + 18, ty + 3, SW - 24 - subW, 0xFFFFFFFF);
        if (subW > 0) PhoneUi.textRight(g, sub, SX + SW - 4, ty + 3, 0xFFE0E8F4);
        g.enableScissor(APP_X, APP_Y, APP_X + PhoneAppView.W, APP_Y + PhoneAppView.H);
        g.pose().pushMatrix();
        g.pose().translate(APP_X, APP_Y);
        if (noSignal.contains(app.app())) {
            drawNoSignal(g);
        } else if (!app.loaded) {
            String dots = ".".repeat((int) (System.currentTimeMillis() / 300 % 4));
            PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.loading").append(dots), PhoneAppView.W / 2, 70, PhoneAppView.W, PhoneUi.MUTED);
        } else {
            app.setState(state);
            app.render(g, mx - APP_X, my - APP_Y, partial);
        }
        g.pose().popMatrix();
        g.disableScissor();
        if (System.currentTimeMillis() < flashUntil && !flash.getString().isEmpty()) {
            var lines = font.split(flash, SW - 16);
            int h = Math.min(3, lines.size()) * 10 + 6;
            int y = SY + SH - h - 4;
            PhoneUi.round2(g, SX + 4, y, SW - 8, h, 0xEE2A3245);
            for (int i = 0; i < Math.min(3, lines.size()); i++) g.text(font, lines.get(i), SX + 8, y + 4 + i * 10, 0xFFFFFFFF, false);
        }
    }

    private void drawNoSignal(GuiGraphicsExtractor g) {
        int cx = PhoneAppView.W / 2;
        // a crossed-out antenna
        for (int i = 0; i < 4; i++) {
            int h = 4 + i * 4;
            g.fill(cx - 13 + i * 7, 52 - h, cx - 8 + i * 7, 52, 0xFF3A4254);
        }
        for (int i = 0; i < 26; i++) g.fill(cx - 13 + i, 28 + i, cx - 11 + i, 30 + i, PhoneUi.BAD);
        PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.no_signal_title"), cx, 64, PhoneAppView.W - 8, PhoneUi.TEXT);
        PhoneUi.wrapped(g, Component.translatable("gui.factoryascent.phone.no_signal_body"), 8, 80, PhoneAppView.W - 16, 6, PhoneUi.MUTED);
    }

    private void drawDead(GuiGraphicsExtractor g) {
        g.fill(SX, SY, SX + SW, SY + SH, 0xFF000000);
        int cx = SX + SW / 2, cy = SY + SH / 2 - 10;
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        g.fill(cx - 14, cy - 7, cx + 12, cy + 7, 0xFF8A93A6);
        g.fill(cx - 13, cy - 6, cx + 11, cy + 6, 0xFF000000);
        g.fill(cx + 12, cy - 3, cx + 14, cy + 3, 0xFF8A93A6);
        if (blink) g.fill(cx - 12, cy - 5, cx - 9, cy + 5, PhoneUi.BAD);
        PhoneUi.centered(g, Component.translatable("gui.factoryascent.phone.dead"), cx, cy + 14, SW - 8, 0xFF8A93A6);
    }

    private void drawBanner(GuiGraphicsExtractor g) {
        if (banner == null) return;
        long age = System.currentTimeMillis() - bannerStart;
        if (age > 3500) {
            banner = null;
            return;
        }
        float slide = age < 200 ? age / 200f : age > 3200 ? (3500 - age) / 300f : 1f;
        int h = 30;
        int y = SY + BAR + 2 - Math.round((1f - slide) * (h + 4));
        int color = banner.level() >= 2 ? 0xF0602028 : banner.level() == 1 ? 0xF0504018 : 0xF0283448;
        PhoneUi.round2(g, SX + 3, y, SW - 6, h, color);
        g.pose().pushMatrix();
        g.pose().translate(SX + 6, y + 3);
        g.pose().scale(0.5f, 0.5f);
        PhoneUi.icon(g, banner.app(), 0, 0, 0xFFFFFFFF);
        g.pose().popMatrix();
        PhoneUi.text(g, banner.title(), SX + 20, y + 4, SW - 28, 0xFFFFFFFF);
        PhoneUi.text(g, banner.body(), SX + 7, y + 17, SW - 14, 0xFFD8DEE8);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = localX(event.x()), y = localY(event.y());
        if (inBack(x, y)) {
            if (view != null) goHome();
            else onClose();
            return true;
        }
        if (inHome(x, y)) {
            if (view != null) goHome();
            return true;
        }
        if (inClose(x, y)) {
            onClose();
            return true;
        }
        if (!state.alive() || animating()) return true;
        if (banner != null && PhoneUi.inside(x, y, SX + 3, SY + BAR + 2, SW - 6, 30)) {
            String app = banner.app();
            banner = null;
            if (view == null || !view.app().equals(app)) openApp(app);
            return true;
        }
        if (view != null) {
            if (!noSignal.contains(view.app()) && view.loaded
                    && PhoneUi.inside(x, y, APP_X, APP_Y, PhoneAppView.W, PhoneAppView.H)) {
                if (view.click(x - APP_X, y - APP_Y, event.button())) click(1.8f);
            }
            return true;
        }
        List<AppInfo> list = apps();
        for (int i = 0; i < list.size(); i++) {
            if (PhoneUi.inside(x, y, cellX(i), cellY(i), CELL_W, CELL_H)) {
                openApp(list.get(i).id());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (view != null && view.loaded && view.drag(localX(event.x()) - APP_X, localY(event.y()) - APP_Y, event.button())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (view != null && view.loaded && view.release(localX(event.x()) - APP_X, localY(event.y()) - APP_Y, event.button())) {
            click(1.8f);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = localX(mouseX), y = localY(mouseY);
        if (view != null && view.loaded && scrollY != 0) {
            return view.scroll(x - APP_X, y - APP_Y, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (view != null && view.capturesKeys()) {
            if (event.isEscape() || view.key(event)) {
                if (event.isEscape()) view.key(event);
                return true;
            }
            return true;
        }
        if (event.isEscape() || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (view != null) {
                goHome();
                return true;
            }
            onClose();
            return true;
        }
        if (view != null && view.key(event)) return true;
        if (minecraft != null && minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (view != null && view.character(event)) return true;
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        PhoneClient.cancelChild();
        super.onClose();
    }

    @Override
    public void removed() {
        if (!PhoneClient.leavingForChild()) {
            ClientPacketDistributor.sendToServer(new PhonePayloads.Action("", "close", new CompoundTag()));
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
