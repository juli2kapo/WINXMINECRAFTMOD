package net.juli2kapo.factoryascent.outpost;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Planetary outposts' own entry point (a further {@code @Mod} class of Factory Ascent, like the
 * ships' and the Dyson Cube's), so the planet materials and the way home plug in without touching
 * the main mod class.
 */
@Mod(FactoryAscent.MOD_ID)
public final class OutpostMod {
    public OutpostMod(IEventBus modBus, ModContainer container) {
        OutpostContent.register(modBus, container);
    }
}
