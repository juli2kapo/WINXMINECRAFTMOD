package net.juli2kapo.factoryascent.storagenet;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;

/** Entry point of the storage network feature (controller, drives, cells, terminal, interface). */
public final class StorageNetwork {
    private StorageNetwork() {}

    /** Called from the mod constructor: register blocks, items, block entities, menus, capabilities, payloads. */
    public static void register(IEventBus modBus) {
    }

    /** Items to show in the creative tab, in order. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of();
    }
}
