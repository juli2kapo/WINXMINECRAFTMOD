package net.juli2kapo.factoryascent.xdim.client;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.xdim.XdimContent;
import net.juli2kapo.factoryascent.xdim.XdimPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Client side of the interdimensional links: the core renderer and the link screen. */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class XdimClient {
    public XdimClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(XdimContent.LINK_BE.get(), LinkRenderer::new));
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(XdimPayloads.View.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof XdimScreen screen && screen.pos().equals(payload.pos())) {
                screen.update(payload);
            } else if (payload.open()) {
                mc.gui.setScreen(new XdimScreen(payload));
            }
        }));
    }
}
