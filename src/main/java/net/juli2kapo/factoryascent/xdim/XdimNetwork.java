package net.juli2kapo.factoryascent.xdim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Every channel (name, owner, public or private to the owner's team) and every placed endpoint
 * (where it is, which channel it is tuned to, who placed it, its tier and whether it is keeping
 * its chunk loaded). Saved with the world; the chunk tickets are checked against it on load.
 */
public final class XdimNetwork extends SavedData {
    public static final int MAX_NAME = 24;

    public record Channel(int id, String name, UUID owner, boolean isPublic) {
        static final Codec<Channel> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Channel::id),
                Codec.STRING.fieldOf("name").forGetter(Channel::name),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Channel::owner),
                Codec.BOOL.fieldOf("public").forGetter(Channel::isPublic)
        ).apply(i, Channel::new));
    }

    public record Endpoint(GlobalPos pos, int channel, UUID owner, LinkTier tier, boolean anchored) {
        static final Codec<Endpoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.CODEC.fieldOf("pos").forGetter(Endpoint::pos),
                Codec.INT.fieldOf("channel").forGetter(Endpoint::channel),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Endpoint::owner),
                Codec.STRING.xmap(s -> s.equals("quantum") ? LinkTier.QUANTUM : LinkTier.ENDER, LinkTier::key)
                        .fieldOf("tier").forGetter(Endpoint::tier),
                Codec.BOOL.fieldOf("anchored").forGetter(Endpoint::anchored)
        ).apply(i, Endpoint::new));
    }

    private record Stored(List<Channel> channels, List<Endpoint> endpoints, int nextId) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Channel.CODEC.listOf().fieldOf("channels").forGetter(Stored::channels),
                Endpoint.CODEC.listOf().fieldOf("endpoints").forGetter(Stored::endpoints),
                Codec.INT.fieldOf("next_id").forGetter(Stored::nextId)
        ).apply(i, Stored::new));
    }

    private static final Codec<XdimNetwork> CODEC = Stored.CODEC.xmap(XdimNetwork::new,
            n -> new Stored(List.copyOf(n.channels.values()), List.copyOf(n.endpoints.values()), n.nextId));
    private static final SavedDataType<XdimNetwork> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "xdim_links"), () -> new XdimNetwork(new Stored(List.of(), List.of(), 1)), CODEC);

    private final Map<Integer, Channel> channels = new LinkedHashMap<>();
    private final Map<GlobalPos, Endpoint> endpoints = new LinkedHashMap<>();
    private int nextId;

    private XdimNetwork(Stored stored) {
        stored.channels().forEach(c -> channels.put(c.id(), c));
        stored.endpoints().forEach(e -> endpoints.put(e.pos(), e));
        nextId = Math.max(1, stored.nextId());
    }

    public static XdimNetwork get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // ---------------------------------------------------------------- channels

    public static String cleanName(String name) {
        String s = name.strip();
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
    }

    public Channel create(UUID owner, String name, boolean isPublic) {
        String n = cleanName(name);
        if (n.isEmpty()) n = "Channel " + nextId;
        Channel c = new Channel(nextId++, n, owner, isPublic);
        channels.put(c.id(), c);
        setDirty();
        return c;
    }

    public @Nullable Channel channel(int id) {
        return channels.get(id);
    }

    public void rename(int id, String name) {
        Channel c = channels.get(id);
        String n = cleanName(name);
        if (c == null || n.isEmpty()) return;
        channels.put(id, new Channel(id, n, c.owner(), c.isPublic()));
        setDirty();
    }

    public void setPublic(int id, boolean isPublic) {
        Channel c = channels.get(id);
        if (c == null) return;
        channels.put(id, new Channel(id, c.name(), c.owner(), isPublic));
        setDirty();
    }

    /** Removes a channel; endpoints tuned to it fall silent (they keep the id but find no channel). */
    public void delete(int id) {
        if (channels.remove(id) != null) setDirty();
    }

    /** A player (or an endpoint's owner) may use a channel that is public or belongs to their team. */
    public static boolean mayUse(MinecraftServer server, UUID who, @Nullable Channel channel) {
        return channel != null && (channel.isPublic() || FactoryTeams.get(server).sameTeam(who, channel.owner()));
    }

    /** Only the owner's team may rename, open up or delete a channel. */
    public static boolean mayEdit(MinecraftServer server, UUID who, @Nullable Channel channel) {
        return channel != null && FactoryTeams.get(server).sameTeam(who, channel.owner());
    }

    /** The channels a player can tune to: their team's first, then public ones, by id. */
    public List<Channel> visibleTo(MinecraftServer server, UUID player) {
        List<Channel> own = new ArrayList<>(), open = new ArrayList<>();
        FactoryTeams teams = FactoryTeams.get(server);
        for (Channel c : channels.values()) {
            if (teams.sameTeam(player, c.owner())) own.add(c);
            else if (c.isPublic()) open.add(c);
        }
        own.addAll(open);
        return own;
    }

    // ---------------------------------------------------------------- endpoints

    public @Nullable Endpoint endpoint(GlobalPos pos) {
        return endpoints.get(pos);
    }

    public void put(Endpoint e) {
        if (!e.equals(endpoints.get(e.pos()))) {
            endpoints.put(e.pos(), e);
            setDirty();
        }
    }

    public void remove(GlobalPos pos) {
        if (endpoints.remove(pos) != null) setDirty();
    }

    public List<Endpoint> on(int channel) {
        List<Endpoint> out = new ArrayList<>();
        if (channel <= 0) return out;
        for (Endpoint e : endpoints.values()) if (e.channel() == channel) out.add(e);
        return out;
    }

    public Iterable<Endpoint> endpoints() {
        return endpoints.values();
    }

    /** How many endpoints of the team are keeping their chunk loaded (not counting {@code except}). */
    public int anchoredFor(MinecraftServer server, String teamKey, @Nullable GlobalPos except) {
        FactoryTeams teams = FactoryTeams.get(server);
        int n = 0;
        for (Endpoint e : endpoints.values()) {
            if (e.anchored() && !e.pos().equals(except) && teams.teamOf(e.owner()).equals(teamKey)) n++;
        }
        return n;
    }

    public boolean isAnchored(GlobalPos pos) {
        Endpoint e = endpoints.get(pos);
        return e != null && e.anchored();
    }
}
