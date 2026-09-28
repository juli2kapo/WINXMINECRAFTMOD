package net.juli2kapo.factoryascent.orbital.client;

import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * Client registration of the Orbital age's newer screens: the Launch Controller's menu screen
 * (and its Launch refusals) and the survey map (views and imagery batches).
 */
public final class OrbitalUiClient {
    private OrbitalUiClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(OrbitalContent.LAUNCH_CONTROLLER_MENU.get(), LaunchControllerScreen::new));
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(OrbitalPayloads.PadMessage.TYPE, (payload, context) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof LaunchControllerScreen screen) {
                    screen.showMessage(payload.message());
                } else if (mc.player != null) {
                    mc.player.sendOverlayMessage(payload.message());
                }
            });
            e.register(OrbitalPayloads.SurveyView.TYPE, (payload, context) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof SurveyScreen screen) {
                    if (payload.open()) {
                        screen.reopen(payload);
                    } else {
                        screen.update(payload);
                    }
                } else if (payload.open()) {
                    mc.gui.setScreen(new SurveyScreen(payload));
                }
            });
            e.register(OrbitalPayloads.SurveyTiles.TYPE, (payload, context) -> {
                if (Minecraft.getInstance().gui.screen() instanceof SurveyScreen) SurveyImagery.accept(payload);
            });
        });
    }
}
