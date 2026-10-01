package net.juli2kapo.factoryascent.phone.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The registry of phone app views (client halves), keyed by the same id as the server's
 * {@link net.juli2kapo.factoryascent.phone.PhoneApp}. Each entry also gives the app's accent colour
 * (its title bar). Register from a client-dist mod constructor:
 * <pre>{@code PhoneAppViews.register("my_app", 0xFF40A0E0, MyView::new);}</pre>
 */
public final class PhoneAppViews {
    public record Entry(int accent, Function<String, PhoneAppView> factory) {}

    private static final Map<String, Entry> VIEWS = new HashMap<>();

    private PhoneAppViews() {}

    public static synchronized void register(String id, int accent, Function<String, PhoneAppView> factory) {
        if (VIEWS.putIfAbsent(id, new Entry(accent, factory)) != null) {
            throw new IllegalStateException("Phone app view registered twice: " + id);
        }
    }

    public static @Nullable Entry get(String id) {
        return VIEWS.get(id);
    }

    public static int accent(String id) {
        Entry e = VIEWS.get(id);
        return e == null ? PhoneUi.ACCENT : e.accent();
    }

    /** A new view for the app (a placeholder page if no view was registered). */
    public static PhoneAppView create(String id) {
        Entry e = VIEWS.get(id);
        return e == null ? new Placeholder(id) : e.factory().apply(id);
    }

    /** Shown for an app the server knows but this client has no view for. */
    static final class Placeholder extends PhoneAppView {
        Placeholder(String app) {
            super(app);
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float partial) {
            PhoneUi.centered(g, net.minecraft.network.chat.Component.translatable("gui.factoryascent.phone.no_view"), W / 2, 60, W - 8, PhoneUi.MUTED);
        }
    }
}
