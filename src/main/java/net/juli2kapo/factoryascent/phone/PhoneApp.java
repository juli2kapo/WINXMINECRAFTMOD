package net.juli2kapo.factoryascent.phone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The server half of a Factory Phone app. Register one with {@link PhoneApps#register} (from a
 * mod constructor) and its client half with {@code PhoneAppViews.register} (client constructor):
 * both are keyed by {@link #id()}. The phone does the rest: the home-screen icon
 * ({@code textures/gui/phone/app/<id>.png}, 24x24), the name ({@code gui.factoryascent.phone.app.<id>}),
 * the signal rule, battery, sessions and packets.
 *
 * <p>The protocol is deliberately generic so a new app needs no new packets: when the app is
 * opened (and every second while it stays open, if {@link #live()}) the server sends
 * {@link #data} as a {@link CompoundTag}; the app's buttons send back an action name with a
 * small argument tag, which {@link #action} must validate like any client input.
 */
public interface PhoneApp {
    /** Lower-case id, unique: the key of the icon, name and client view. */
    String id();

    /** Position on the home screen (lower first). */
    int order();

    /**
     * True: the app only works with the team's Uplink Satellite over the player's dimension
     * ({@link net.juli2kapo.factoryascent.orbital.OrbitalSignal}); without it the phone shows
     * "No signal" and never calls {@link #data} or {@link #action}.
     */
    boolean needsSignal();

    /** True: {@link #data} is re-sent about once a second while the app is open. */
    default boolean live() {
        return false;
    }

    /** What the app's screen shows right now, for this player and phone. */
    CompoundTag data(PhoneContext ctx);

    /**
     * A button press from the app's screen. Check everything (the client may lie); the phone
     * re-sends {@link #data} afterwards. Returns a short message to show, or null.
     */
    default @Nullable Component action(PhoneContext ctx, String action, CompoundTag args) {
        return null;
    }

    /** A number on the home-screen icon (unread messages, active alerts), 0 for none. */
    default int badge(PhoneContext ctx) {
        return 0;
    }
}
