package net.juli2kapo.factoryascent.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/**
 * Holding right-click repeats a "press" every 4 ticks. Items whose use does one thing per press
 * (toggle a magnet, cycle a drill mode, report a failed recall) call {@link #hold} after acting
 * and check {@link #stillHeld} before acting: while the presses keep coming without a gap, the
 * button was never let go, so nothing repeats and the message on screen stays readable.
 *
 * <p>Tracked separately per side, so client and server agree as long as both call it from the
 * same {@code use} path.
 */
public final class HeldUse {
    /** Ticks without a press that count as "let go" (presses repeat every 4 ticks while held). */
    public static final int GAP = 6;

    private static final Map<UUID, Long> SERVER = new HashMap<>();
    private static final Map<UUID, Long> CLIENT = new HashMap<>();
    /** Extra ticks the first gap may last (an item cooldown set together with {@link #hold(Player, int)}). */
    private static final Map<UUID, Integer> GRACE = new HashMap<>(), CLIENT_GRACE = new HashMap<>();

    private HeldUse() {}

    private static Map<UUID, Long> map(Player player) {
        return player.level().isClientSide() ? CLIENT : SERVER;
    }

    /** Remembers that this player's use button is held right now, after an action that must not repeat. */
    public static void hold(Player player) {
        hold(player, 0);
    }

    /**
     * Like {@link #hold(Player)}, for an action that also puts the item on a cooldown of
     * {@code cooldownTicks}: no presses arrive during a cooldown, so the first gap may be that much longer (also
     * used for the lag between a server-side stop and the client's next repeated press).
     */
    public static void hold(Player player, int cooldownTicks) {
        map(player).put(player.getUUID(), player.level().getGameTime());
        (player.level().isClientSide() ? CLIENT_GRACE : GRACE).put(player.getUUID(), cooldownTicks);
    }

    /**
     * True (and remembers this press) while the button from the last {@link #hold} is still held:
     * the previous press was at most {@link #GAP} ticks ago. A fresh press returns false.
     */
    public static boolean stillHeld(Player player) {
        Map<UUID, Long> map = map(player);
        Long last = map.get(player.getUUID());
        if (last == null) return false;
        long now = player.level().getGameTime();
        Map<UUID, Integer> graces = player.level().isClientSide() ? CLIENT_GRACE : GRACE;
        int grace = graces.getOrDefault(player.getUUID(), 0);
        graces.remove(player.getUUID());
        if (now >= last && now - last <= GAP + grace) {
            map.put(player.getUUID(), now);
            return true;
        }
        map.remove(player.getUUID());
        return false;
    }

    public static void forget(UUID player) {
        SERVER.remove(player);
        CLIENT.remove(player);
        GRACE.remove(player);
        CLIENT_GRACE.remove(player);
    }
}
