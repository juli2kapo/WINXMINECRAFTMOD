package net.juli2kapo.factoryascent.stationkit.client;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.stationkit.StationKitContent;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client side of the station kit content: the crew pod's seat is invisible (the Return Pod block is the pod). */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class StationKitClient {
    public StationKitClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(StationKitContent.POD_SEAT.get(), NoopRenderer::new));
    }
}
