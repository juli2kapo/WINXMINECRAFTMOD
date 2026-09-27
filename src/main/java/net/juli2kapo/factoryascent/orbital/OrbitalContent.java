package net.juli2kapo.factoryascent.orbital;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;

/** Entry point of the Orbital age: teams, satellites, launch pad, ground station, wireless terminal. */
public final class OrbitalContent {
    private OrbitalContent() {}

    /** Called from the mod constructor. */
    public static void register(IEventBus modBus) {
    }

    /** Icon of the Orbital creative tab. */
    public static ItemLike tabIcon() {
        return net.minecraft.world.item.Items.FIREWORK_ROCKET;
    }

    /** Items for the Orbital creative tab, in order. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of();
    }
}
