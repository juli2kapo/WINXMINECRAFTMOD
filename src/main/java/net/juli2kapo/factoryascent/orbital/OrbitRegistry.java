package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every satellite in orbit, by team key (see {@link FactoryTeams}). Stored in the overworld's data storage. */
public final class OrbitRegistry extends SavedData {
    private static final Codec<OrbitRegistry> CODEC = Codec.unboundedMap(Codec.STRING, Satellite.CODEC.listOf())
            .xmap(OrbitRegistry::new, r -> r.byTeam);
    private static final SavedDataType<OrbitRegistry> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "orbit"), () -> new OrbitRegistry(Map.of()), CODEC);

    private final Map<String, List<Satellite>> byTeam = new HashMap<>();

    private OrbitRegistry(Map<String, List<Satellite>> stored) {
        stored.forEach((team, list) -> byTeam.put(team, new ArrayList<>(list)));
    }

    public static OrbitRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void add(String team, Satellite satellite) {
        byTeam.computeIfAbsent(team, k -> new ArrayList<>()).add(satellite);
        setDirty();
    }

    public List<Satellite> all(String team) {
        return List.copyOf(byTeam.getOrDefault(team, List.of()));
    }

    /** The team's satellites over one dimension, oldest first. */
    public List<Satellite> over(String team, ResourceKey<Level> dimension) {
        return byTeam.getOrDefault(team, List.of()).stream().filter(s -> s.dimension().equals(dimension)).toList();
    }

    public boolean has(String team, ResourceKey<Level> dimension, SatelliteType type) {
        return byTeam.getOrDefault(team, List.of()).stream()
                .anyMatch(s -> s.type() == type && s.dimension().equals(dimension));
    }

    /** How many satellites of this type the team has launched (anywhere), for automatic names. */
    public int count(String team, SatelliteType type) {
        return (int) byTeam.getOrDefault(team, List.of()).stream().filter(s -> s.type() == type).count();
    }
}
