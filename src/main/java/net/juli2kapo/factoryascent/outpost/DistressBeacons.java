package net.juli2kapo.factoryascent.outpost;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.space.planet.Planet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Every lit Distress Beacon (overworld data storage), by team: what a teammate's shuttle can home
 * in on from its Navigation panel. The list logic ({@link #forTeam}, {@link #next},
 * {@link #landingSpot}) is pure so game tests can check it.
 */
public final class DistressBeacons extends SavedData {
    /** A lit beacon: where it is, whose team it calls and who lit it. {@code lit} orders them (oldest first). */
    public record Beacon(ResourceKey<Level> dimension, BlockPos pos, String team, String owner, long lit) {
        static final Codec<Beacon> CODEC = RecordCodecBuilder.create(i -> i.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Beacon::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Beacon::pos),
                Codec.STRING.fieldOf("team").forGetter(Beacon::team),
                Codec.STRING.fieldOf("owner").forGetter(Beacon::owner),
                Codec.LONG.optionalFieldOf("lit", 0L).forGetter(Beacon::lit)
        ).apply(i, Beacon::new));

        public GlobalPos global() {
            return GlobalPos.of(dimension, pos);
        }

        public @Nullable Planet planet() {
            return Planet.of(dimension);
        }
    }

    private static final Codec<DistressBeacons> CODEC = Beacon.CODEC.listOf().xmap(DistressBeacons::new, r -> r.beacons);
    private static final SavedDataType<DistressBeacons> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "distress_beacons"), () -> new DistressBeacons(List.of()), CODEC);

    private final List<Beacon> beacons;

    private DistressBeacons(List<Beacon> stored) {
        this.beacons = new ArrayList<>(stored);
    }

    public static DistressBeacons get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Lights a beacon (replacing one already at that spot). A team over its limit loses its oldest
     * beacon; returns that one, if any, so the caller can tell the team.
     */
    public @Nullable Beacon light(Beacon beacon, int perTeam) {
        beacons.removeIf(b -> b.dimension().equals(beacon.dimension()) && b.pos().equals(beacon.pos()));
        beacons.add(beacon);
        Beacon dropped = null;
        List<Beacon> mine = forTeam(beacons, beacon.team());
        if (mine.size() > perTeam) {
            dropped = mine.getFirst();
            beacons.remove(dropped);
        }
        setDirty();
        return dropped;
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        if (beacons.removeIf(b -> b.dimension().equals(dimension) && b.pos().equals(pos))) setDirty();
    }

    public @Nullable Beacon at(GlobalPos pos) {
        for (Beacon b : beacons) {
            if (b.dimension().equals(pos.dimension()) && b.pos().equals(pos.pos())) return b;
        }
        return null;
    }

    public List<Beacon> all() {
        return List.copyOf(beacons);
    }

    /** The beacons a team's shuttles can fly to: theirs, on a planet, oldest first. */
    public List<Beacon> forTeam(String team) {
        return forTeam(beacons, team);
    }

    // ---------------------------------------------------------------- pure rules

    /** A team's beacons on planets (a beacon anywhere else calls nobody), oldest first, then by place. */
    public static List<Beacon> forTeam(List<Beacon> all, String team) {
        List<Beacon> out = new ArrayList<>();
        for (Beacon b : all) {
            if (b.team().equals(team) && Planet.of(b.dimension()) != null) out.add(b);
        }
        out.sort(Comparator.comparingLong(Beacon::lit).thenComparing(b -> b.dimension().identifier().toString())
                .thenComparingLong(b -> b.pos().asLong()));
        return out;
    }

    /** The beacon after {@code current} in the list (wrapping), or the first one; null for an empty list. */
    public static @Nullable Beacon next(List<Beacon> list, @Nullable GlobalPos current) {
        if (list.isEmpty()) return null;
        if (current != null) {
            for (int i = 0; i < list.size(); i++) {
                Beacon b = list.get(i);
                if (b.dimension().equals(current.dimension()) && b.pos().equals(current.pos())) {
                    return list.get((i + 1) % list.size());
                }
            }
        }
        return list.getFirst();
    }

    /** 1-based position of {@code current} in the list, 0 if it isn't there. */
    public static int indexOf(List<Beacon> list, @Nullable GlobalPos current) {
        if (current == null) return 0;
        for (int i = 0; i < list.size(); i++) {
            Beacon b = list.get(i);
            if (b.dimension().equals(current.dimension()) && b.pos().equals(current.pos())) return i + 1;
        }
        return 0;
    }

    /** Where a rescue shuttle comes down: a few blocks east of the beacon, clear of it. */
    public static BlockPos landingSpot(BlockPos beacon) {
        return beacon.offset(4, 0, 0);
    }
}
