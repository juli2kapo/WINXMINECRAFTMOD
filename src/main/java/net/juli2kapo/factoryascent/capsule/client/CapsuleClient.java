package net.juli2kapo.factoryascent.capsule.client;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.capsule.CapsuleContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client side of the capsule machines: their screens. */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class CapsuleClient {
    public CapsuleClient(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            e.register(CapsuleContent.SIZE_CHAMBER_MENU.get(), SizeChamberScreen::new);
            e.register(CapsuleContent.MOB_RELEASER_MENU.get(), MobReleaserScreen::new);
        });
    }
}
