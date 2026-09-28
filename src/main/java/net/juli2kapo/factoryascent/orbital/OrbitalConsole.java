package net.juli2kapo.factoryascent.orbital;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.Row;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.TeamAction;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.TeamView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Server side of the Team screen (opened by sneak-using a Wireless Terminal in the air or
 * sneak-using a Ground Station with an empty hand): your team, its members, your pending invites,
 * create / invite / join / leave, and your team's satellites with a deorbit button. Everything goes
 * through the same {@link FactoryTeams} rules as {@code /factoryascent team}.
 */
public final class OrbitalConsole {
    /** How many online players the screen offers as invite suggestions. */
    private static final int MAX_CANDIDATES = 8;

    private OrbitalConsole() {}

    /** Opens the Team screen for the player. */
    public static void open(ServerPlayer player) {
        FactoryTeams.get(player.level().getServer()).remember(player);
        PacketDistributor.sendToPlayer(player, view(player, true, Component.empty()));
    }

    /** What the Team screen shows for this player right now. */
    static TeamView view(ServerPlayer player, boolean open, Component message) {
        MinecraftServer server = player.level().getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        String key = teams.teamOf(player.getUUID());
        String teamName = FactoryTeams.isSolo(key) ? "" : teams.displayName(key);
        List<String> members = teams.members(key).stream().map(teams::playerName).toList();
        List<String> invites = teams.invitesOf(player.getUUID()).stream().map(teams::displayName).sorted().toList();
        List<String> candidates = new ArrayList<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (candidates.size() >= MAX_CANDIDATES) break;
            if (other != player && !teams.sameTeam(player.getUUID(), other.getUUID())) {
                candidates.add(other.getGameProfile().name());
            }
        }
        candidates.sort(String.CASE_INSENSITIVE_ORDER);
        List<Row> satellites = new ArrayList<>();
        for (Satellite s : OrbitRegistry.get(server).all(key)) {
            satellites.add(new Row(s.id(), OrbitalText.satelliteLine(s).append(Component.translatable(
                    "message.factoryascent.station_age", OrbitalText.daysInOrbit(server, s)).withStyle(ChatFormatting.DARK_GRAY)), 0, 0));
        }
        return new TeamView(open, teamName, members, invites, candidates, satellites, message);
    }

    /** A Team screen button: act, then refresh the screen with the outcome. */
    static void handle(ServerPlayer player, TeamAction action) {
        Component message = switch (action.action()) {
            case TeamAction.CREATE -> create(player, action.arg());
            case TeamAction.INVITE -> invite(player, action.arg());
            case TeamAction.JOIN -> join(player, action.arg());
            case TeamAction.LEAVE -> leave(player);
            case TeamAction.DEORBIT -> deorbitFromScreen(player, action.arg());
            default -> Component.empty();
        };
        PacketDistributor.sendToPlayer(player, view(player, false, message));
    }

    private static Component outcome(FactoryTeams.Result result, Component success) {
        return result.ok() ? success : Component.translatable(result.key()).withStyle(ChatFormatting.RED);
    }

    private static Component create(ServerPlayer player, String name) {
        FactoryTeams teams = FactoryTeams.get(player.level().getServer());
        teams.remember(player);
        var result = teams.create(player.getUUID(), name.trim());
        String shown = result.ok() ? teams.displayName(teams.teamOf(player.getUUID())) : name;
        return outcome(result, Component.translatable("message.factoryascent.team.created", shown).withStyle(ChatFormatting.GREEN));
    }

    private static Component invite(ServerPlayer player, String name) {
        MinecraftServer server = player.level().getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(name.trim());
        if (target == null) return Component.translatable("message.factoryascent.team.no_such_player", name).withStyle(ChatFormatting.RED);
        FactoryTeams teams = FactoryTeams.get(server);
        teams.remember(player);
        teams.remember(target);
        var result = teams.invite(player.getUUID(), target.getUUID());
        if (result.ok()) {
            String team = teams.displayName(teams.teamOf(player.getUUID()));
            String command = "/factoryascent team join " + team;
            MutableComponent click = Component.literal("[" + command + "]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.SuggestCommand(command))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
            target.sendSystemMessage(Component.translatable("message.factoryascent.team.invited_you",
                    player.getDisplayName(), team, click).withStyle(ChatFormatting.YELLOW));
            target.sendSystemMessage(Component.translatable("message.factoryascent.team.invited_screen").withStyle(ChatFormatting.GRAY));
        }
        return outcome(result, Component.translatable("message.factoryascent.team.invite_sent", target.getDisplayName())
                .withStyle(ChatFormatting.GREEN));
    }

    private static Component join(ServerPlayer player, String name) {
        MinecraftServer server = player.level().getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        teams.remember(player);
        var result = teams.join(player.getUUID(), name.trim());
        if (result.ok()) {
            tellOthers(server, teams.teamOf(player.getUUID()), player.getUUID(),
                    Component.translatable("message.factoryascent.team.member_joined", player.getDisplayName()).withStyle(ChatFormatting.GRAY));
        }
        return outcome(result, Component.translatable("message.factoryascent.team.joined",
                teams.displayName(teams.teamOf(player.getUUID()))).withStyle(ChatFormatting.GREEN));
    }

    private static Component leave(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        String key = teams.teamOf(player.getUUID());
        String name = teams.displayName(key);
        var result = teams.leave(player.getUUID());
        if (result.ok()) {
            tellOthers(server, key, player.getUUID(),
                    Component.translatable("message.factoryascent.team.member_left", player.getDisplayName()).withStyle(ChatFormatting.GRAY));
        }
        return outcome(result, Component.translatable("message.factoryascent.team.left", name).withStyle(ChatFormatting.GREEN));
    }

    private static Component deorbitFromScreen(ServerPlayer player, String id) {
        UUID satellite;
        try {
            satellite = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return Component.empty();
        }
        Component problem = deorbit(player.level().getServer(), player.getUUID(), satellite);
        return problem != null ? problem : Component.translatable("message.factoryascent.deorbit_done").withStyle(ChatFormatting.GREEN);
    }

    /**
     * Brings one of the player's team's satellites down (it burns up on re-entry: nothing is
     * returned) and tells the team. Null on success, else why not.
     */
    public static @Nullable Component deorbit(MinecraftServer server, UUID player, UUID satellite) {
        FactoryTeams teams = FactoryTeams.get(server);
        String team = teams.teamOf(player);
        OrbitRegistry orbit = OrbitRegistry.get(server);
        var found = orbit.find(satellite);
        if (found.isEmpty()) return Component.translatable("message.factoryascent.deorbit_gone").withStyle(ChatFormatting.RED);
        if (!found.get().team().equals(team)) {
            return Component.translatable("message.factoryascent.deorbit_not_yours").withStyle(ChatFormatting.RED);
        }
        orbit.remove(satellite);
        Satellite s = found.get().satellite();
        OrbitalText.tellTeam(server, team, Component.translatable("message.factoryascent.deorbited", teams.playerName(player),
                s.type().displayName(), s.name(), OrbitalText.dimensionName(s.dimension())).withStyle(ChatFormatting.YELLOW));
        return null;
    }

    /** Sends a message to every online member of a team except one. */
    private static void tellOthers(MinecraftServer server, String key, UUID except, Component message) {
        for (UUID member : FactoryTeams.get(server).members(key)) {
            if (member.equals(except)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p != null) p.sendSystemMessage(message);
        }
    }
}
