package net.juli2kapo.factoryascent.storagenet;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Entry point of the storage network feature (controller, drives, cells, terminal, interface). */
public final class StorageNetwork {
    private StorageNetwork() {}

    /** Called from the mod constructor: register blocks, items, block entities, menus, capabilities, payloads. */
    public static void register(IEventBus modBus) {
        StorageContent.BLOCKS.register(modBus);
        StorageContent.ITEMS.register(modBus);
        StorageContent.COMPONENTS.register(modBus);
        StorageContent.BLOCK_ENTITIES.register(modBus);
        StorageContent.MENUS.register(modBus);
        modBus.addListener(StorageNetwork::registerCapabilities);
        modBus.addListener(StorageNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(StorageNetwork::onLevelUnload);
    }

    /** Items to show in the creative tab, in order. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return StorageContent.creativeItems();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, StorageContent.INTERFACE_BE.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, StorageContent.CONTROLLER_BE.get(), (be, side) -> be.energyHandler());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(TerminalSyncPayload.TYPE, TerminalSyncPayload.STREAM_CODEC)
                .playToServer(TerminalClickPayload.TYPE, TerminalClickPayload.STREAM_CODEC, TerminalClickPayload::handle);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) StorageNetManager.remove(level);
    }
}
