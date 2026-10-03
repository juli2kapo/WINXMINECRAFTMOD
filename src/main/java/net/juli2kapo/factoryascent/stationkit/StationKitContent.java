package net.juli2kapo.factoryascent.stationkit;

import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.space.station.StationContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/** Registrations of the station kit content: two payload items and the crew pod's seat. */
public final class StationKitContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(FactoryAscent.MOD_ID);
    private static final ResourceKey<CreativeModeTab> ORBITAL_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "orbital"));

    public static final DeferredItem<StationKitItem> STATION_KIT = ITEMS.registerItem("station_kit", StationKitItem::new,
            p -> p.stacksTo(1));
    public static final DeferredItem<CargoPodItem> CARGO_POD = ITEMS.registerItem("cargo_pod", CargoPodItem::new,
            p -> p.stacksTo(1));

    public static final DeferredHolder<EntityType<?>, EntityType<PodSeatEntity>> POD_SEAT = ENTITIES.registerEntityType(
            "pod_seat", PodSeatEntity::new, MobCategory.MISC,
            b -> b.sized(0.6f, 0.6f).noSummon().fireImmune().clientTrackingRange(16).updateInterval(4));

    private StationKitContent() {}

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().equals(ORBITAL_TAB)) creativeItems().forEach(i -> e.accept(i.get()));
        });
        // the Station Core's cargo hold, for hoppers and pipes
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerBlockEntity(Capabilities.Item.BLOCK,
                StationContent.STATION_CORE_BE.get(), (be, side) -> VanillaContainerWrapper.of(be.cargo())));
    }

    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of(STATION_KIT, CARGO_POD);
    }
}
