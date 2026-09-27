package net.juli2kapo.factoryascent.orbital;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /factoryascent team create|invite|join|leave|info|list}. Open to every player: a team
 * only decides who shares satellites (and their signal).
 */
final class TeamCommands {
    private TeamCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("factoryascent").then(Commands.literal("team")
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> create(c, StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> invite(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> {
                            if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return b.buildFuture();
                            FactoryTeams teams = FactoryTeams.get(c.getSource().getServer());
                            return SharedSuggestionProvider.suggest(teams.invitesOf(p.getUUID()).stream()
                                    .map(k -> teams.displayName(k)), b);
                        })
                        .executes(c -> join(c, StringArgumentType.getString(c, "name")))))
                .then(Commands.literal("leave").executes(TeamCommands::leave))
                .then(Commands.literal("info").executes(TeamCommands::info))
                .then(Commands.literal("list").executes(TeamCommands::list))));
    }

    private static FactoryTeams teams(CommandContext<CommandSourceStack> c) {
        return FactoryTeams.get(c.getSource().getServer());
    }

    private static int report(CommandContext<CommandSourceStack> c, FactoryTeams.Result result, Component success) {
        if (result.ok()) {
            c.getSource().sendSuccess(() -> success, false);
            return 1;
        }
        c.getSource().sendFailure(Component.translatable(result.key()));
        return 0;
    }

    private static int create(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        FactoryTeams teams = teams(c);
        teams.remember(player);
        var result = teams.create(player.getUUID(), name);
        String shown = result.ok() ? teams.displayName(teams.teamOf(player.getUUID())) : name;
        return report(c, result, Component.translatable("message.factoryascent.team.created", shown).withStyle(ChatFormatting.GREEN));
    }

    private static int invite(CommandContext<CommandSourceStack> c, ServerPlayer target) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        FactoryTeams teams = teams(c);
        teams.remember(player);
        teams.remember(target);
        var result = teams.invite(player.getUUID(), target.getUUID());
        if (result.ok()) {
            String name = teams.displayName(teams.teamOf(player.getUUID()));
            String command = "/factoryascent team join " + name;
            MutableComponent click = Component.literal("[" + command + "]").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.SuggestCommand(command))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
            target.sendSystemMessage(Component.translatable("message.factoryascent.team.invited_you",
                    player.getDisplayName(), name, click).withStyle(ChatFormatting.YELLOW));
        }
        return report(c, result, Component.translatable("message.factoryascent.team.invite_sent", target.getDisplayName()));
    }

    private static int join(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        FactoryTeams teams = teams(c);
        teams.remember(player);
        var result = teams.join(player.getUUID(), name);
        if (result.ok()) {
            String key = teams.teamOf(player.getUUID());
            tell(c.getSource().getServer(), key, player.getUUID(),
                    Component.translatable("message.factoryascent.team.member_joined", player.getDisplayName()).withStyle(ChatFormatting.GRAY));
        }
        return report(c, result, Component.translatable("message.factoryascent.team.joined",
                teams.displayName(teams.teamOf(player.getUUID()))).withStyle(ChatFormatting.GREEN));
    }

    private static int leave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        FactoryTeams teams = teams(c);
        String key = teams.teamOf(player.getUUID());
        String name = teams.displayName(key);
        var result = teams.leave(player.getUUID());
        if (result.ok()) {
            tell(c.getSource().getServer(), key, player.getUUID(),
                    Component.translatable("message.factoryascent.team.member_left", player.getDisplayName()).withStyle(ChatFormatting.GRAY));
        }
        return report(c, result, Component.translatable("message.factoryascent.team.left", name));
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        FactoryTeams teams = teams(c);
        teams.remember(player);
        String key = teams.teamOf(player.getUUID());
        CommandSourceStack src = c.getSource();
        if (FactoryTeams.isSolo(key)) {
            src.sendSuccess(() -> Component.translatable("message.factoryascent.team.info_solo").withStyle(ChatFormatting.GOLD), false);
        } else {
            String members = teams.members(key).stream().map(teams::playerName).collect(Collectors.joining(", "));
            src.sendSuccess(() -> Component.translatable("message.factoryascent.team.info", teams.displayName(key), members)
                    .withStyle(ChatFormatting.GOLD), false);
        }
        List<Satellite> sats = OrbitRegistry.get(src.getServer()).all(key);
        src.sendSuccess(() -> Component.translatable("message.factoryascent.team.info_satellites", sats.size()), false);
        for (Satellite s : sats) src.sendSuccess(() -> OrbitalText.satelliteLine(s), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        FactoryTeams teams = teams(c);
        var all = teams.namedTeams();
        if (all.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("message.factoryascent.team.none"), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("message.factoryascent.team.list_header", all.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (var team : all) {
            String members = team.members().stream().map(teams::playerName).collect(Collectors.joining(", "));
            c.getSource().sendSuccess(() -> Component.translatable("message.factoryascent.team.list_entry",
                    team.name(), team.members().size(), members), false);
        }
        return all.size();
    }

    /** Sends a message to every online member of a team except one. */
    private static void tell(net.minecraft.server.MinecraftServer server, String key, UUID except, Component message) {
        for (UUID member : FactoryTeams.get(server).members(key)) {
            if (member.equals(except)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p != null) p.sendSystemMessage(message);
        }
    }
}
