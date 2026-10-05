package net.juli2kapo.factoryascent.trains;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * Trains: the Steam Locomotive (Bronze age), the Diesel-Electric Locomotive (Automation age), the
 * Passenger Car, Cargo Wagon, Tank Wagon and Hopper Wagon, the Coupler and the Train Station.
 * Registers the entities, items, block, block entity, menus, data components, payloads,
 * capabilities and the trains' own config file.
 */
public final class TrainContent {
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SteamLocomotive>> STEAM_LOCOMOTIVE = ENTITIES.registerEntityType(
            "steam_locomotive", SteamLocomotive::new, MobCategory.MISC,
            b -> b.sized(0.98f, 1.9f).clientTrackingRange(10).updateInterval(1).setShouldReceiveVelocityUpdates(true));
    public static final DeferredHolder<EntityType<?>, EntityType<DieselLocomotive>> DIESEL_LOCOMOTIVE = ENTITIES.registerEntityType(
            "diesel_locomotive", DieselLocomotive::new, MobCategory.MISC,
            b -> b.sized(0.98f, 1.9f).clientTrackingRange(10).updateInterval(1).setShouldReceiveVelocityUpdates(true));
    public static final DeferredHolder<EntityType<?>, EntityType<PassengerCar>> PASSENGER_CAR = ENTITIES.registerEntityType(
            "passenger_car", PassengerCar::new, MobCategory.MISC,
            b -> b.sized(0.98f, 1.6f).clientTrackingRange(10).updateInterval(1).setShouldReceiveVelocityUpdates(true));
    public static final DeferredHolder<EntityType<?>, EntityType<CargoWagon>> CARGO_WAGON = ENTITIES.registerEntityType(
            "cargo_wagon", CargoWagon::new, MobCategory.MISC,
            b -> b.sized(0.98f, 1.4f).clientTrackingRange(10).updateInterval(1).setShouldReceiveVelocityUpdates(true));
    public static final DeferredHolder<EntityType<?>, EntityType<TankWagon>> TANK_WAGON = ENTITIES.registerEntityType(
            "tank_wagon", TankWagon::new, MobCategory.MISC,
            b -> b.sized(0.98f, 1.3f).clientTrackingRange(10).updateInterval(1).setShouldReceiveVelocityUpdates(true));
    public static final DeferredHolder<EntityType<?>, EntityType<HopperWagon>> HOPPER_WAGON = ENTITIES.registerEntityType(
            "hopper_wagon", HopperWagon::new, MobCategory.MISC,
            b -> b.sized(0.98f, 1.1f).clientTrackingRange(10).updateInterval(1).setShouldReceiveVelocityUpdates(true));

    public static final DeferredItem<TrainItem> STEAM_LOCOMOTIVE_ITEM = ITEMS.registerItem("steam_locomotive",
            p -> new TrainItem(STEAM_LOCOMOTIVE, p), p -> p.stacksTo(1));
    public static final DeferredItem<TrainItem> DIESEL_LOCOMOTIVE_ITEM = ITEMS.registerItem("diesel_locomotive",
            p -> new TrainItem(DIESEL_LOCOMOTIVE, p), p -> p.stacksTo(1));
    public static final DeferredItem<TrainItem> PASSENGER_CAR_ITEM = ITEMS.registerItem("passenger_car",
            p -> new TrainItem(PASSENGER_CAR, p), p -> p.stacksTo(1));
    public static final DeferredItem<TrainItem> CARGO_WAGON_ITEM = ITEMS.registerItem("cargo_wagon",
            p -> new TrainItem(CARGO_WAGON, p), p -> p.stacksTo(1));
    public static final DeferredItem<TrainItem> TANK_WAGON_ITEM = ITEMS.registerItem("tank_wagon",
            p -> new TrainItem(TANK_WAGON, p), p -> p.stacksTo(1));
    public static final DeferredItem<TrainItem> HOPPER_WAGON_ITEM = ITEMS.registerItem("hopper_wagon",
            p -> new TrainItem(HOPPER_WAGON, p), p -> p.stacksTo(1));
    public static final DeferredItem<CouplerItem> COUPLER = ITEMS.registerItem("coupler", CouplerItem::new, p -> p.stacksTo(1));

