package net.juli2kapo.minewinx.client;

import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registro cliente de entidades con la burbuja de ahogo (por id, con expiración). */
public final class ClientDrowningTracker {

    private static final Map<Integer, Long> EXPIRY = new ConcurrentHashMap<>();

    private ClientDrowningTracker() {}

    public static void mark(int entityId, int durationTicks) {
        EXPIRY.put(entityId, currentTime() + durationTicks);
    }

    public static boolean isDrowning(int entityId) {
        Long until = EXPIRY.get(entityId);
        if (until == null) return false;
        if (until <= currentTime()) {
            EXPIRY.remove(entityId);
            return false;
        }
        return true;
    }

    private static long currentTime() {
        return Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }
}
