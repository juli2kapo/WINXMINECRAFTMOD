package net.juli2kapo.factoryascent.phone;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The phone's ringtones: a short note-block jingle each (the client plays the notes a few ticks
 * apart, see {@code PhoneClient}). Alarm alerts (an overheating reactor) always use the
 * last, urgent pattern.
 */
public final class PhoneSounds {
    public static final int COUNT = 4;
    /** Settings keys: {@code gui.factoryascent.phone.ringtone.<key>}. */
    public static final String[] KEYS = {"chime", "bell", "pling", "bit"};
    /** Ticks between two notes. */
    public static final int STEP = 3;

    private PhoneSounds() {}

    public static Holder<SoundEvent> sound(int ringtone) {
        return switch (Math.floorMod(ringtone, COUNT)) {
            case 1 -> SoundEvents.NOTE_BLOCK_BELL;
            case 2 -> SoundEvents.NOTE_BLOCK_PLING;
            case 3 -> SoundEvents.NOTE_BLOCK_BIT;
            default -> SoundEvents.NOTE_BLOCK_CHIME;
        };
    }

    /** Note pitches of each jingle (note-block pitch: 2^((n-12)/12)). */
    public static float[] melody(int ringtone, boolean alarm) {
        if (alarm) return new float[] {1.6f, 1.2f, 1.6f, 1.2f, 1.6f};
        return switch (Math.floorMod(ringtone, COUNT)) {
            case 1 -> new float[] {1.0f, 1.26f};
            case 2 -> new float[] {0.75f, 1.0f, 1.5f};
            case 3 -> new float[] {1.5f, 1.12f, 1.5f};
            default -> new float[] {1.19f, 1.5f, 1.78f};
        };
    }
}
