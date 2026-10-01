package net.juli2kapo.factoryascent.xdim;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The interdimensional links' own entry point (a further {@code @Mod} class of Factory Ascent,
 * like the ships', fluids' and Dyson Cube's), so the feature plugs in without touching the main mod class.
 */
@Mod(FactoryAscent.MOD_ID)
public final class XdimMod {
    public XdimMod(IEventBus modBus, ModContainer container) {
        XdimContent.register(modBus, container);
    }
}
