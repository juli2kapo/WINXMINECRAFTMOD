package net.juli2kapo.factoryascent.fluid;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The fluids' own entry point (a further {@code @Mod} class of Factory Ascent, like the ships'
 * and the Dyson Cube's), so the feature plugs in without touching the main mod class.
 */
@Mod(FactoryAscent.MOD_ID)
public final class FluidsMod {
    public FluidsMod(IEventBus modBus, ModContainer container) {
        FluidContent.register(modBus, container);
    }
}
