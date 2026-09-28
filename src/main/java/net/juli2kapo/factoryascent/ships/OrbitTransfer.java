package net.juli2kapo.factoryascent.ships;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Moves a shuttle between the Overworld and {@code factoryascent:orbit} with everyone aboard.
 * Vanilla's cross-dimension teleport already carries passengers and re-mounts them on the new
 * entity; as a safety net every transfer is remembered for two seconds and any passenger found
 * unmounted in that time is put back in its seat (and the passenger list re-sent to clients).
 */
public final class OrbitTransfer {
    /** The orbit dimension (added by the space content; looked up by id). */
    public static final ResourceKey<Level> ORBIT = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "orbit"));

    private record Pending(ResourceKey<Level> level, UUID ship, List<UUID> passengers, int[] ticksLeft) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    private OrbitTransfer() {}

    public static ShipMath.Realm realm(Level level) {
        if (level.dimension() == ORBIT) return ShipMath.Realm.ORBIT;
        if (level.dimension() == Level.OVERWORLD) return ShipMath.Realm.OVERWORLD;
        return ShipMath.Realm.OTHER;
    }

    /** The Overworld's build limit (320 by default): the orbit line is this plus the configured margin. */
    public static int overworldTop(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getMaxY() + 1;
    }

    /**
     * Sends the shuttle and its passengers across. Returns the new entity, or null if the target
     * dimension doesn't exist (the pilot is told) or the move was refused.
     */
    public static @Nullable Entity transfer(Shuttle ship, ShipMath.Transfer transfer) {
        if (!(ship.level() instanceof ServerLevel from) || transfer == ShipMath.Transfer.NONE) return null;
        MinecraftServer server = from.getServer();
        ServerLevel target = server.getLevel(transfer == ShipMath.Transfer.TO_ORBIT ? ORBIT : Level.OVERWORLD);
        if (target == null) {
            var pilot = ship.pilot();
            if (pilot != null && ship.tickCount % 40 == 0) {
                pilot.sendOverlayMessage(Component.translatable("message.factoryascent.shuttle.no_orbit")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
            return null;
        }
        double y = ShipMath.arrivalY(transfer, overworldTop(server), ShipConfig.thresholds());
        Vec3 v = ship.getDeltaMovement();
        Vec3 arrive = transfer == ShipMath.Transfer.TO_ORBIT
                ? new Vec3(v.x * 0.5, 0.05, v.z * 0.5)       // coast into orbit, climbing gently
                : new Vec3(v.x * 0.5, -0.6, v.z * 0.5);      // falling into the atmosphere
        List<UUID> riders = ship.getPassengers().stream().map(Entity::getUUID).toList();
        for (Entity p : ship.getPassengers()) {
            if (p instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable(transfer == ShipMath.Transfer.TO_ORBIT
                        ? "message.factoryascent.shuttle.to_orbit" : "message.factoryascent.shuttle.reentry"));
            }
        }
        TeleportTransition transition = new TeleportTransition(target, new Vec3(ship.getX(), y, ship.getZ()), arrive,
                ship.getYRot(), 0f, TeleportTransition.DO_NOTHING);
        ship.allowDismount = true;
        Entity moved;
        try {
            moved = ship.teleport(transition);
        } finally {
            ship.allowDismount = false;
        }
        if (moved instanceof Shuttle shuttle) {
            shuttle.setDeltaMovement(arrive);
            if (transfer == ShipMath.Transfer.TO_OVERWORLD) shuttle.startReentry();
            if (!riders.isEmpty()) PENDING.add(new Pending(target.dimension(), shuttle.getUUID(), riders, new int[] {40}));
        }
        return moved;
    }

    /** Server tick: puts any passenger that fell out during a transfer back in its seat. */
    public static void tick(MinecraftServer server) {
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (--p.ticksLeft()[0] < 0) {
                it.remove();
                continue;
            }
            ServerLevel level = server.getLevel(p.level());
            Entity ship = level == null ? null : level.getEntity(p.ship());
            if (!(ship instanceof AbstractShip)) continue;
            boolean changed = false;
            for (UUID id : p.passengers()) {
                Entity rider = server.getPlayerList().getPlayer(id);
                if (rider == null) rider = level.getEntity(id);
                if (rider == null || rider.level() != level || rider.getVehicle() == ship || !rider.isAlive()) continue;
                if (rider.getVehicle() != null) continue; // chose another seat
                if (rider.startRiding(ship, true, false)) changed = true;
            }
            if (changed) level.getChunkSource().sendToTrackingPlayersAndSelf(ship, new ClientboundSetPassengersPacket(ship));
        }
    }

    public static void clear() {
        PENDING.clear();
    }
}
