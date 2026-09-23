package net.juli2kapo.minewinx.client;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Qué jugadores (por id de entidad) están transformados y con qué elemento. */
public final class ClientWingState {

    private static final Map<Integer, String> WINGS = new HashMap<>();

    private ClientWingState() {}

    public static void set(int entityId, String element, boolean transformed) {
        if (transformed && element != null && !element.isEmpty()) {
            WINGS.put(entityId, element);
        } else {
            WINGS.remove(entityId);
        }
    }

    @Nullable
    public static String getWings(int entityId) {
        return WINGS.get(entityId);
    }

    public static void clear() {
        WINGS.clear();
    }
}