    public static final DeferredBlock<StationBlock> TRAIN_STATION = BLOCKS.registerBlock("train_station", StationBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5f, 6f).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE).noOcclusion().lightLevel(s -> s.getValue(StationBlock.LIT) ? 6 : 0));
    public static final DeferredItem<BlockItem> TRAIN_STATION_ITEM = ITEMS.registerItem("train_station",
            p -> new FactoryBlockItem(TRAIN_STATION.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final Supplier<BlockEntityType<StationBlockEntity>> STATION_BE = BLOCK_ENTITIES.register("train_station",
            () -> new BlockEntityType<>(StationBlockEntity::new, TRAIN_STATION.get()));

    /** FE carried by a locomotive item. Items use vanilla's container component, fluids the fluids' tank_contents. */
    public static final Supplier<DataComponentType<Integer>> TRAIN_ENERGY = COMPONENTS.registerComponentType(
            "train_energy", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** The vehicle a Coupler picked first. */
    public static final Supplier<DataComponentType<UUID>> COUPLER_TARGET = COMPONENTS.registerComponentType(
            "coupler_target", b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final Supplier<MenuType<TrainMenu>> TRAIN_MENU = MENUS.register("train",
            () -> IMenuTypeExtension.create(TrainMenu::fromNetwork));
    public static final Supplier<MenuType<StationMenu>> STATION_MENU = MENUS.register("train_station",
            () -> IMenuTypeExtension.create(StationMenu::fromNetwork));

    private TrainContent() {}

    public static void register(IEventBus modBus, ModContainer container) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, TrainConfig.SPEC, "factoryascent-trains-server.toml");
        modBus.addListener(TrainPayloads::register);
        modBus.addListener(TrainContent::registerCapabilities);
    }

    /** Items in the order of the ages (shown in the Tools tab, after the ships). */
    public static List<DeferredItem<? extends Item>> creativeItems() {
        return List.of(STEAM_LOCOMOTIVE_ITEM, PASSENGER_CAR_ITEM, CARGO_WAGON_ITEM, TANK_WAGON_ITEM, HOPPER_WAGON_ITEM, COUPLER,
                TRAIN_STATION_ITEM, DIESEL_LOCOMOTIVE_ITEM);
    }

    public static Item itemFor(EntityType<?> type) {
        if (type == STEAM_LOCOMOTIVE.get()) return STEAM_LOCOMOTIVE_ITEM.get();
        if (type == DIESEL_LOCOMOTIVE.get()) return DIESEL_LOCOMOTIVE_ITEM.get();
        if (type == PASSENGER_CAR.get()) return PASSENGER_CAR_ITEM.get();
        if (type == TANK_WAGON.get()) return TANK_WAGON_ITEM.get();
        if (type == HOPPER_WAGON.get()) return HOPPER_WAGON_ITEM.get();
        return CARGO_WAGON_ITEM.get();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.Item.ENTITY, CARGO_WAGON.get(), (w, ctx) -> VanillaContainerWrapper.of(w));
        event.registerEntity(Capabilities.Item.ENTITY_AUTOMATION, CARGO_WAGON.get(), (w, side) -> VanillaContainerWrapper.of(w));
        event.registerEntity(Capabilities.Item.ENTITY, HOPPER_WAGON.get(), (w, ctx) -> VanillaContainerWrapper.of(w));
        event.registerEntity(Capabilities.Item.ENTITY_AUTOMATION, HOPPER_WAGON.get(), (w, side) -> VanillaContainerWrapper.of(w));
        event.registerEntity(Capabilities.Item.ENTITY, STEAM_LOCOMOTIVE.get(), (w, ctx) -> VanillaContainerWrapper.of(w));
        event.registerEntity(Capabilities.Item.ENTITY, DIESEL_LOCOMOTIVE.get(), (w, ctx) -> VanillaContainerWrapper.of(w));
        event.registerEntity(Capabilities.Fluid.ENTITY, TANK_WAGON.get(), (w, side) -> w.tank());
        event.registerEntity(Capabilities.Fluid.ENTITY, STEAM_LOCOMOTIVE.get(), (w, side) -> w.water());
        event.registerEntity(Capabilities.Fluid.ENTITY, DIESEL_LOCOMOTIVE.get(), (w, side) -> w.diesel());
        event.registerEntity(Capabilities.Energy.ENTITY, DIESEL_LOCOMOTIVE.get(), (w, side) -> w.energyHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, STATION_BE.get(), (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, STATION_BE.get(), (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, STATION_BE.get(), (be, side) -> be.energyHandler());
    }
}
