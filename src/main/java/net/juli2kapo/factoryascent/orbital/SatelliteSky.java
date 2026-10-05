package net.juli2kapo.factoryascent.orbital;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Satellites you can see: every client gets the list of satellites over the sky it is under
 * (Earth orbit and the Overworld show the Overworld's, a planet its own), so it can draw them
 * passing overhead (3D models in orbit and on the planets, moving dots in the Overworld's night
 * sky). Over the planets they are out of reach, only drawn in the sky; the Overworld's also fly in
 * Earth orbit as real bodies ({@code satellites.SatelliteBodies}), and orbit draws them where they
 * really are. Your team's satellites come with
 * their names; other teams' stay "unidentified" until one of your team's Orbital Radars has locked
 * them.
 *
 * <p>The list is sent on login, on every change of dimension and respawn, and to everyone
 * whenever a satellite is launched or removed.
 */
public final class SatelliteSky {
    /**
     * One satellite as a client sees it; {@code launchTime} and the spot it circles over
     * ({@code centerX}, {@code centerZ}) place it in Earth orbit ({@code satellites.SatelliteOrbit}).
     */
    public record Entry(UUID id, int type, String name, String owner, boolean own, boolean identified,
                        long launchTime, int centerX, int centerZ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Entry::id,
                ByteBufCodecs.VAR_INT, Entry::type,
                ByteBufCodecs.stringUtf8(64), Entry::name,
                ByteBufCodecs.stringUtf8(64), Entry::owner,
                ByteBufCodecs.BOOL, Entry::own,
                ByteBufCodecs.BOOL, Entry::identified,
                ByteBufCodecs.VAR_LONG, Entry::launchTime,
                ByteBufCodecs.VAR_INT, Entry::centerX,
                ByteBufCodecs.VAR_INT, Entry::centerZ,
                Entry::new);

        public Entry(UUID id, int type, String name, String owner, boolean own, boolean identified) {
            this(id, type, name, owner, own, identified, 0L, 0, 0);
        }

        public SatelliteType satelliteType() {
            SatelliteType[] all = SatelliteType.values();
            return all[Math.floorMod(type, all.length)];
        }
    }

    /**
     * Server → client: the satellites over the sky the player is under ({@code sky} = the player's
     * dimension); {@code altitude} is their orbit height in Earth orbit, 0 when they don't fly there
     * as bodies (config off): then orbit draws them in the sky like the planets do.
     */
    public record Payload(Identifier sky, List<Entry> satellites, int altitude) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "satellite_sky"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Payload::sky,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list(512)), Payload::satellites,
                ByteBufCodecs.VAR_INT, Payload::altitude,
                Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Foreign satellites each team's radars have locked (identified in its members' skies). */
    private static final Map<String, Set<UUID>> LOCKS = new ConcurrentHashMap<>();
    private static int sentChanges = -1;

    private SatelliteSky() {}

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(Payload.TYPE, Payload.STREAM_CODEC));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) sendTo(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) sendTo(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) sendTo(sp);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) -> registerCommand(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            LOCKS.clear();
            sentChanges = -1;
        });
    }

    /** Which satellites a sky shows: Earth orbit looks at the Overworld's; every other sky at its own. */
    public static ResourceKey<Level> satelliteDimension(ResourceKey<Level> sky) {
        return sky.equals(SpaceRules.ORBIT) ? Level.OVERWORLD : sky;
    }

    /** Remembers radar locks (called by the Orbital Radar). */
    public static void noteLocks(String team, java.util.Collection<UUID> ids) {
        Set<UUID> set = LOCKS.computeIfAbsent(team, k -> ConcurrentHashMap.newKeySet());
        if (set.addAll(ids)) sentChanges = -1; // resend: something new is identified
    }

    /**
     * The list a member of {@code team} sees: everything over the dimension, their own satellites
     * named, foreign ones named only if identified (their names and owners blanked otherwise).
     */
    public static List<Entry> entriesFor(List<OrbitRegistry.Owned> satellites, String team, Set<UUID> identified,
                                         Function<String, String> teamName) {
        return entriesFor(satellites, team, identified, teamName, s -> net.minecraft.core.BlockPos.ZERO);
    }

    /** As above, with the spot each satellite circles over in Earth orbit. */
    public static List<Entry> entriesFor(List<OrbitRegistry.Owned> satellites, String team, Set<UUID> identified,
                                         Function<String, String> teamName, Function<Satellite, net.minecraft.core.BlockPos> center) {
        List<Entry> out = new ArrayList<>();
        for (OrbitRegistry.Owned o : satellites) {
            if (out.size() >= 512) break;
            Satellite s = o.satellite();
            boolean own = o.team().equals(team);
            boolean known = own || identified.contains(s.id());
            var c = center.apply(s);
            out.add(new Entry(s.id(), s.type().ordinal(), known ? s.name() : "", known ? teamName.apply(o.team()) : "", own, known,
                    s.launchTime(), c.getX(), c.getZ()));
        }
        return out;
    }

    public static void sendTo(ServerPlayer player) {
        if (player.connection == null || !player.connection.hasChannel(Payload.TYPE)) return;
        MinecraftServer server = player.level().getServer();
        ResourceKey<Level> sky = player.level().dimension();
        FactoryTeams teams = FactoryTeams.get(server);
        String team = teams.teamOf(player.getUUID());
        List<Entry> list = entriesFor(OrbitRegistry.get(server).everyOver(satelliteDimension(sky)), team,
                LOCKS.getOrDefault(team, Set.of()), teams::displayName,
                s -> net.juli2kapo.factoryascent.satellites.SatelliteOrbit.center(server, s));
        int altitude = net.juli2kapo.factoryascent.satellites.SatelliteConfig.physical()
                ? net.juli2kapo.factoryascent.satellites.SatelliteConfig.altitude() : 0;
        PacketDistributor.sendToPlayer(player, new Payload(sky.identifier(), list, altitude));
    }

    /**
     * {@code /factoryascent sky add <survey|uplink|guardian> [count] [foreign]} (operators): puts
     * satellites over the sky you are under without a launch, yours or a rival team's (to try
     * out the sky and the radar); {@code /factoryascent sky list} lists what your sky shows.
     */
    private static void registerCommand(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        var types = com.mojang.brigadier.arguments.StringArgumentType.word();
        dispatcher.register(net.minecraft.commands.Commands.literal("factoryascent").then(net.minecraft.commands.Commands.literal("sky")
                .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                .then(net.minecraft.commands.Commands.literal("add").then(net.minecraft.commands.Commands.argument("type", types)
                        .executes(c -> add(c, 1, false))
                        .then(net.minecraft.commands.Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 32))
                                .executes(c -> add(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count"), false))
                                .then(net.minecraft.commands.Commands.literal("foreign")
                                        .executes(c -> add(c, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count"), true))))))
                .then(net.minecraft.commands.Commands.literal("list").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    var over = OrbitRegistry.get(p.level().getServer()).everyOver(satelliteDimension(p.level().dimension()));
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(over.size() + " satellites over this sky"), false);
                    return over.size();
                }))));
    }

    private static int add(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c, int count, boolean foreign)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String typeName = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type");
        SatelliteType type = switch (typeName) {
            case "survey" -> SatelliteType.SURVEY;
            case "guardian", "defense" -> SatelliteType.DEFENSE;
            default -> SatelliteType.UPLINK;
        };
        MinecraftServer server = p.level().getServer();
        OrbitRegistry orbit = OrbitRegistry.get(server);
        String team = foreign ? FactoryTeams.keyOf("Rivals") : FactoryTeams.get(server).teamOf(p.getUUID());
        ResourceKey<Level> over = satelliteDimension(p.level().dimension());
        for (int i = 0; i < count; i++) {
            int n = orbit.count(team, type) + 1;
            orbit.add(team, new Satellite(type, over, server.overworld().getGameTime(), type.shortName().getString() + "-" + n,
                    foreign ? new UUID(0, 0) : p.getUUID(), over == Level.OVERWORLD ? p.blockPosition() : null));
        }
        c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Added " + count + " " + typeName
                + " satellites" + (foreign ? " (team Rivals)" : "")), true);
        return count;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 0) return;
        int changes = OrbitRegistry.changes();
        if (changes == sentChanges) return;
        sentChanges = changes;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sendTo(p);
    }
}
