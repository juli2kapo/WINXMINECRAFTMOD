package net.juli2kapo.factoryascent.dyson;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server logic of the Dyson project around {@link DysonSwarm}: collectors joining a team's swarm
 * (from a Mass Driver or a Launch Pad), milestones (announced to the team once, advancements granted
 * to every member who reaches them) and keeping each player's client told how big their team's
 * swarm is (for the sky, the monitor hologram and the Dyson Monitor screen).
 */
public final class DysonService {
    /** Milestones: the first collector, then these fractions of the target. */
    static final double[] MILESTONES = {0, 0.10, 0.25, 0.50, 1.0};
    static final String[] MILESTONE_KEYS = {"dyson_first", "dyson_10", "dyson_25", "dyson_50", "dyson_100"};

    /** What each online player's client was last told: collectors × 1_000_003 + target. */
    private static final Map<UUID, Long> SENT = new HashMap<>();

    private DysonService() {}

    /** Whether a swarm of {@code collectors} has reached milestone {@code index}. */
    static boolean reached(int index, long collectors) {
        if (index == 0) return collectors >= 1;
        return collectors >= Math.ceil(MILESTONES[index] * DysonSwarm.target() - 1e-9);
    }

    /** Percent a milestone stands for (0 for the first collector). */
    public static int milestonePercent(int index) {
        return (int) Math.round(MILESTONES[index] * 100);
    }

    public static int milestoneCount() {
        return MILESTONES.length;
    }

    /**
     * Collectors reach solar orbit for a team: they join its swarm (up to the target), milestones
     * fire, and the team's clients are told. Returns how many joined.
     */
    public static long addCollectors(MinecraftServer server, String team, long count) {
        DysonSwarm swarm = DysonSwarm.get(server);
        long added = swarm.add(team, count);
        if (added > 0) changed(server, team);
        return added;
    }

    /** Sets a team's swarm size (admin command, tests); milestones above it are forgotten. */
    public static void setCollectors(MinecraftServer server, String team, long count) {
        DysonSwarm swarm = DysonSwarm.get(server);
        swarm.set(team, count);
        swarm.clearMilestonesAbove(team);
        changed(server, team);
    }

    /** Checks milestones and tells the team's clients. */
    static void changed(MinecraftServer server, String team) {
        DysonSwarm swarm = DysonSwarm.get(server);
        DysonSwarm.Project p = swarm.project(team);
        FactoryTeams teams = FactoryTeams.get(server);
        for (int i = 0; i < MILESTONES.length; i++) {
            if (!reached(i, p.collectors())) continue;
            if ((p.milestones() & (1 << i)) == 0) {
                swarm.markMilestone(team, i);
                announce(server, team, i, p.collectors());
            }
            award(server, teams.members(team), MILESTONE_KEYS[i]);
        }
        for (UUID member : teams.members(team)) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) sync(player);
        }
    }

    private static void announce(MinecraftServer server, String team, int index, long collectors) {
        Component message = index == 0
                ? Component.translatable("message.factoryascent.dyson.first").withStyle(ChatFormatting.GOLD)
                : Component.translatable("message.factoryascent.dyson.milestone", milestonePercent(index), collectors,
                DysonSwarm.target()).withStyle(index == MILESTONES.length - 1 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD);
        for (UUID member : FactoryTeams.get(server).members(team)) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player == null) continue;
            player.sendSystemMessage(message);
            float pitch = index == MILESTONES.length - 1 ? 0.6f : 1.0f;
            player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, pitch);
        }
    }

    /** Grants a code-triggered advancement ({@code minecraft:impossible} criterion "done") to online players. */
    static void award(MinecraftServer server, Iterable<UUID> players, String key) {
        AdvancementHolder holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, key));
        if (holder == null) return;
        for (UUID id : players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.getAdvancements().award(holder, "done");
        }
    }

    // ---------------------------------------------------------------- client sync

    /** The team a player is on (team key). */
    public static String teamOf(MinecraftServer server, UUID player) {
        return FactoryTeams.get(server).teamOf(player);
    }

    /** Sends the player their team's swarm size, if it changed since the last time. */
    public static void sync(ServerPlayer player) {
        if (player.connection == null || !player.connection.hasChannel(DysonPayloads.Sync.TYPE)) return; // fake/test players
        MinecraftServer server = player.level().getServer();
        String team = teamOf(server, player.getUUID());
        long collectors = DysonSwarm.get(server).collectors(team);
        int target = DysonSwarm.target();
        long key = collectors * 1_000_003L + target;
        Long sent = SENT.get(player.getUUID());
        if (sent != null && sent == key) return;
        SENT.put(player.getUUID(), key);
        PacketDistributor.sendToPlayer(player, new DysonPayloads.Sync(collectors, target));
    }

    /** Every second: team changes (joining, leaving) show up in the sky too. */
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }

    /** On login: the sky right away, and milestones the player's team reached while they were away. */
    static void login(ServerPlayer player) {
        SENT.remove(player.getUUID());
        MinecraftServer server = player.level().getServer();
        String team = teamOf(server, player.getUUID());
        long collectors = DysonSwarm.get(server).collectors(team);
        for (int i = 0; i < MILESTONES.length; i++) {
            if (reached(i, collectors)) award(server, java.util.List.of(player.getUUID()), MILESTONE_KEYS[i]);
        }
        sync(player);
    }

    static void logout(UUID player) {
        SENT.remove(player);
    }

    static void clear() {
        SENT.clear();
    }
}
