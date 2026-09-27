package net.juli2kapo.factoryascent.client;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class FactoryAscentClient {
    public FactoryAscentClient(IEventBus modBus) {
        modBus.addListener(FactoryAscentClient::registerScreens);
        modBus.addListener(QuernRenderer::registerModel);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(net.juli2kapo.factoryascent.registry.ModBlockEntities.machine(
                        net.juli2kapo.factoryascent.machine.MachineType.QUERN).get(), QuernRenderer::new));
        net.juli2kapo.factoryascent.storagenet.StorageNetworkClient.register(modBus);
        net.juli2kapo.factoryascent.ender.EnderContentClient.register(modBus);
        net.juli2kapo.factoryascent.mobs.MobContentClient.register(modBus);
        // Before JEI reads the recipes on the same event.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, ClientRecipes::onRecipesReceived);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MACHINE.get(), MachineScreen::new);
    }
}
