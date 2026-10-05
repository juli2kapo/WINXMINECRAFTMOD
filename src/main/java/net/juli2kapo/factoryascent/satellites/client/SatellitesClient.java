package net.juli2kapo.factoryascent.satellites.client;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.satellites.SatellitesContent;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Client side of the satellites in Earth orbit: their renderer and the tracker on the HUD. */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class SatellitesClient {
    public SatellitesClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(SatellitesContent.ORBITING_SATELLITE.get(), OrbitingSatelliteRenderer::new));
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "satellite_tracker"),
                        SatelliteTracker::render));
    }
}
