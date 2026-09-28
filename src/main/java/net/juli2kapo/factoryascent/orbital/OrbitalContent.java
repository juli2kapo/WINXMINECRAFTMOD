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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entry point of the Orbital age: teams ({@link FactoryTeams}, also managed from the Team screen,
 * {@link OrbitalConsole}), satellites in orbit ({@link OrbitRegistry}), the Launch Pad, the Ground
 * Station, the Wireless Terminal, the signal API other devices use ({@link OrbitalSignal}), and
 * orbital warfare: the Orbital Radar, Anti-Satellite missiles and Guardian Satellites.
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
    public static final DeferredBlock<OrbitalRadarBlock> ORBITAL_RADAR = BLOCKS.registerBlock("orbital_radar",
            OrbitalRadarBlock::new, () -> metal().mapColor(MapColor.COLOR_RED).noOcclusion().lightLevel(s -> 3));

    public static final DeferredItem<BlockItem> LAUNCH_PAD_ITEM = ITEMS.registerItem("launch_pad",
            p -> new FactoryBlockItem(LAUNCH_PAD.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> LAUNCH_CONTROLLER_ITEM = ITEMS.registerItem("launch_controller",
            p -> new FactoryBlockItem(LAUNCH_CONTROLLER.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> GROUND_STATION_ITEM = ITEMS.registerItem("ground_station",
            p -> new FactoryBlockItem(GROUND_STATION.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> ORBITAL_RADAR_ITEM = ITEMS.registerItem("orbital_radar",
            p -> new FactoryBlockItem(ORBITAL_RADAR.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<SatelliteItem> SURVEY_SATELLITE = ITEMS.registerItem("survey_satellite",
            p -> new SatelliteItem(SatelliteType.SURVEY, p), p -> p.stacksTo(1));
    public static final DeferredItem<SatelliteItem> UPLINK_SATELLITE = ITEMS.registerItem("uplink_satellite",
            p -> new SatelliteItem(SatelliteType.UPLINK, p), p -> p.stacksTo(1));
    public static final DeferredItem<SatelliteItem> GUARDIAN_SATELLITE = ITEMS.registerItem("guardian_satellite",
            p -> new SatelliteItem(SatelliteType.DEFENSE, p), p -> p.stacksTo(1));
    public static final DeferredItem<AsatMissileItem> ASAT_MISSILE = ITEMS.registerItem("asat_missile",
            AsatMissileItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> ROCKET_FUEL = ITEMS.registerSimpleItem("rocket_fuel");
    public static final DeferredItem<WirelessTerminalItem> WIRELESS_TERMINAL = ITEMS.registerItem("wireless_terminal",
            WirelessTerminalItem::new, p -> p.stacksTo(1));

    public static final Supplier<BlockEntityType<LaunchControllerBlockEntity>> LAUNCH_CONTROLLER_BE = BLOCK_ENTITIES.register(
            "launch_controller", () -> new BlockEntityType<>(LaunchControllerBlockEntity::new, LAUNCH_CONTROLLER.get()));
    public static final Supplier<BlockEntityType<GroundStationBlockEntity>> GROUND_STATION_BE = BLOCK_ENTITIES.register(
            "ground_station", () -> new BlockEntityType<>(GroundStationBlockEntity::new, GROUND_STATION.get()));
    public static final Supplier<BlockEntityType<OrbitalRadarBlockEntity>> ORBITAL_RADAR_BE = BLOCK_ENTITIES.register(
            "orbital_radar", () -> new BlockEntityType<>(OrbitalRadarBlockEntity::new, ORBITAL_RADAR.get()));

    /** The Storage Terminal a Wireless Terminal opens. */
    public static final Supplier<DataComponentType<GlobalPos>> LINKED_TERMINAL = COMPONENTS.registerComponentType(
            "linked_terminal", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    /** What a programmed Anti-Satellite missile flies at. */
    public static final Supplier<DataComponentType<AsatMissileItem.Target>> ASAT_TARGET = COMPONENTS.registerComponentType(
            "asat_target", b -> b.persistent(AsatMissileItem.Target.CODEC).networkSynchronized(AsatMissileItem.Target.STREAM_CODEC));

    private OrbitalContent() {}

    /** Called from the mod constructor. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(OrbitalPayloads::register);
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.Item.BLOCK, LAUNCH_CONTROLLER_BE.get(), (be, side) -> be.itemHandler());
            e.registerBlockEntity(Capabilities.Energy.BLOCK, ORBITAL_RADAR_BE.get(), (be, side) -> be.energyHandler());
        });
        // The one hook into the storage network: remote terminal sessions stay open over the uplink.
        TerminalMenu.remoteAccess = WirelessTerminalItem::allowsRemote;
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> TeamCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> SurveyMapper.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> SurveyMapper.clear());
        // Sneaking with an item normally skips the block; sneak + flint and steel must reach the pad to launch.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) -> {
            if (e.getEntity().isShiftKeyDown() && e.getItemStack().is(net.minecraft.world.item.Items.FLINT_AND_STEEL)) {
                var block = e.getLevel().getBlockState(e.getPos()).getBlock();
                if (block instanceof LaunchControllerBlock || block instanceof LaunchPadBlock) {
                    e.setUseBlock(net.minecraft.util.TriState.TRUE);
                }
            }
        });
    }

    /** Icon of the Orbital creative tab. */
    public static ItemLike tabIcon() {
        return UPLINK_SATELLITE.get();
    }

    /** Items for the Orbital creative tab, in order. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of(LAUNCH_PAD_ITEM, LAUNCH_CONTROLLER_ITEM, ROCKET_FUEL, SURVEY_SATELLITE, UPLINK_SATELLITE,
                GUARDIAN_SATELLITE, GROUND_STATION_ITEM, WIRELESS_TERMINAL, ORBITAL_RADAR_ITEM, ASAT_MISSILE);
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
