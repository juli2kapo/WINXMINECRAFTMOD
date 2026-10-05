package net.juli2kapo.factoryascent.satellites;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Entry point of the satellites flying in Earth orbit (a further {@code @Mod} class, like the ships' one). */
@Mod(FactoryAscent.MOD_ID)
public final class SatellitesMod {
    public SatellitesMod(IEventBus modBus, ModContainer container) {
        SatellitesContent.register(modBus, container);
    }
}
