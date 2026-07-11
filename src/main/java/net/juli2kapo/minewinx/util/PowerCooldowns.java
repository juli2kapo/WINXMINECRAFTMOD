package net.juli2kapo.minewinx.util;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cooldowns de poderes por jugador y slot, lado servidor (en memoria). */
public final class PowerCooldowns {

    private static final Map<UUID, long[]> EXPIRY = new ConcurrentHashMap<>();

    private PowerCooldowns() {}

    /** @return ticks restantes de cooldown para el slot (0 = listo) */
    public static long remaining(ServerPlayer player, int slot) {
        long[] slots = EXPIRY.get(player.getUUID());
        if (slots == null || slot < 1 || slot > 3) return 0;
        return Math.max(0, slots[slot] - player.serverLevel().getGameTime());
    }

    public static void set(ServerPlayer player, int slot, int cooldownTicks) {
        if (slot < 1 || slot > 3 || cooldownTicks <= 0) return;
        long[] slots = EXPIRY.computeIfAbsent(player.getUUID(), u -> new long[4]);
        slots[slot] = player.serverLevel().getGameTime() + cooldownTicks;
    }
}
