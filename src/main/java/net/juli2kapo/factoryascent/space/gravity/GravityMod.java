package net.juli2kapo.factoryascent.space.gravity;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/** Entry point of the turned-gravity feature (Magnetic Boots): packets and the server's rules. */
@Mod(FactoryAscent.MOD_ID)
public final class GravityMod {
    public GravityMod(IEventBus modBus) {
        modBus.addListener(GravityPayloads::register);
        NeoForge.EVENT_BUS.addListener(Gravity::onServerTick);
        NeoForge.EVENT_BUS.addListener(Gravity::onLogin);
        NeoForge.EVENT_BUS.addListener(Gravity::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(Gravity::onStartTracking);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityTeleportEvent.TeleportCommand e) -> Gravity.onTeleport(e));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityTeleportEvent.SpreadPlayersCommand e) -> Gravity.onTeleport(e));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityTeleportEvent.EnderPearl e) -> Gravity.onTeleport(e));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityTeleportEvent.ItemConsumption e) -> Gravity.onTeleport(e));
    }
}
