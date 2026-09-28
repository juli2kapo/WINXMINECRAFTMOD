package net.juli2kapo.factoryascent.orbital;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Deflater;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.Marker;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.Row;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.SurveyAction;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.SurveyHeader;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.SurveyTiles;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.SurveyView;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the survey map screen. {@link #openAtStation} (right-click a Ground Station) and
 * {@link #openRemote} (a Wireless Terminal over the uplink) open it; while it is open the player
 * has a session that streams the team's {@link SurveyData} for that dimension in compressed
 * batches ({@link SurveyTiles}, nearest chunks first), then every newly imaged chunk as it comes
 * in, plus a {@link SurveyView} (status, satellites, markers) about once a second. The session ends
 * when the screen closes, the player leaves or changes dimension, or (remote) loses the signal.
 *
 * <p>The same API is what a future phone app would call: {@code openRemote(player)}.
 */
public final class SurveyService {
    /** Tile packets per session per tick. */
    private static final int PACKETS_PER_TICK = 2;

    private static final class Session {
        final String team;
        final ResourceKey<Level> dimension;
        final BlockPos origin;
        final boolean atStation;
        final LongLinkedOpenHashSet pending = new LongLinkedOpenHashSet();
        boolean streaming;
        int ticks;

        Session(String team, ResourceKey<Level> dimension, BlockPos origin, boolean atStation) {
            this.team = team;
            this.dimension = dimension;
            this.origin = origin;
            this.atStation = atStation;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SurveyService() {}

    // ---------------------------------------------------------------- opening

    /** Opens the survey map of a Ground Station for a player (claiming an unowned station for them). */
    public static void openAtStation(ServerPlayer player, GroundStationBlockEntity station) {
        MinecraftServer server = player.level().getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        teams.remember(player);
        if (station.owner() == null) station.setOwner(player.getUUID());
        open(player, teams.teamOf(player.getUUID()), station.getBlockPos(), true);
    }

    /**
     * Opens the survey map centred on the player, over the uplink: needs the team's Uplink
     * Satellite over the player's dimension. Null on success, else why not.
     */
    public static @Nullable Component openRemote(ServerPlayer player) {
        if (!OrbitalSignal.hasCoverage(player)) return Component.translatable("message.factoryascent.no_signal");
        FactoryTeams teams = FactoryTeams.get(player.level().getServer());
        teams.remember(player);
        open(player, teams.teamOf(player.getUUID()), player.blockPosition(), false);
        return null;
    }

    private static void open(ServerPlayer player, String team, BlockPos origin, boolean atStation) {
        Session session = new Session(team, player.level().dimension(), origin.immutable(), atStation);
        SESSIONS.put(player.getUUID(), session);
        MinecraftServer server = player.level().getServer();
        PacketDistributor.sendToPlayer(player, view(server, player, session, true, Component.empty()));
        if (surveying(server, session)) {
            OrbitalContent.award(server, List.of(player.getUUID()), "orbital_station");
        }
    }

    /** True if the player has the survey map open right now (for tests). */
    public static boolean isOpen(UUID player) {
        return SESSIONS.containsKey(player);
    }

    private static boolean surveying(MinecraftServer server, Session s) {
        return OrbitRegistry.get(server).has(s.team, s.dimension, SatelliteType.SURVEY);
    }

    // ---------------------------------------------------------------- streaming

    /** A chunk of a team's survey changed: queue it for every open map of that team and dimension. */
    static void chunkChanged(MinecraftServer server, String team, ResourceKey<Level> dimension, long chunk) {
        for (Session s : SESSIONS.values()) {
            if (s.streaming && s.team.equals(team) && s.dimension.equals(dimension)) s.pending.add(chunk);
        }
    }

    static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (var it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session s = entry.getValue();
            if (player == null || !player.level().dimension().equals(s.dimension)) {
                it.remove();
                continue;
            }
            Component message = Component.empty();
            if (!s.atStation && !OrbitalSignal.hasCoverage(player)) {
                message = Component.translatable("message.factoryascent.survey_signal_lost").withStyle(ChatFormatting.RED);
                s.streaming = false;
                s.pending.clear();
            } else if (surveying(server, s)) {
                if (!s.streaming) {
                    s.streaming = true;
                    queueAll(server, s);
                }
                for (int n = 0; n < PACKETS_PER_TICK && !s.pending.isEmpty(); n++) {
                    PacketDistributor.sendToPlayer(player, tiles(SurveyData.get(server, s.team, s.dimension), s.pending));
                }
            } else {
                s.streaming = false;
                s.pending.clear();
            }
            if (++s.ticks % 20 == 0) PacketDistributor.sendToPlayer(player, view(server, player, s, false, message));
        }
    }

    /** Queues every imaged chunk of the team's survey, nearest to the map's centre first. */
    private static void queueAll(MinecraftServer server, Session s) {
        SurveyData data = SurveyData.get(server, s.team, s.dimension);
        int ox = s.origin.getX() >> 4, oz = s.origin.getZ() >> 4;
        long[] all = data.chunks().toLongArray();
        LongArrayList sorted = new LongArrayList(all);
        sorted.sort((a, b) -> Long.compare(dist(a, ox, oz), dist(b, ox, oz)));
        s.pending.addAll(sorted);
    }

    private static long dist(long chunk, int ox, int oz) {
        long dx = ChunkPos.getX(chunk) - ox, dz = ChunkPos.getZ(chunk) - oz;
        return dx * dx + dz * dz;
    }

    /** Takes up to {@link SurveyTiles#MAX_CHUNKS} chunks off the queue into one compressed packet. */
    static SurveyTiles tiles(SurveyData data, LongLinkedOpenHashSet pending) {
        LongArrayList chunks = new LongArrayList();
        List<String> biomes = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        while (!pending.isEmpty() && chunks.size() < SurveyTiles.MAX_CHUNKS) {
            long chunk = pending.removeFirstLong();
            byte[] px = data.colors(chunk);
            if (px == null) continue;
            chunks.add(chunk);
            raw.writeBytes(px);
            String biome = data.biome(chunk);
            int b = biome == null ? -1 : index.computeIfAbsent(biome, k -> {
                biomes.add(k);
                return biomes.size() - 1;
            });
            raw.write(b >> 8);
            raw.write(b);
        }
        return new SurveyTiles(chunks.toLongArray(), deflate(raw.toByteArray()), biomes);
    }

    private static byte[] deflate(byte[] in) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(in);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length / 4 + 64);
        byte[] buf = new byte[8192];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- the view

    private static SurveyView view(MinecraftServer server, ServerPlayer player, Session s, boolean open, Component message) {
        SurveyData data = SurveyData.get(server, s.team, s.dimension);
        int radius = Config.SURVEY_RADIUS_CHUNKS.get();
        int ox = s.origin.getX() >> 4, oz = s.origin.getZ() >> 4;
        int imaged = 0;
        for (long chunk : data.chunks()) {
            if (Math.abs(ChunkPos.getX(chunk) - ox) <= radius && Math.abs(ChunkPos.getZ(chunk) - oz) <= radius) imaged++;
        }
        boolean surveying = surveying(server, s);
        SurveyHeader header = new SurveyHeader(s.dimension.identifier(), s.origin, s.atStation, surveying, radius, imaged,
                SurveyScanner.total(radius));
        List<Row> satellites = new ArrayList<>();
        for (Satellite sat : OrbitRegistry.get(server).over(s.team, s.dimension)) {
            satellites.add(new Row(sat.id(), Component.translatable("message.factoryascent.survey_satellite_row",
                    sat.type().shortName(), sat.name()).withStyle(sat.type().color()), 0, (int) OrbitalText.daysInOrbit(server, sat)));
        }
        return new SurveyView(open, header, status(server, s, surveying), satellites, markers(server, player, s), message);
    }

    /** "Survey-1 · scanning ring 12/48" / "sweep complete" / "no station scanning here". */
    private static Component status(MinecraftServer server, Session s, boolean surveying) {
        if (!surveying) return Component.translatable("message.factoryascent.survey_none").withStyle(ChatFormatting.RED);
        String sat = OrbitRegistry.get(server).first(s.team, s.dimension, SatelliteType.SURVEY).map(Satellite::name).orElse("?");
        ServerLevel level = server.getLevel(s.dimension);
        if (s.atStation && level != null && level.isLoaded(s.origin)
                && level.getBlockEntity(s.origin) instanceof GroundStationBlockEntity station) {
            Component scan = station.pausing() || (station.sweeps() > 0 && station.sweepProgress() >= 1f)
                    ? Component.translatable("message.factoryascent.survey_complete")
                    : Component.translatable(station.sweeps() > 0 ? "message.factoryascent.survey_refreshing" : "message.factoryascent.survey_scanning",
                            station.sweepRing(), Config.SURVEY_RADIUS_CHUNKS.get());
            return Component.translatable("message.factoryascent.survey_status", sat, scan);
        }
        return Component.translatable("message.factoryascent.survey_status", sat,
                Component.translatable("message.factoryascent.survey_remote"));
    }

    private static List<Marker> markers(MinecraftServer server, ServerPlayer viewer, Session s) {
        List<Marker> out = new ArrayList<>();
        FactoryTeams teams = FactoryTeams.get(server);
        ServerLevel level = server.getLevel(s.dimension);
        SurveySites sites = SurveySites.get(server);
        for (SurveySites.Site site : sites.ofTeam(server, s.team, s.dimension)) {
            if (out.size() >= 200) break;
            if (level != null && level.isLoaded(site.pos())) {
                boolean there = site.kind() == SurveySites.STATION ? level.getBlockEntity(site.pos()) instanceof GroundStationBlockEntity
                        : level.getBlockEntity(site.pos()) instanceof LaunchControllerBlockEntity;
                if (!there) {
                    sites.remove(s.dimension, site.pos());
                    continue;
                }
            }
            int kind = site.kind() == SurveySites.PAD ? Marker.PAD
                    : s.atStation && site.pos().equals(s.origin) ? Marker.THIS_STATION : Marker.STATION;
            out.add(new Marker(kind, teams.playerName(site.owner()), site.pos().getX(), site.pos().getZ()));
        }
        if (s.atStation && out.stream().noneMatch(m -> m.kind() == Marker.THIS_STATION)) {
            out.add(new Marker(Marker.THIS_STATION, "", s.origin.getX(), s.origin.getZ()));
        }
        for (UUID member : teams.members(s.team)) {
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p == null || p == viewer || !p.level().dimension().equals(s.dimension)) continue;
            out.add(new Marker(Marker.MEMBER, p.getGameProfile().name(), p.getBlockX(), p.getBlockZ()));
        }
        return out;
    }

    // ---------------------------------------------------------------- buttons

    static void handle(ServerPlayer player, SurveyAction action) {
        Session s = SESSIONS.get(player.getUUID());
        if (action.action() == SurveyAction.CLOSE) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        if (s == null || action.action() != SurveyAction.DEORBIT) return;
        MinecraftServer server = player.level().getServer();
        Component problem = OrbitalConsole.deorbit(server, player.getUUID(), action.id());
        Component message = problem != null ? problem
                : Component.translatable("message.factoryascent.deorbit_done").withStyle(ChatFormatting.GREEN);
        PacketDistributor.sendToPlayer(player, view(server, player, s, false, message));
    }

    static void clear() {
        SESSIONS.clear();
    }

    static void forget(UUID player) {
        SESSIONS.remove(player);
    }
}
