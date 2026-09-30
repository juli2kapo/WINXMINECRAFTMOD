package net.juli2kapo.factoryascent.dyson;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Every team's Dyson Sphere project, by team key (see
 * {@link net.juli2kapo.factoryascent.orbital.FactoryTeams}): how many Solar Collectors the team
 * has in solar orbit, how many it ever launched, and which milestones it has announced. Stored in
 * the overworld's data storage.
 *
 * <p>Also keeps (not saved) how much of each team's swarm power the team's Dyson Receivers took
 * this tick and in the last second: the swarm's output is one budget shared by all receivers.
 */
public final class DysonSwarm extends SavedData {
    /** One team's project. Mutable; saved through {@link #CODEC}. */
    public static final class Project {
        long collectors;
        long launched;
        int milestones;

        Project(long collectors, long launched, int milestones) {
            this.collectors = collectors;
            this.launched = launched;
            this.milestones = milestones;
        }

        public long collectors() {
            return collectors;
        }

        /** Collectors ever launched (shots that reached the swarm). */
        public long launched() {
            return launched;
        }

        /** Bit {@code i} set once milestone {@code i} of {@link DysonService#MILESTONES} was announced. */
        public int milestones() {
            return milestones;
        }

        static final Codec<Project> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("collectors").forGetter(p -> p.collectors),
                Codec.LONG.optionalFieldOf("launched", 0L).forGetter(p -> p.launched),
                Codec.INT.optionalFieldOf("milestones", 0).forGetter(p -> p.milestones)
        ).apply(i, Project::new));
    }

    static final Codec<DysonSwarm> CODEC = Codec.unboundedMap(Codec.STRING, Project.CODEC)
            .xmap(DysonSwarm::new, s -> s.projects);
    private static final SavedDataType<DysonSwarm> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "dyson_swarm"), () -> new DysonSwarm(Map.of()), CODEC);

    private final Map<String, Project> projects = new HashMap<>();
    /** Swarm power taken by receivers: team → FE this tick, and the tick it was counted in. */
    private final Map<String, long[]> budget = new HashMap<>();
    /** Team → {FE taken during the current second, FE taken during the last full second, second index}. */
    private final Map<String, long[]> output = new HashMap<>();

    private DysonSwarm(Map<String, Project> stored) {
        stored.forEach((team, p) -> projects.put(team, new Project(p.collectors, p.launched, p.milestones)));
    }

    public static DysonSwarm get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Solar Collectors a complete sphere needs (config). */
    public static int target() {
        return DysonConfig.SWARM_TARGET.get();
    }

    public Project project(String team) {
        return projects.computeIfAbsent(team, k -> new Project(0, 0, 0));
    }

    public long collectors(String team) {
        Project p = projects.get(team);
        return p == null ? 0 : p.collectors;
    }

    /** 0..1 of a complete sphere. */
    public double completion(String team) {
        return Math.min(1.0, collectors(team) / (double) target());
    }

    public boolean isComplete(String team) {
        return collectors(team) >= target();
    }

    /** Adds launched collectors (never beyond the target); returns how many actually joined the swarm. */
    long add(String team, long count) {
        Project p = project(team);
        long room = Math.max(0, target() - p.collectors);
        long added = Math.min(room, Math.max(0, count));
        if (added > 0) {
            p.collectors += added;
            p.launched += added;
            setDirty();
        }
        return added;
    }

    /** Sets the team's collector count directly (admin command / tests). */
    void set(String team, long count) {
        Project p = project(team);
        p.collectors = Math.max(0, Math.min(count, target()));
        setDirty();
    }

    void markMilestone(String team, int index) {
        project(team).milestones |= 1 << index;
        setDirty();
    }

    void clearMilestonesAbove(String team) {
        Project p = project(team);
        int before = p.milestones;
        for (int i = 0; i < DysonService.MILESTONES.length; i++) {
            if (!DysonService.reached(i, p.collectors)) p.milestones &= ~(1 << i);
        }
        if (p.milestones != before) setDirty();
    }

    // ---------------------------------------------------------------- the shared power budget (not saved)

    /** FE per tick the team's whole swarm beams down in full sunlight. */
    public long swarmPower(String team) {
        return collectors(team) * (long) DysonConfig.FE_PER_COLLECTOR.get();
    }

    /**
     * A receiver asks for {@code want} FE this tick; returns how much of the team's swarm budget is
     * left for it (the budget is {@code swarmPower × exposure of the asking receiver}, shared).
     */
    long take(String team, long gameTime, long budgetThisTick, long want) {
        long[] b = budget.computeIfAbsent(team, k -> new long[] {0, gameTime});
        if (b[1] != gameTime) {
            b[0] = 0;
            b[1] = gameTime;
        }
        long got = Math.max(0, Math.min(want, budgetThisTick - b[0]));
        b[0] += got;
        long[] o = output.computeIfAbsent(team, k -> new long[] {0, 0, gameTime / 20});
        roll(o, gameTime);
        o[0] += got;
        return got;
    }

    private static void roll(long[] o, long gameTime) {
        long second = gameTime / 20;
        if (o[2] != second) {
            o[1] = o[2] == second - 1 ? o[0] : 0;
            o[0] = 0;
            o[2] = second;
        }
    }

    /** Average FE per tick the team's receivers took during the last full second. */
    public long receivedPerTick(String team, long gameTime) {
        long[] o = output.get(team);
        if (o == null) return 0;
        roll(o, gameTime);
        return o[1] / 20;
    }
}
