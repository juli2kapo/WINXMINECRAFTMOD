package net.juli2kapo.factoryascent.storagenet;

import net.juli2kapo.factoryascent.storagenet.client.DriveScreen;
import net.juli2kapo.factoryascent.storagenet.client.TerminalScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-side registration of the storage network (screens, renderers). */
public final class StorageNetworkClient {
    private StorageNetworkClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(StorageNetworkClient::registerScreens);
        modBus.addListener(StorageNetworkClient::registerPayloadHandlers);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(StorageContent.DRIVE_MENU.get(), DriveScreen::new);
        event.register(StorageContent.TERMINAL_MENU.get(), TerminalScreen::new);
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(TerminalSyncPayload.TYPE, StorageNetworkClient::onTerminalSync);
    }

    private static void onTerminalSync(TerminalSyncPayload payload, IPayloadContext context) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId()) {
            menu.applySync(payload);
        }
    }
}
