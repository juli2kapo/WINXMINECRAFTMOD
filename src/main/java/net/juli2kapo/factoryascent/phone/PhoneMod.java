package net.juli2kapo.factoryascent.phone;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The Factory Phone's own entry point (a further {@code @Mod} class of Factory Ascent, like the
 * Dyson Cube's), so the phone and its apps plug in without touching the main mod class.
 */
@Mod(FactoryAscent.MOD_ID)
public final class PhoneMod {
    public PhoneMod(IEventBus modBus, ModContainer container) {
        PhoneContent.register(modBus, container);
    }
}
