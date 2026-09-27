package net.juli2kapo.factoryascent.mobs;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;

/** Entry point of the mob tools (Mob Capsule, Minimizer and Maximizer rays). */
public final class MobContent {
    private MobContent() {}

    /** Called from the mod constructor. */
    public static void register(IEventBus modBus) {
    }

    /** Items for the Utility creative tab, in order. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of();
    }
}
