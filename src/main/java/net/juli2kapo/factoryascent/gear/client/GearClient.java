package net.juli2kapo.factoryascent.gear.client;

import net.juli2kapo.factoryascent.gear.GearContent;
import net.juli2kapo.factoryascent.kinetic.client.KineticRenderer;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.registry.ModBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client side of the early/mid-game additions: the backpack screen and the turning water wheel and windmill. */
public final class GearClient {
    private GearClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(GearContent.BACKPACK_MENU.get(), BackpackScreen::new));
        modBus.addListener(KineticRenderer::registerModels);
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(ModBlockEntities.machine(MachineType.WATER_WHEEL).get(), KineticRenderer::new);
            e.registerBlockEntityRenderer(ModBlockEntities.machine(MachineType.WINDMILL).get(), KineticRenderer::new);
        });
    }
}
