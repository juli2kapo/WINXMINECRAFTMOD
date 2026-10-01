package net.juli2kapo.factoryascent.phone.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.juli2kapo.factoryascent.phone.PhonePayloads.Notify;
import net.juli2kapo.factoryascent.phone.PhoneSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/**
 * Phone notifications on the client: a card that drops down at the top centre of the HUD (or drops
 * down inside the phone when its screen is open) and the ringtone jingle, note by note.
 */
public final class PhoneToasts {
    private static final long SHOW_MS = 5000, SLIDE_MS = 250;
    private static final int TW = 220;

    private record Shown(Notify note, long start) {}

    private record Note(int ringtone, float pitch, int atTick) {}

    private static final Deque<Notify> QUEUE = new ArrayDeque<>();
    private static Shown current;
    private static final List<Note> NOTES = new ArrayList<>();
    private static int ticks;

    private PhoneToasts() {}

    public static void push(Notify notify) {
        if (notify.ringtone() >= 0) jingle(notify.ringtone(), notify.level() >= 2);
        if (Minecraft.getInstance().gui.screen() instanceof PhoneScreen screen) {
            screen.banner(notify);
            return;
        }
        if (QUEUE.size() < 4) QUEUE.addLast(notify);
    }

    /** Queues the ringtone's notes a few ticks apart. */
    public static void jingle(int ringtone, boolean alarm) {
        float[] melody = PhoneSounds.melody(ringtone, alarm);
        NOTES.clear();
        for (int i = 0; i < melody.length; i++) NOTES.add(new Note(ringtone, melody[i], ticks + i * PhoneSounds.STEP));
    }

    static void tick() {
        ticks++;
        if (NOTES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        for (var it = NOTES.iterator(); it.hasNext(); ) {
            Note n = it.next();
            if (n.atTick() > ticks) continue;
            it.remove();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(PhoneSounds.sound(n.ringtone()).value(), n.pitch(), 0.6f));
        }
    }

    static void clear() {
        QUEUE.clear();
        current = null;
        NOTES.clear();
    }

    /** HUD layer: the current notification card. */
    static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (current != null && now - current.start() > SHOW_MS) current = null;
        if (current == null && !QUEUE.isEmpty()) current = new Shown(QUEUE.removeFirst(), now);
        if (current == null || mc.player == null) return;
        long age = now - current.start();
        float slide = age < SLIDE_MS ? age / (float) SLIDE_MS : age > SHOW_MS - SLIDE_MS ? (SHOW_MS - age) / (float) SLIDE_MS : 1f;
        slide = 1f - (1f - slide) * (1f - slide);
        Notify n = current.note();
        var body = mc.font.split(n.body(), TW - 38);
        int lines = Math.max(1, Math.min(2, body.size()));
        int th = 22 + lines * 10;
        // drops down at the top centre, like a phone's notification (vanilla toasts use the top right)
        int x = (g.guiWidth() - TW) / 2, y = 4 - Math.round((th + 6) * (1f - slide));
        int edge = n.level() >= 2 ? PhoneUi.BAD : n.level() == 1 ? PhoneUi.WARN : PhoneUi.ACCENT;
        PhoneUi.round2(g, x, y, TW, th, 0xF0141A26);
        g.fill(x + 1, y + 2, x + 3, y + th - 2, edge);
        PhoneUi.icon(g, n.app(), x + 6, y + (th - 24) / 2, 0xFFFFFFFF);
        PhoneUi.text(g, n.title(), x + 34, y + 5, TW - 38, 0xFFFFFFFF);
        for (int i = 0; i < lines && i < body.size(); i++) g.text(mc.font, body.get(i), x + 34, y + 17 + i * 10, 0xFFC8D0DC, false);
    }
}
