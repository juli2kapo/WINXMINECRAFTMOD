package net.juli2kapo.factoryascent.orbital;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.juli2kapo.factoryascent.storagenet.TerminalMenu;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entry point of the Orbital age: teams ({@link FactoryTeams}), satellites in orbit
 * ({@link OrbitRegistry}), the Launch Pad, the Ground Station, the Wireless Terminal, and the
 * signal API other devices use ({@link OrbitalSignal}).
 */
public final class OrbitalContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f, 8f)
                .requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    public static final DeferredBlock<LaunchPadBlock> LAUNCH_PAD = BLOCKS.registerBlock("launch_pad",
            LaunchPadBlock::new, () -> metal().mapColor(MapColor.COLOR_GRAY));
    public static final DeferredBlock<LaunchControllerBlock> LAUNCH_CONTROLLER = BLOCKS.registerBlock("launch_controller",
            LaunchControllerBlock::new, () -> metal().mapColor(MapColor.COLOR_YELLOW).lightLevel(s -> 4));
    public static final DeferredBlock<GroundStationBlock> GROUND_STATION = BLOCKS.registerBlock("ground_station",
            GroundStationBlock::new, () -> metal().noOcclusion().lightLevel(s -> 3));

    public static final DeferredItem<BlockItem> LAUNCH_PAD_ITEM = ITEMS.registerItem("launch_pad",
            p -> new FactoryBlockItem(LAUNCH_PAD.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> LAUNCH_CONTROLLER_ITEM = ITEMS.registerItem("launch_controller",
            p -> new FactoryBlockItem(LAUNCH_CONTROLLER.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> GROUND_STATION_ITEM = ITEMS.registerItem("ground_station",
            p -> new FactoryBlockItem(GROUND_STATION.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<SatelliteItem> SURVEY_SATELLITE = ITEMS.registerItem("survey_satellite",
            p -> new SatelliteItem(SatelliteType.SURVEY, p), p -> p.stacksTo(1));
    public static final DeferredItem<SatelliteItem> UPLINK_SATELLITE = ITEMS.registerItem("uplink_satellite",
            p -> new SatelliteItem(SatelliteType.UPLINK, p), p -> p.stacksTo(1));
    public static final DeferredItem<Item> ROCKET_FUEL = ITEMS.registerSimpleItem("rocket_fuel");
    public static final DeferredItem<WirelessTerminalItem> WIRELESS_TERMINAL = ITEMS.registerItem("wireless_terminal",
            WirelessTerminalItem::new, p -> p.stacksTo(1));

    public static final Supplier<BlockEntityType<LaunchControllerBlockEntity>> LAUNCH_CONTROLLER_BE = BLOCK_ENTITIES.register(
            "launch_controller", () -> new BlockEntityType<>(LaunchControllerBlockEntity::new, LAUNCH_CONTROLLER.get()));
    public static final Supplier<BlockEntityType<GroundStationBlockEntity>> GROUND_STATION_BE = BLOCK_ENTITIES.register(
            "ground_station", () -> new BlockEntityType<>(GroundStationBlockEntity::new, GROUND_STATION.get()));

    /** The Storage Terminal a Wireless Terminal opens. */
    public static final Supplier<DataComponentType<GlobalPos>> LINKED_TERMINAL = COMPONENTS.registerComponentType(
            "linked_terminal", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    private OrbitalContent() {}

    /** Called from the mod constructor. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        // The one hook into the storage network: remote terminal sessions stay open over the uplink.
        TerminalMenu.remoteAccess = WirelessTerminalItem::allowsRemote;
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> TeamCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> SurveyMapper.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> SurveyMapper.clear());
    }

    /** Icon of the Orbital creative tab. */
    public static ItemLike tabIcon() {
        return UPLINK_SATELLITE.get();
    }

    /** Items for the Orbital creative tab, in order. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of(LAUNCH_PAD_ITEM, LAUNCH_CONTROLLER_ITEM, ROCKET_FUEL, SURVEY_SATELLITE, UPLINK_SATELLITE,
                GROUND_STATION_ITEM, WIRELESS_TERMINAL);
    }

    /** Grants a code-triggered advancement ({@code minecraft:impossible} criterion "done") to online players. */
    static void award(MinecraftServer server, Collection<UUID> players, String key) {
        AdvancementHolder holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, key));
        if (holder == null) return;
        for (UUID id : players) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.getAdvancements().award(holder, "done");
        }
    }
}
