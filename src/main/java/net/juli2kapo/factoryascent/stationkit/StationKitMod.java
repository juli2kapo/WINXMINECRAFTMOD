package net.juli2kapo.factoryascent.stationkit;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Getting stations built in orbit (a further {@code @Mod} class of Factory Ascent): the Station Kit
 * and the Cargo Pod (Launch Pad payloads, see {@link StationKits}) and the crew pod a capsule
 * leaves floating in orbit when there is no station to land on ({@link CrewPods}).
 */
@Mod(FactoryAscent.MOD_ID)
public final class StationKitMod {
    public StationKitMod(IEventBus modBus) {
        StationKitContent.register(modBus);
    }
}
