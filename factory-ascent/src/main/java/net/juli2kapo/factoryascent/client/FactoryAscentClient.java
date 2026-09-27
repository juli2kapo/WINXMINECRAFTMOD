package net.juli2kapo.factoryascent.client;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class FactoryAscentClient {
    public FactoryAscentClient(IEventBus modBus) {
        modBus.addListener(FactoryAscentClient::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MACHINE.get(), MachineScreen::new);
    }
}
