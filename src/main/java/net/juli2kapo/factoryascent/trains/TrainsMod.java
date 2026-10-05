package net.juli2kapo.factoryascent.trains;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * The trains' own entry point (a further {@code @Mod} class of Factory Ascent, like the ships'),
 * so the feature plugs in without touching the main mod class. The rolling stock, the Coupler and
 * the Train Station join the Tools tab's vehicles, after the ships.
 */
@Mod(FactoryAscent.MOD_ID)
public final class TrainsMod {
    private static final ResourceKey<CreativeModeTab> TOOLS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "tools"));

    public TrainsMod(IEventBus modBus, ModContainer container) {
        TrainContent.register(modBus, container);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().equals(TOOLS_TAB)) TrainContent.creativeItems().forEach(i -> e.accept(i.get()));
        });
    }
}
