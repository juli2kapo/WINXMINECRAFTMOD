package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Factory Ascent teams (independent of vanilla scoreboard teams). Every player is implicitly on
 * a solo team whose key is their UUID string until they create or join a named team, whose key
 * is {@code "team:" + lowercase name}. Satellites (see {@link OrbitRegistry}) belong to team keys,
 * so a team's satellites stay with it when members come and go.
 *
 * <p>Stored in the overworld's data storage. Pending invites are not saved: they last until the
 * server stops.
 */
public final class FactoryTeams extends SavedData {
    public static final String NAMED_PREFIX = "team:";
    public static final int MAX_NAME = 24;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,24}");

    /** A named team. {@code name} keeps the capitalisation it was created with. */
    public static final class Team {
        final String name;
        final LinkedHashSet<UUID> members;

        Team(String name, List<UUID> members) {
            this.name = name;
            this.members = new LinkedHashSet<>(members);
        }

        public String name() {
            return name;
        }

        public Set<UUID> members() {
            return java.util.Collections.unmodifiableSet(members);
        }

        static final Codec<Team> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(t -> t.name),
                UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(t -> List.copyOf(t.members))
        ).apply(i, Team::new));
    }

    private record Stored(Map<String, Team> teams, Map<UUID, String> names) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Team.CODEC).fieldOf("teams").forGetter(Stored::teams),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING).optionalFieldOf("names", Map.of()).forGetter(Stored::names)
        ).apply(i, Stored::new));
    }

    private static final Codec<FactoryTeams> CODEC = Stored.CODEC.xmap(
            s -> new FactoryTeams(s.teams(), s.names()), t -> new Stored(t.teams, t.names));
    private static final SavedDataType<FactoryTeams> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "teams"), () -> new FactoryTeams(Map.of(), Map.of()), CODEC);

    /** Team key → team (named teams only). */
    private final Map<String, Team> teams;
    /** Last known player names, for team info when members are offline. */
    private final Map<UUID, String> names;
    /** Player → the only named team they belong to (rebuilt from {@link #teams}). */
    private final Map<UUID, String> membership = new HashMap<>();
    /** Invitee → team keys they were invited to (not saved). */
    private final Map<UUID, Set<String>> invites = new HashMap<>();

    private FactoryTeams(Map<String, Team> teams, Map<UUID, String> names) {
        this.teams = new LinkedHashMap<>(teams);
        this.names = new HashMap<>(names);
        this.teams.forEach((key, team) -> team.members.forEach(m -> membership.put(m, key)));
    }

    public static FactoryTeams get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Result of a team operation: ok, or the translation key suffix of what went wrong. */
    public enum Result {
        OK, BAD_NAME, NAME_TAKEN, ALREADY_IN_TEAM, NOT_IN_TEAM, NO_SUCH_TEAM, NOT_INVITED, SELF, TARGET_IN_TEAM;

        public boolean ok() {
            return this == OK;
        }

        public String key() {
            return "message.factoryascent.team." + name().toLowerCase(Locale.ROOT);
        }
    }

    // ---------------------------------------------------------------- queries

    public static String keyOf(String name) {
        return NAMED_PREFIX + name.toLowerCase(Locale.ROOT);
    }

    public static String soloKey(UUID player) {
        return player.toString();
    }

    public static boolean isSolo(String teamKey) {
        return !teamKey.startsWith(NAMED_PREFIX);
    }

    /** The team a player is on: a named team's key, or their solo key. */
    public String teamOf(UUID player) {
        return membership.getOrDefault(player, soloKey(player));
    }

    public @Nullable Team team(String key) {
        return teams.get(key);
    }

    /** Members of a team key: the named team's members, or just the player for a solo key. */
    public Set<UUID> members(String key) {
        Team team = teams.get(key);
        if (team != null) return team.members();
        if (isSolo(key)) {
            try {
                return Set.of(UUID.fromString(key));
            } catch (IllegalArgumentException e) {
                return Set.of();
            }
        }
        return Set.of();
    }

    public boolean sameTeam(UUID a, UUID b) {
        return teamOf(a).equals(teamOf(b));
    }

    /** What to call a team in messages: the named team's name, or the player's name for a solo team. */
    public String displayName(String key) {
        Team team = teams.get(key);
        if (team != null) return team.name;
        try {
            return names.getOrDefault(UUID.fromString(key), key);
        } catch (IllegalArgumentException e) {
            return key;
        }
    }

    public String playerName(UUID player) {
        return names.getOrDefault(player, player.toString().substring(0, 8));
    }

    public List<Team> namedTeams() {
        return new ArrayList<>(teams.values());
    }

    public boolean isInvited(UUID player, String teamKey) {
        return invites.getOrDefault(player, Set.of()).contains(teamKey);
    }

    public Set<String> invitesOf(UUID player) {
        return java.util.Collections.unmodifiableSet(invites.getOrDefault(player, Set.of()));
    }

    // ---------------------------------------------------------------- changes

    /** Remembers the player's name (shown in team info when they are offline). */
    public void remember(ServerPlayer player) {
        String name = player.getGameProfile().name();
        if (!name.equals(names.put(player.getUUID(), name))) setDirty();
    }

    /**
     * Founds a named team with the player as its first member. A name whose team has no members
     * left can be founded again: it keeps that team's satellites.
     */
    public Result create(UUID founder, String name) {
        if (!NAME.matcher(name).matches()) return Result.BAD_NAME;
        if (membership.containsKey(founder)) return Result.ALREADY_IN_TEAM;
        String key = keyOf(name);
        Team existing = teams.get(key);
        if (existing != null && !existing.members.isEmpty()) return Result.NAME_TAKEN;
        Team team = new Team(existing != null ? existing.name : name, List.of(founder));
        teams.put(key, team);
        membership.put(founder, key);
        invites.remove(founder);
        setDirty();
        return Result.OK;
    }

    /** A member invites someone who isn't on a named team yet. */
    public Result invite(UUID inviter, UUID target) {
        String key = membership.get(inviter);
        if (key == null) return Result.NOT_IN_TEAM;
        if (inviter.equals(target)) return Result.SELF;
        if (key.equals(membership.get(target))) return Result.TARGET_IN_TEAM;
        invites.computeIfAbsent(target, k -> new HashSet<>()).add(key);
        return Result.OK;
    }

    /** Joins a named team; needs a pending invite and no current team. */
    public Result join(UUID player, String name) {
        String key = keyOf(name);
        if (!teams.containsKey(key)) return Result.NO_SUCH_TEAM;
        if (membership.containsKey(player)) return Result.ALREADY_IN_TEAM;
        if (!isInvited(player, key)) return Result.NOT_INVITED;
        teams.get(key).members.add(player);
        membership.put(player, key);
        invites.remove(player);
        setDirty();
        return Result.OK;
    }

    /** Leaves the named team; the player is back on their solo team. The team keeps its satellites. */
    public Result leave(UUID player) {
        String key = membership.remove(player);
        if (key == null) return Result.NOT_IN_TEAM;
        Team team = teams.get(key);
        if (team != null) team.members.remove(player);
        setDirty();
        return Result.OK;
    }

    public Optional<Team> teamOfPlayer(UUID player) {
        String key = membership.get(player);
        return key == null ? Optional.empty() : Optional.ofNullable(teams.get(key));
    }
}
