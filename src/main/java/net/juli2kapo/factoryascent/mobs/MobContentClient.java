package net.juli2kapo.factoryascent.mobs;

import net.neoforged.bus.api.IEventBus;

/** Client-side registration of the mob tools (renderers, particles, HUD). */
public final class MobContentClient {
    private MobContentClient() {}

    public static void register(IEventBus modBus) {
        SizeRayClient.register(modBus);
    }
}
