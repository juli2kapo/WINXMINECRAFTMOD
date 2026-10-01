package net.juli2kapo.factoryascent.phone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The registry of phone apps (server halves). Built-in apps register in {@link PhoneContent};
 * later features add theirs the same way from their own mod constructor:
 * <pre>{@code PhoneApps.register(new MyApp());}</pre>
 * plus the client view ({@code PhoneAppViews.register("my_app", MyView::new)}), an icon
 * texture and a name. Ids must be unique; registering an id twice is an error.
 */
public final class PhoneApps {
    private static final Map<String, PhoneApp> APPS = new LinkedHashMap<>();
    private static @Nullable List<PhoneApp> sorted;

    private PhoneApps() {}

    public static synchronized void register(PhoneApp app) {
        if (!app.id().matches("[a-z0-9_]{1,32}")) throw new IllegalArgumentException("Bad phone app id: " + app.id());
        if (APPS.putIfAbsent(app.id(), app) != null) throw new IllegalStateException("Phone app registered twice: " + app.id());
        sorted = null;
    }

    public static @Nullable PhoneApp get(String id) {
        return APPS.get(id);
    }

    /** Every app in home-screen order. */
    public static synchronized List<PhoneApp> all() {
        if (sorted == null) {
            List<PhoneApp> list = new ArrayList<>(APPS.values());
            list.sort(Comparator.comparingInt(PhoneApp::order).thenComparing(PhoneApp::id));
            sorted = List.copyOf(list);
        }
        return sorted;
    }
}
