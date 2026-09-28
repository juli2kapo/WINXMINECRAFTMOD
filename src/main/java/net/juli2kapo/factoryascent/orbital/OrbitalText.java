package net.juli2kapo.factoryascent.orbital;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Shared chat text of the orbital age. */
final class OrbitalText {
    private OrbitalText() {}

    /** "the Overworld", "the Nether", or the dimension id for anything without a translation. */
    static Component dimensionName(ResourceKey<Level> dimension) {
        var id = dimension.identifier();
        return Component.translatableWithFallback("orbital.factoryascent.dimension." + id.getNamespace() + "." + id.getPath(),
                id.toString());
    }

    /** "• Uplink Satellite 'Uplink-1' over the Overworld" */
    static MutableComponent satelliteLine(Satellite s) {
        return Component.translatable("message.factoryascent.satellite_line", s.type().displayName(), s.name(),
                dimensionName(s.dimension())).withStyle(s.type().color());
    }

    /** "3 days in orbit" suffix: whole Minecraft days since the launch. */
    static long daysInOrbit(MinecraftServer server, Satellite s) {
        return Math.max(0, server.overworld().getGameTime() - s.launchTime()) / 24000L;
    }

    /** Sends a message to every online member of a team. */
    static void tellTeam(MinecraftServer server, String team, Component message) {
        for (UUID member : FactoryTeams.get(server).members(team)) {
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p != null) p.sendSystemMessage(message);
        }
    }
}
