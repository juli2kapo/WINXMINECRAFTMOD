package net.juli2kapo.factoryascent.phone.dock;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** The Link Card and Phone Dock's own entry point (a further {@code @Mod} class of Factory Ascent). */
@Mod(FactoryAscent.MOD_ID)
public final class DockMod {
    public DockMod(IEventBus modBus) {
        DockContent.register(modBus);
    }
}
