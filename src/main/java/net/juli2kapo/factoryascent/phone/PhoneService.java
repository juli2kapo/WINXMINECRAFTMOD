package net.juli2kapo.factoryascent.phone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.juli2kapo.factoryascent.gear.PoweredItem;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.juli2kapo.factoryascent.orbital.OrbitRegistry;
import net.juli2kapo.factoryascent.orbital.Satellite;
import net.juli2kapo.factoryascent.orbital.SatelliteType;
import net.juli2kapo.factoryascent.phone.PhonePayloads.Action;
import net.juli2kapo.factoryascent.phone.PhonePayloads.AppData;
import net.juli2kapo.factoryascent.phone.PhonePayloads.AppInfo;
import net.juli2kapo.factoryascent.phone.PhonePayloads.Notify;
import net.juli2kapo.factoryascent.phone.PhonePayloads.PhoneState;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the Factory Phone: open phone sessions (battery drain, status bar refresh, live
 * app data), the action packets from the screen (validated here before any app sees them), push
 * notifications, and the background watch over linked machines that raises alerts.
 *
 * <p>Every action needs a phone in the player's hand with charge left; apps marked
 * {@link PhoneApp#needsSignal()} additionally need the team's Uplink Satellite over the player's
 * dimension.
 */
public final class PhoneService {
    /** An open phone screen: which hand holds the phone and which app is showing ("" = home). */
    static final class Session {
        final InteractionHand hand;
        String app = "";

        Session(InteractionHand hand) {
            this.hand = hand;
        }
    }

    /** What the background watch remembers about one watched machine. */
    static final class Watch {
        boolean wasWorking;
        int stoppedFor;
        boolean alerted;
        boolean goneAlerted;
        long hotUntil;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Map<GlobalPos, Watch>> WATCHES = new HashMap<>();
    private static final Set<UUID> LOW_BATTERY_WARNED = new HashSet<>();
    /** The last notifications each player got (newest last), for the Team app badge and GameTests. */
    private static final Map<UUID, Deque<Notify>> LOG = new HashMap<>();

    private PhoneService() {}

    // ---------------------------------------------------------------- phones

    public static boolean isPhone(ItemStack stack) {
        return stack.getItem() instanceof FactoryPhoneItem;
    }

    /** The hand holding a phone (main hand first), or null. */
    public static @Nullable InteractionHand phoneHand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (isPhone(player.getItemInHand(hand))) return hand;
        }
        return null;
    }

    /** The player's active phone: the one in hand, else the first in the inventory; empty if none. */
    public static ItemStack findPhone(Player player) {
        InteractionHand hand = phoneHand(player);
        if (hand != null) return player.getItemInHand(hand);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isPhone(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static int battery(ItemStack phone) {
        return PoweredItem.energy(phone);
    }

    /** Uses up to {@code amount} FE (down to empty); returns whether there is charge left. */
    public static boolean drain(ItemStack phone, int amount) {
        int have = battery(phone);
        if (amount > 0 && have > 0) phone.set(ModComponents.ENERGY.get(), Math.max(0, have - amount));
        return battery(phone) > 0;
    }

    /** Signal bars: none without uplink coverage, 3 with one Uplink Satellite overhead, 4 with more. */
    public static int bars(ServerPlayer player) {
        if (!PhoneDevices.signal(player)) return 0;
        MinecraftServer server = player.level().getServer();
        String team = FactoryTeams.get(server).teamOf(player.getUUID());
        long uplinks = OrbitRegistry.get(server).over(team, player.level().dimension()).stream()
                .map(Satellite::type).filter(t -> t == SatelliteType.UPLINK).count();
        return uplinks >= 2 ? 4 : 3;
    }

    // ---------------------------------------------------------------- sessions

    /** Opens the phone in {@code hand}; null on success, else why not. */
    public static @Nullable Component open(ServerPlayer player, InteractionHand hand) {
        ItemStack phone = player.getItemInHand(hand);
        if (!isPhone(phone)) return Component.empty();
        if (battery(phone) <= 0) {
            return Component.translatable("message.factoryascent.phone.battery_empty").withStyle(ChatFormatting.RED);
        }
        FactoryTeams.get(player.level().getServer()).remember(player);
        SESSIONS.put(player.getUUID(), new Session(hand));
        PhonePayloads.send(player, state(player, phone, true));
        return null;
    }

    /** True if the player has the phone screen open (for tests). */
    public static boolean isOpen(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** Which app the player's open phone shows ("" = home, null = closed). */
    public static @Nullable String openApp(UUID player) {
        Session s = SESSIONS.get(player);
        return s == null ? null : s.app;
    }

    static PhoneState state(ServerPlayer player, ItemStack phone, boolean open) {
        boolean signal = PhoneDevices.signal(player);
        InteractionHand hand = phoneHand(player);
        PhoneContext ctx = new PhoneContext(player, phone, hand == null ? InteractionHand.MAIN_HAND : hand, signal);
        List<AppInfo> apps = new ArrayList<>();
        for (PhoneApp app : PhoneApps.all()) {
            int badge = !app.needsSignal() || signal ? app.badge(ctx) : 0;
            apps.add(new AppInfo(app.id(), app.needsSignal(), badge));
        }
        FactoryTeams teams = FactoryTeams.get(player.level().getServer());
        String key = teams.teamOf(player.getUUID());
        String team = FactoryTeams.isSolo(key) ? "" : teams.displayName(key);
        int capacity = phone.getItem() instanceof PoweredItem p ? p.capacity() : 1;
        return new PhoneState(open, battery(phone) > 0, bars(player), battery(phone), capacity, PhoneMemory.of(phone), apps, team);
    }

    /** Closes the phone screen on the client (dead battery, phone put away). */
    private static void kick(ServerPlayer player, ItemStack phone) {
        SESSIONS.remove(player.getUUID());
        PhonePayloads.send(player, new PhoneState(false, false, 0, battery(phone), 1, PhoneMemory.of(phone), List.of(), ""));
    }

    /** The context for the player's open session, or null (and the screen is closed) if it isn't valid any more. */
    private static @Nullable PhoneContext context(ServerPlayer player, Session session) {
        ItemStack phone = player.getItemInHand(session.hand);
        if (!isPhone(phone) || battery(phone) <= 0) {
            if (battery(phone) <= 0 && isPhone(phone)) {
                player.sendOverlayMessage(Component.translatable("message.factoryascent.phone.battery_empty").withStyle(ChatFormatting.RED));
            }
            kick(player, phone);
            return null;
        }
        return new PhoneContext(player, phone, session.hand, PhoneDevices.signal(player));
    }

    /** Sends an app's data (or "No signal") to the player. */
    static void sendApp(PhoneContext ctx, PhoneApp app, Component message) {
        if (app.needsSignal() && !ctx.signal()) {
            PhonePayloads.send(ctx.player(), new AppData(app.id(), true, new CompoundTag(), message));
            return;
        }
        PhonePayloads.send(ctx.player(), new AppData(app.id(), false, app.data(ctx), message));
    }

    /** An action packet from the phone screen. */
    static void handle(ServerPlayer player, Action action) {
        UUID id = player.getUUID();
        if (action.app().isEmpty()) {
            switch (action.action()) {
                case "close" -> SESSIONS.remove(id);
                case "home" -> {
                    Session s = SESSIONS.get(id);
                    if (s != null) s.app = "";
                }
                case "resume" -> {
                    InteractionHand hand = phoneHand(player);
                    if (hand == null) {
                        PhonePayloads.send(player, new PhoneState(false, false, 0, 0, 1, PhoneMemory.EMPTY, List.of(), ""));
                        return;
                    }
                    Component problem = open(player, hand);
                    if (problem != null) {
                        kick(player, player.getItemInHand(hand));
                        player.sendOverlayMessage(problem);
                    }
                }
                default -> {
                }
            }
            return;
        }
        Session session = SESSIONS.get(id);
        PhoneApp app = PhoneApps.get(action.app());
        if (session == null || app == null) return;
        PhoneContext ctx = context(player, session);
        if (ctx == null) return;
        switch (action.action()) {
            case "open" -> {
                session.app = app.id();
                sendApp(ctx, app, Component.empty());
            }
            case "refresh" -> sendApp(ctx, app, Component.empty());
            default -> {
                if (app.needsSignal() && !ctx.signal()) {
                    sendApp(ctx, app, Component.empty());
                    return;
                }
                Component message = app.action(ctx, action.action(), action.args());
                // The action may have opened another screen (map, terminal): the phone screen reopens on return.
                sendApp(ctx, app, message == null ? Component.empty() : message);
                PhonePayloads.send(player, state(player, ctx.phone(), false));
            }
        }
    }

    // ---------------------------------------------------------------- notifications

    /**
     * Pushes a notification to the player's phone (toast + ringtone). Nothing happens without a
     * charged phone in the inventory or with notifications switched off. Returns whether it was sent.
     */
    public static boolean notify(ServerPlayer player, String app, Component title, Component body, int level) {
        ItemStack phone = findPhone(player);
        if (phone.isEmpty() || battery(phone) <= 0) return false;
        PhoneMemory memory = PhoneMemory.of(phone);
        if (!memory.alerts()) return false;
        Notify notify = new Notify(app, title, body, level, memory.sound() ? memory.ringtone() : -1);
        Deque<Notify> log = LOG.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        log.addLast(notify);
        while (log.size() > 16) log.removeFirst();
        PhonePayloads.send(player, notify);
        return true;
    }

    /** The last notifications the player got, oldest first (GameTests). */
    public static List<Notify> notificationsOf(UUID player) {
        return List.copyOf(LOG.getOrDefault(player, new ArrayDeque<>()));
    }

    /** Re-sends the given app to every player who has it open (e.g. the Team app after a new message). */
    public static void refreshOpen(MinecraftServer server, String app, java.util.function.Predicate<ServerPlayer> who) {
        PhoneApp phoneApp = PhoneApps.get(app);
        if (phoneApp == null) return;
        for (var entry : SESSIONS.entrySet()) {
            if (!entry.getValue().app.equals(app)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null || !who.test(p)) continue;
            PhoneContext ctx = context(p, entry.getValue());
            if (ctx != null) sendApp(ctx, phoneApp, Component.empty());
        }
    }

    // ---------------------------------------------------------------- ticking

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (var it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            Session session = entry.getValue();
            ItemStack phone = player.getItemInHand(session.hand);
            if (!isPhone(phone)) {
                it.remove();
                PhonePayloads.send(player, new PhoneState(false, false, 0, 0, 1, PhoneMemory.EMPTY, List.of(), ""));
                continue;
            }
            if (!drain(phone, PhoneConfig.DRAIN_OPEN.get())) {
                it.remove();
                player.sendOverlayMessage(Component.translatable("message.factoryascent.phone.battery_empty").withStyle(ChatFormatting.RED));
                kick(player, phone);
                continue;
            }
            PhonePayloads.send(player, state(player, phone, false));
            PhoneApp app = PhoneApps.get(session.app);
            if (app != null && app.live()) {
                sendApp(new PhoneContext(player, phone, session.hand, PhoneDevices.signal(player)), app, Component.empty());
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) watch(player);
    }

    /** Once a second per player: the background watch over the phone's linked machines. */
    public static void watch(ServerPlayer player) {
        ItemStack phone = findPhone(player);
        UUID id = player.getUUID();
        if (phone.isEmpty()) {
            WATCHES.remove(id);
            return;
        }
        int capacity = phone.getItem() instanceof PoweredItem p ? p.capacity() : 1;
        int charge = battery(phone);
        if (charge > capacity / 5) LOW_BATTERY_WARNED.remove(id);
        if (charge <= 0) return;
        PhoneMemory memory = PhoneMemory.of(phone);
        List<PhoneMemory.Link> machines = memory.of(PhoneMemory.MACHINE);
        if (machines.isEmpty() || !memory.alerts()) {
            WATCHES.remove(id);
            return;
        }
        if (!drain(phone, PhoneConfig.DRAIN_STANDBY.get())) return;
        if (battery(phone) < capacity / 10 && LOW_BATTERY_WARNED.add(id)) {
            notify(player, "settings", Component.translatable("gui.factoryascent.phone.low_battery"),
                    Component.translatable("gui.factoryascent.phone.low_battery_body"), 1);
        }
        boolean signal = PhoneDevices.signal(player);
        Map<GlobalPos, Watch> watches = WATCHES.computeIfAbsent(id, k -> new HashMap<>());
        watches.keySet().removeIf(pos -> memory.find(pos) == null);
        long now = player.level().getServer().overworld().getGameTime();
        for (PhoneMemory.Link link : machines) {
            Watch w = watches.computeIfAbsent(link.pos(), k -> new Watch());
            CompoundTag tag = PhoneDevices.machine(player, link, signal);
            Component name = Component.translatable(tag.getStringOr("name", link.block()));
            if (tag.getBooleanOr("offline", false)) {
                if (tag.getBooleanOr("gone", false) && !w.goneAlerted) {
                    w.goneAlerted = true;
                    alert(player, Component.translatable("gui.factoryascent.phone.alert.gone_title", name),
                            Component.translatable("gui.factoryascent.phone.alert.gone", link.pos().pos().toShortString()), 2);
                }
                continue;
            }
            w.goneAlerted = false;
            if (tag.contains("temp")) {
                float temp = tag.getFloatOr("temp", 0f);
                if (temp >= tag.getIntOr("alarm", Integer.MAX_VALUE) && now >= w.hotUntil) {
                    w.hotUntil = now + 600;
                    alert(player, Component.translatable("gui.factoryascent.phone.alert.hot_title", name),
                            Component.translatable("gui.factoryascent.phone.alert.hot", Math.round(temp), tag.getIntOr("meltdown", 0)), 2);
                }
            }
            if (tag.getBooleanOr("working", false)) {
                w.wasWorking = true;
                w.stoppedFor = 0;
                w.alerted = false;
            } else if (w.wasWorking && !tag.getBooleanOr("benign", false)) {
                if (++w.stoppedFor >= PhoneConfig.STOP_SECONDS.get() && !w.alerted) {
                    w.alerted = true;
                    w.wasWorking = false;
                    Component status = tag.contains("arg") ? Component.translatable(tag.getStringOr("status", ""), tag.getIntOr("arg", 0))
                            : Component.translatable(tag.getStringOr("status", ""));
                    alert(player, Component.translatable("gui.factoryascent.phone.alert.stopped_title", name),
                            Component.translatable("gui.factoryascent.phone.alert.stopped", status), 1);
                }
            }
        }
    }

    private static void alert(ServerPlayer player, Component title, Component body, int level) {
        if (notify(player, "machines", title, body, level)) PhoneContent.award(player, "phone_alert");
    }

    static void forget(UUID player) {
        SESSIONS.remove(player);
        WATCHES.remove(player);
        LOW_BATTERY_WARNED.remove(player);
        LOG.remove(player);
    }

    static void clear() {
        SESSIONS.clear();
        WATCHES.clear();
        LOW_BATTERY_WARNED.clear();
        LOG.clear();
    }
}
