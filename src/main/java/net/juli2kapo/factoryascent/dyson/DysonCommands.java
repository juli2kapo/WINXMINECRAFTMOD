package net.juli2kapo.factoryascent.dyson;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.juli2kapo.factoryascent.orbital.FactoryTeams;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /factoryascent dyson info} (anyone: your team's swarm) and
 * {@code /factoryascent dyson set|add <collectors>} (operators: change your team's swarm, for
 * testing and map making).
 */
final class DysonCommands {
    private DysonCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("factoryascent").then(Commands.literal("dyson")
                .then(Commands.literal("info").executes(DysonCommands::info))
                .then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("collectors", IntegerArgumentType.integer(0))
                                .executes(c -> set(c, IntegerArgumentType.getInteger(c, "collectors"), false))))
                .then(Commands.literal("add").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("collectors", IntegerArgumentType.integer(1))
                                .executes(c -> set(c, IntegerArgumentType.getInteger(c, "collectors"), true))))));
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        var server = c.getSource().getServer();
        String team = DysonService.teamOf(server, player.getUUID());
        DysonSwarm swarm = DysonSwarm.get(server);
        long n = swarm.collectors(team);
        c.getSource().sendSuccess(() -> Component.translatable("message.factoryascent.dyson.info",
                FactoryTeams.get(server).displayName(team), n, DysonSwarm.target(),
                String.format("%.1f", swarm.completion(team) * 100), swarm.swarmPower(team)), false);
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    private static int set(CommandContext<CommandSourceStack> c, int count, boolean add) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        var server = c.getSource().getServer();
        String team = DysonService.teamOf(server, player.getUUID());
        if (add) {
            DysonService.addCollectors(server, team, count);
        } else {
            DysonService.setCollectors(server, team, count);
        }
        return info(c);
    }
}
