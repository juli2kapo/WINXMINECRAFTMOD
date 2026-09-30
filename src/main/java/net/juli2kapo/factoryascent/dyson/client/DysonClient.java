package net.juli2kapo.factoryascent.dyson.client;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.dyson.DysonContent;
import net.juli2kapo.factoryascent.dyson.DysonPayloads;
import net.juli2kapo.factoryascent.space.client.SkyHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client side of the Dyson Cube (its own {@code @Mod} class for the client dist): the swarm in
 * the sky ({@link DysonSky}), the Mass Driver's coils and shots, the Dyson Receiver's sun-tracking
 * dish and beam, the Dyson Monitor's hologram ({@link DysonRenderers}) and its screen
 * ({@link DysonScreen}). Knows the size of the local player's team swarm (sent by the server).
 */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class DysonClient {
    private static long collectors;
    private static int target = 1000;

    public DysonClient(IEventBus modBus) {
        modBus.addListener((RegisterRenderPipelinesEvent e) -> {
            e.registerPipeline(DysonSky.GLOW);
            e.registerPipeline(DysonSky.SHADE);
        });
        modBus.addListener((ModelEvent.RegisterStandalone e) -> e.register(DysonRenderers.DISH,
                SimpleUnbakedStandaloneModel.simpleModelWrapper(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/dyson_receiver_dish"))));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(DysonContent.MASS_DRIVER_BE.get(), DysonRenderers.MassDriverRenderer::new);
            e.registerBlockEntityRenderer(DysonContent.DYSON_RECEIVER_BE.get(), DysonRenderers.ReceiverRenderer::new);
            e.registerBlockEntityRenderer(DysonContent.DYSON_MONITOR_BE.get(), DysonRenderers.MonitorRenderer::new);
        });
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(DysonPayloads.Sync.TYPE, (payload, context) -> {
                collectors = payload.collectors();
                target = Math.max(1, payload.target());
            });
            e.register(DysonPayloads.MonitorView.TYPE, (payload, context) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof DysonScreen screen && screen.pos().equals(payload.pos())) {
                    screen.update(payload);
                } else if (payload.open()) {
                    mc.gui.setScreen(new DysonScreen(payload));
                }
            });
        });
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent.AfterSky e) -> DysonSky.afterSky(e));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            collectors = 0;
            target = 1000;
        });
        SkyHooks.register(DysonSky::aroundSun);
    }

    /** Collectors in the local player's team swarm. */
    public static long collectors() {
        return collectors;
    }

    public static int target() {
        return target;
    }
}
