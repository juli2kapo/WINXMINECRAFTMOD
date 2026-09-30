package net.juli2kapo.factoryascent.dyson;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The Dyson Cube's own entry point (a further {@code @Mod} class of Factory Ascent, like the
 * ships' one), so the megaproject plugs in without touching the main mod class.
 */
@Mod(FactoryAscent.MOD_ID)
public final class DysonMod {
    public DysonMod(IEventBus modBus, ModContainer container) {
        DysonContent.register(modBus, container);
    }
}
