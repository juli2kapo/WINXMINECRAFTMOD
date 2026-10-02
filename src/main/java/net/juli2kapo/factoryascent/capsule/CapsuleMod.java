package net.juli2kapo.factoryascent.capsule;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * The capsule machines' own entry point (a further {@code @Mod} class of Factory Ascent, like the
 * phone's and the Dyson Cube's): the Size Chamber and the Mob Releaser.
 */
@Mod(FactoryAscent.MOD_ID)
public final class CapsuleMod {
    public CapsuleMod(IEventBus modBus, ModContainer container) {
        CapsuleContent.register(modBus, container);
    }
}
