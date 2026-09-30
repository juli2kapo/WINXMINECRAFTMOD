package net.juli2kapo.factoryascent.dyson;

import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Dyson Sphere: the Quantum age's capstone megaproject. A team builds Solar Collectors
 * (Stellar Alloy from the Plasma Forge, assembled in the Precision Assembler), fires them into
 * solar orbit with a Mass Driver (or, early on, one per rocket from a Launch Pad), and the swarm
 * ({@link DysonSwarm}) grows around the sun, visibly, in the sky of every member. Dyson Receivers
 * turn it into huge amounts of FE; the Dyson Monitor shows the project.
 */
public final class DysonContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    private static final ResourceKey<CreativeModeTab> ORBITAL_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "orbital"));

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0f, 12f)
                .requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK);
    }

    public static final DeferredBlock<MassDriverBlock> MASS_DRIVER = BLOCKS.registerBlock("mass_driver",
            MassDriverBlock::new, () -> metal().mapColor(MapColor.COLOR_CYAN).lightLevel(s -> 5));
    public static final DeferredBlock<MassDriverRailBlock> MASS_DRIVER_RAIL = BLOCKS.registerBlock("mass_driver_rail",
            MassDriverRailBlock::new, () -> metal().noOcclusion().lightLevel(s -> 3));
    public static final DeferredBlock<DysonReceiverBlock> DYSON_RECEIVER = BLOCKS.registerBlock("dyson_receiver",
            DysonReceiverBlock::new, () -> metal().mapColor(MapColor.GOLD).noOcclusion().lightLevel(s -> 6));
    public static final DeferredBlock<DysonReceiverArrayBlock> DYSON_RECEIVER_ARRAY = BLOCKS.registerBlock("dyson_receiver_array",
            DysonReceiverArrayBlock::new, () -> metal().mapColor(MapColor.COLOR_BLUE).noOcclusion());
    public static final DeferredBlock<DysonMonitorBlock> DYSON_MONITOR = BLOCKS.registerBlock("dyson_monitor",
            DysonMonitorBlock::new, () -> metal().noOcclusion().lightLevel(s -> 7));

    public static final DeferredItem<BlockItem> MASS_DRIVER_ITEM = ITEMS.registerItem("mass_driver",
            p -> new FactoryBlockItem(MASS_DRIVER.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> MASS_DRIVER_RAIL_ITEM = ITEMS.registerItem("mass_driver_rail",
            p -> new FactoryBlockItem(MASS_DRIVER_RAIL.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> DYSON_RECEIVER_ITEM = ITEMS.registerItem("dyson_receiver",
            p -> new FactoryBlockItem(DYSON_RECEIVER.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> DYSON_RECEIVER_ARRAY_ITEM = ITEMS.registerItem("dyson_receiver_array",
            p -> new FactoryBlockItem(DYSON_RECEIVER_ARRAY.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> DYSON_MONITOR_ITEM = ITEMS.registerItem("dyson_monitor",
            p -> new FactoryBlockItem(DYSON_MONITOR.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<Item> STELLAR_ALLOY_INGOT = ITEMS.registerSimpleItem("stellar_alloy_ingot",
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<DysonCollectorItem> DYSON_COLLECTOR = ITEMS.registerItem("dyson_collector",
            DysonCollectorItem::new, p -> p.rarity(Rarity.UNCOMMON));

    public static final Supplier<BlockEntityType<MassDriverBlockEntity>> MASS_DRIVER_BE = BLOCK_ENTITIES.register(
            "mass_driver", () -> new BlockEntityType<>(MassDriverBlockEntity::new, MASS_DRIVER.get()));
    public static final Supplier<BlockEntityType<DysonReceiverBlockEntity>> DYSON_RECEIVER_BE = BLOCK_ENTITIES.register(
            "dyson_receiver", () -> new BlockEntityType<>(DysonReceiverBlockEntity::new, DYSON_RECEIVER.get()));
    public static final Supplier<BlockEntityType<DysonMonitorBlockEntity>> DYSON_MONITOR_BE = BLOCK_ENTITIES.register(
            "dyson_monitor", () -> new BlockEntityType<>(DysonMonitorBlockEntity::new, DYSON_MONITOR.get()));

    private DysonContent() {}

    /** Called from {@link DysonMod}. */
    static void register(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, DysonConfig.SPEC, FactoryAscent.MOD_ID + "-dyson-server.toml");
        modBus.addListener(DysonPayloads::register);
        modBus.addListener((RegisterCapabilitiesEvent e) -> {
            e.registerBlockEntity(Capabilities.Energy.BLOCK, MASS_DRIVER_BE.get(), (be, side) -> be.energyHandler());
            e.registerBlockEntity(Capabilities.Item.BLOCK, MASS_DRIVER_BE.get(), (be, side) -> be.itemHandler());
            e.registerBlockEntity(Capabilities.Energy.BLOCK, DYSON_RECEIVER_BE.get(), (be, side) -> be.energyHandler());
        });
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().equals(ORBITAL_TAB)) creativeItems().forEach(i -> e.accept(i.get()));
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> DysonCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> DysonService.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> DysonService.clear());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) DysonService.login(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> DysonService.logout(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) DysonService.login(sp);
        });
    }

    /** Items for the Orbital creative tab (after the orbital and space items), in order. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return List.of(STELLAR_ALLOY_INGOT, DYSON_COLLECTOR, MASS_DRIVER_ITEM, MASS_DRIVER_RAIL_ITEM,
                DYSON_RECEIVER_ITEM, DYSON_RECEIVER_ARRAY_ITEM, DYSON_MONITOR_ITEM);
    }
}
