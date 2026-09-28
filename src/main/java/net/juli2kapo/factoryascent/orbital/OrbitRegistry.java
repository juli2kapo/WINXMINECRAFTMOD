package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    /** A satellite together with the team that owns it. */
    public record Owned(String team, Satellite satellite) {}

    private final Map<String, List<Satellite>> byTeam = new HashMap<>();

    private OrbitRegistry(Map<String, List<Satellite>> stored) {
        stored.forEach((team, list) -> byTeam.put(team, new ArrayList<>(list)));
        // Old saves get their satellite ids on load: save them so they stay the same.
        if (!byTeam.isEmpty()) setDirty();
    }

    public static OrbitRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public void add(String team, Satellite satellite) {
        byTeam.computeIfAbsent(team, k -> new ArrayList<>()).add(satellite);
        setDirty();
    }

    /** Takes a satellite out of orbit; the removed satellite and its team, if it was there. */
    public Optional<Owned> remove(UUID id) {
        for (var entry : byTeam.entrySet()) {
            for (var it = entry.getValue().iterator(); it.hasNext(); ) {
                Satellite s = it.next();
                if (s.id().equals(id)) {
                    it.remove();
                    setDirty();
                    return Optional.of(new Owned(entry.getKey(), s));
                }
            }
        }
        return Optional.empty();
    }

    /** A satellite by id, with its team. */
    public Optional<Owned> find(UUID id) {
        for (var entry : byTeam.entrySet()) {
            for (Satellite s : entry.getValue()) {
                if (s.id().equals(id)) return Optional.of(new Owned(entry.getKey(), s));
            }
        }
        return Optional.empty();
    }

    public List<Satellite> all(String team) {
        return List.copyOf(byTeam.getOrDefault(team, List.of()));
    }

    /** The team's satellites over one dimension, oldest first. */
    public List<Satellite> over(String team, ResourceKey<Level> dimension) {
        return byTeam.getOrDefault(team, List.of()).stream().filter(s -> s.dimension().equals(dimension)).toList();
    }

    /** Every team's satellites over one dimension (what a radar there sees), oldest first. */
    public List<Owned> everyOver(ResourceKey<Level> dimension) {
        List<Owned> out = new ArrayList<>();
        byTeam.forEach((team, list) -> list.stream().filter(s -> s.dimension().equals(dimension))
                .forEach(s -> out.add(new Owned(team, s))));
        out.sort(java.util.Comparator.comparingLong(o -> o.satellite().launchTime()));
        return out;
    }

    /** The team's oldest satellite of this type over the dimension, if any. */
    public Optional<Satellite> first(String team, ResourceKey<Level> dimension, SatelliteType type) {
        return byTeam.getOrDefault(team, List.of()).stream()
                .filter(s -> s.type() == type && s.dimension().equals(dimension)).findFirst();
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
