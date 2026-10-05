package net.juli2kapo.factoryascent.satellites;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.juli2kapo.factoryascent.orbital.OrbitRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /factoryascent satellites [seconds]} (operators): where each of the Overworld's satellites
 * flies in Earth orbit now (or in that many seconds), with whether its body is out right now.
 */
final class SatelliteCommands {
    private SatelliteCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("factoryascent").then(Commands.literal("satellites")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> list(c.getSource(), 0))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                        .executes(c -> list(c.getSource(), IntegerArgumentType.getInteger(c, "seconds"))))));
    }

    private static int list(CommandSourceStack source, int seconds) {
        MinecraftServer server = source.getServer();
        var all = OrbitRegistry.get(server).everyOver(Level.OVERWORLD);
        long time = server.overworld().getGameTime() + seconds * 20L;
        source.sendSuccess(() -> Component.literal(all.size() + " satellites in Earth orbit"
                + (SatelliteConfig.physical() ? "" : " (physicalSatellites is off)")), false);
        for (OrbitRegistry.Owned o : all) {
            Vec3 p = SatelliteOrbit.position(server, o.satellite(), time);
            var params = SatelliteOrbit.params(o.satellite().id());
            boolean out = SatelliteBodies.body(o.satellite().id()) != null;
            source.sendSuccess(() -> Component.literal(String.format(java.util.Locale.ROOT,
                    "%s [%s]: %.0f %.0f %.0f  r=%.0f lap %ds%s", o.satellite().name(), o.team(), p.x, p.y, p.z,
                    params.radius(), Math.round(params.period() / 20), out ? "  (body out)" : "")), false);
        }
        return all.size();
    }
}
