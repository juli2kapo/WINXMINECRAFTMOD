package net.juli2kapo.factoryascent.ships;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * The ships' own entry point (a second {@code @Mod} class of Factory Ascent, like the client
 * one), so the feature plugs in without touching the main mod class. The three ships join the
 * Tools tab as its "vehicles" group, after the tools.
 */
@Mod(FactoryAscent.MOD_ID)
public final class ShipsMod {
    private static final ResourceKey<net.minecraft.world.item.CreativeModeTab> TOOLS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "tools"));

    public ShipsMod(IEventBus modBus, ModContainer container) {
        ShipContent.register(modBus, container);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == TOOLS_TAB || e.getTabKey().equals(TOOLS_TAB)) {
                ShipContent.creativeItems().forEach(i -> e.accept(i.get()));
            }
        });
    }
}
