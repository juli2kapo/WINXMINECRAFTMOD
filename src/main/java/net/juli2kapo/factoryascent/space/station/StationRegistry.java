package net.juli2kapo.factoryascent.space.station;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** Every Station Core in the world (overworld data storage): claims for protection and the Star Chart's markers. */
public final class StationRegistry extends SavedData {
    public record Station(ResourceKey<Level> dimension, BlockPos pos, String team, String name) {
        static final Codec<Station> CODEC = RecordCodecBuilder.create(i -> i.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Station::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Station::pos),
                Codec.STRING.fieldOf("team").forGetter(Station::team),
                Codec.STRING.fieldOf("name").forGetter(Station::name)
        ).apply(i, Station::new));
    }

    private static final Codec<StationRegistry> CODEC = Station.CODEC.listOf().xmap(StationRegistry::new, r -> r.stations);
    private static final SavedDataType<StationRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "stations"), () -> new StationRegistry(List.of()), CODEC);

    private final List<Station> stations;

    private StationRegistry(List<Station> stored) {
        this.stations = new ArrayList<>(stored);
    }

    public static StationRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void put(Station station) {
        stations.removeIf(s -> s.dimension().equals(station.dimension()) && s.pos().equals(station.pos()));
        stations.add(station);
        setDirty();
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        if (stations.removeIf(s -> s.dimension().equals(dimension) && s.pos().equals(pos))) setDirty();
    }

    public List<Station> all() {
        return List.copyOf(stations);
    }

    /** The station whose claim (a cube of the given radius around its core) holds this block, if any. */
    public @Nullable Station claiming(ResourceKey<Level> dimension, BlockPos pos, int radius) {
        for (Station s : stations) {
            if (s.dimension().equals(dimension) && Math.abs(s.pos().getX() - pos.getX()) <= radius
                    && Math.abs(s.pos().getY() - pos.getY()) <= radius && Math.abs(s.pos().getZ() - pos.getZ()) <= radius) {
                return s;
            }
        }
        return null;
    }

    /** Opens the Star Chart for a player: every station, the player's team's marked as their own. */
    public static void sendChart(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        String team = teams.teamOf(player.getUUID());
        List<StationPayloads.ChartStation> list = new ArrayList<>();
        for (Station s : get(server).all()) {
            if (list.size() >= 200) break;
            list.add(new StationPayloads.ChartStation(s.name(), teams.displayName(s.team()), s.dimension().identifier(),
                    s.pos().getX(), s.pos().getZ(), s.team().equals(team)));
        }
        PacketDistributor.sendToPlayer(player, new StationPayloads.StarChart(player.level().dimension().identifier(), list));
    }
}
