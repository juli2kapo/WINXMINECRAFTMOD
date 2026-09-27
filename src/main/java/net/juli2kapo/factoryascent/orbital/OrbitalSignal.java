package net.juli2kapo.factoryascent.orbital;

import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The public signal API for devices that work "over the uplink": the Wireless Terminal today,
 * the phone and its apps later. Server side only; cheap enough to call every tick.
 *
 * <p>A team has coverage in a dimension while it has an Uplink Satellite in orbit over it.
 */
public final class OrbitalSignal {
    private OrbitalSignal() {}

    /** Does the player's team have uplink coverage where the player is now? */
    public static boolean hasCoverage(ServerPlayer player) {
        return hasCoverage(player.level().getServer(), player.getUUID(), player.level().dimension());
    }

    /** Does this player's team have uplink coverage in that dimension? */
    public static boolean hasCoverage(MinecraftServer server, UUID player, ResourceKey<Level> dimension) {
        return has(server, player, dimension, SatelliteType.UPLINK);
    }

    /** Does the player's team have a satellite of this type over that dimension? */
    public static boolean has(MinecraftServer server, UUID player, ResourceKey<Level> dimension, SatelliteType type) {
        String team = FactoryTeams.get(server).teamOf(player);
        return OrbitRegistry.get(server).has(team, dimension, type);
    }

    /** Survey coverage: the Ground Station can map. */
    public static boolean hasSurvey(ServerPlayer player) {
        return has(player.level().getServer(), player.getUUID(), player.level().dimension(), SatelliteType.SURVEY);
    }
}
