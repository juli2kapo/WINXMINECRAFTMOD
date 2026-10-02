package net.juli2kapo.factoryascent.phone.dock.client;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.phone.dock.DockContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client side of the Phone Dock: its screen. */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class DockClient {
    public DockClient(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(DockContent.PHONE_DOCK_MENU.get(), PhoneDockScreen::new));
    }
}
