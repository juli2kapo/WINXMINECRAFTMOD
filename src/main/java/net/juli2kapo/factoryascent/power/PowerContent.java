package net.juli2kapo.factoryascent.power;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.fusion.FusionPartBlock;
import net.juli2kapo.factoryascent.fusion.FusionPortBlock;
import net.juli2kapo.factoryascent.fusion.FusionPortBlockEntity;
import net.juli2kapo.factoryascent.fusion.TokamakCoreBlock;
import net.juli2kapo.factoryascent.fusion.TokamakCoreBlockEntity;
import net.juli2kapo.factoryascent.fusion.TokamakMenu;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.juli2kapo.factoryascent.nuclear.CoriumBlock;
import net.juli2kapo.factoryascent.nuclear.GeigerCounterItem;
import net.juli2kapo.factoryascent.nuclear.HazmatSuitItem;
import net.juli2kapo.factoryascent.nuclear.Radiation;
import net.juli2kapo.factoryascent.nuclear.RadiationEffect;
import net.juli2kapo.factoryascent.nuclear.RadiationState;
import net.juli2kapo.factoryascent.nuclear.ReactorControllerBlock;
import net.juli2kapo.factoryascent.nuclear.ReactorControllerBlockEntity;
import net.juli2kapo.factoryascent.nuclear.ReactorMenu;
import net.juli2kapo.factoryascent.nuclear.ReactorPartBlock;
import net.juli2kapo.factoryascent.nuclear.ReactorPortBlock;
import net.juli2kapo.factoryascent.nuclear.ReactorPortBlockEntity;
import net.juli2kapo.factoryascent.nuclear.WasteBarrelBlock;
import net.juli2kapo.factoryascent.nuclear.WasteBarrelBlockEntity;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * Entry point of the power ladder: the new generators ({@link Generator}), fission (uranium and
 * lead, the reactor, radiation, waste) in {@code nuclear/}, and fusion (the tokamak and its fuel)
 * in {@code fusion/}. The Centrifuge and Electrolyzer are ordinary processing machines
 * ({@code MachineType}). Balance lives in {@link PowerConfig}.
 */
public final class PowerContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, FactoryAscent.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, FactoryAscent.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FactoryAscent.MOD_ID);

    /** Blocks and items in creative-tab order. */
    private static final List<Supplier<? extends ItemLike>> POWER_TAB = new ArrayList<>();
    private static final List<Supplier<? extends ItemLike>> MATERIALS_TAB = new ArrayList<>();
    private static final List<Supplier<? extends ItemLike>> TOOLS_TAB = new ArrayList<>();
    private static final List<Supplier<? extends ItemLike>> WORLD_TAB = new ArrayList<>();

    // ---------------------------------------------------------------- properties

    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties generatorProperties(Generator g) {
        BlockBehaviour.Properties p = switch (g) {
            case KINETIC_DYNAMO -> metal(MapColor.COLOR_ORANGE).sound(SoundType.COPPER);
            case BIOGAS_GENERATOR -> metal(MapColor.COLOR_GREEN);
            case MAGMATIC_GENERATOR -> metal(MapColor.COLOR_BLACK);
            case SOLAR_ARRAY -> metal(MapColor.COLOR_BLUE);
            default -> metal(MapColor.METAL);
        };
        if (!g.fullBlock()) p = p.noOcclusion();
        if (g == Generator.STEAM_ENGINE || g == Generator.MAGMATIC_GENERATOR || g == Generator.BIOGAS_GENERATOR) {
            p = p.lightLevel(s -> s.getValue(PowerBlock.ACTIVE) ? 11 : 0);
        }
        if (g == Generator.RTG) p = p.lightLevel(s -> 5);
        return p;
    }

    // ---------------------------------------------------------------- generators

    private static final Map<Generator, DeferredBlock<PowerBlock>> GENERATORS = new EnumMap<>(Generator.class);
    private static final Map<Generator, Supplier<BlockEntityType<PowerBlockEntity>>> GENERATOR_TYPES = new EnumMap<>(Generator.class);

    static {
        for (Generator g : Generator.VALUES) {
            DeferredBlock<PowerBlock> block = BLOCKS.registerBlock(g.id(), p -> new PowerBlock(g, p), () -> generatorProperties(g));
            GENERATORS.put(g, block);
        }
    }

    public static final DeferredBlock<TurbineMastBlock> TURBINE_MAST = BLOCKS.registerBlock("turbine_mast", TurbineMastBlock::new,
            () -> metal(MapColor.SNOW).strength(3f, 6f).noOcclusion());

    // ---------------------------------------------------------------- ores and nuclear blocks

    public static final DeferredBlock<Block> LEAD_ORE = BLOCKS.registerSimpleBlock("lead_ore", () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(3f, 3f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> DEEPSLATE_LEAD_ORE = BLOCKS.registerSimpleBlock("deepslate_lead_ore", () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.DEEPSLATE).strength(4.5f, 3f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<Block> DEEPSLATE_URANIUM_ORE = BLOCKS.registerSimpleBlock("deepslate_uranium_ore", () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.DEEPSLATE).strength(5f, 3f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE).lightLevel(s -> 3));

    private static BlockBehaviour.Properties reactor() {
        return metal(MapColor.COLOR_GRAY).strength(5f, 12f);
    }

    public static final DeferredBlock<ReactorPartBlock> REACTOR_CASING = BLOCKS.registerBlock("reactor_casing",
            p -> new ReactorPartBlock(ReactorPartBlock.Part.CASING, p), PowerContent::reactor);
    public static final DeferredBlock<ReactorPartBlock> REACTOR_GLASS = BLOCKS.registerBlock("reactor_glass",
            p -> new ReactorPartBlock(ReactorPartBlock.Part.GLASS, p), () -> reactor().sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, pos, e) -> false).isRedstoneConductor((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos) -> false));
    public static final DeferredBlock<ReactorPartBlock> REACTOR_FUEL_CHANNEL = BLOCKS.registerBlock("reactor_fuel_channel",
            p -> new ReactorPartBlock(ReactorPartBlock.Part.FUEL_CHANNEL, p), () -> reactor().noOcclusion());
    public static final DeferredBlock<ReactorPartBlock> REACTOR_CONTROL_ROD = BLOCKS.registerBlock("reactor_control_rod",
            p -> new ReactorPartBlock(ReactorPartBlock.Part.CONTROL_ROD, p), () -> reactor().noOcclusion());
    public static final DeferredBlock<ReactorControllerBlock> REACTOR_CONTROLLER = BLOCKS.registerBlock("reactor_controller",
            ReactorControllerBlock::new, () -> reactor().lightLevel(s -> s.getValue(ReactorControllerBlock.ACTIVE) ? 9 : 2));
    public static final DeferredBlock<ReactorPortBlock> REACTOR_ACCESS_PORT = port("reactor_access_port", ReactorPortBlock.Kind.ACCESS);
    public static final DeferredBlock<ReactorPortBlock> REACTOR_POWER_PORT = port("reactor_power_port", ReactorPortBlock.Kind.POWER);
    public static final DeferredBlock<ReactorPortBlock> REACTOR_COOLANT_PORT = port("reactor_coolant_port", ReactorPortBlock.Kind.COOLANT);
    public static final DeferredBlock<ReactorPortBlock> REACTOR_REDSTONE_PORT = port("reactor_redstone_port", ReactorPortBlock.Kind.REDSTONE);
    public static final DeferredBlock<WasteBarrelBlock> WASTE_BARREL = BLOCKS.registerBlock("waste_barrel", WasteBarrelBlock::new,
            () -> metal(MapColor.COLOR_YELLOW).strength(4f, 12f).noOcclusion());
    public static final DeferredBlock<CoriumBlock> CORIUM = BLOCKS.registerBlock("corium", CoriumBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(25f, 1200f).requiresCorrectToolForDrops()
                    .sound(SoundType.ANCIENT_DEBRIS).lightLevel(s -> 12).pushReaction(PushReaction.BLOCK)
                    .emissiveRendering(s -> true));

    private static DeferredBlock<ReactorPortBlock> port(String name, ReactorPortBlock.Kind kind) {
        return BLOCKS.registerBlock(name, p -> new ReactorPortBlock(kind, p), PowerContent::reactor);
    }

    // ---------------------------------------------------------------- fusion blocks

    private static BlockBehaviour.Properties fusion() {
        return metal(MapColor.COLOR_CYAN).strength(8f, 60f);
    }

    public static final DeferredBlock<FusionPartBlock> FUSION_CASING = BLOCKS.registerBlock("fusion_casing",
            p -> new FusionPartBlock(false, p), PowerContent::fusion);
    public static final DeferredBlock<FusionPartBlock> FUSION_MAGNET = BLOCKS.registerBlock("fusion_magnet",
            p -> new FusionPartBlock(true, p), () -> fusion().mapColor(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<FusionPortBlock> FUSION_PORT = BLOCKS.registerBlock("fusion_port", FusionPortBlock::new, PowerContent::fusion);
    public static final DeferredBlock<TokamakCoreBlock> TOKAMAK_CORE = BLOCKS.registerBlock("tokamak_core", TokamakCoreBlock::new,
            () -> fusion().mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> s.getValue(TokamakCoreBlock.ACTIVE) ? 15 : 3));

    // ---------------------------------------------------------------- block items (tab order)

    static {
        for (Generator g : Generator.VALUES) {
            if (g == Generator.WIND_TURBINE) {
                POWER_TAB.add(blockItem(GENERATORS.get(g)));
                POWER_TAB.add(blockItem(TURBINE_MAST));
            } else if (g != Generator.RTG) {
                POWER_TAB.add(blockItem(GENERATORS.get(g)));
            }
        }
        for (var b : List.of(REACTOR_CONTROLLER, REACTOR_CASING, REACTOR_GLASS, REACTOR_FUEL_CHANNEL, REACTOR_CONTROL_ROD,
                REACTOR_ACCESS_PORT, REACTOR_POWER_PORT, REACTOR_COOLANT_PORT, REACTOR_REDSTONE_PORT, WASTE_BARREL)) {
            POWER_TAB.add(blockItem(b));
        }
        POWER_TAB.add(blockItem(GENERATORS.get(Generator.RTG)));
        for (var b : List.of(TOKAMAK_CORE, FUSION_MAGNET, FUSION_CASING, FUSION_PORT)) POWER_TAB.add(blockItem(b));
        for (var b : List.of(LEAD_ORE, DEEPSLATE_LEAD_ORE, DEEPSLATE_URANIUM_ORE, CORIUM)) WORLD_TAB.add(blockItem(b));
    }

    private static DeferredItem<BlockItem> blockItem(DeferredBlock<?> block) {
        return ITEMS.registerItem(block.getId().getPath(), p -> new FactoryBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix());
    }

    // ---------------------------------------------------------------- materials

    private static DeferredItem<Item> material(String name) {
        return material(name, 64);
    }

    private static DeferredItem<Item> material(String name, int stack) {
        DeferredItem<Item> item = ITEMS.registerItem(name, Item::new, p -> p.stacksTo(stack));
        MATERIALS_TAB.add(item);
        return item;
    }

    public static final DeferredItem<Item> RAW_LEAD = material("raw_lead");
    public static final DeferredItem<Item> LEAD_DUST = material("lead_dust");
    public static final DeferredItem<Item> LEAD_INGOT = material("lead_ingot");
    public static final DeferredItem<Item> LEAD_PLATE = material("lead_plate");
    public static final DeferredItem<Item> RAW_URANIUM = material("raw_uranium");
    public static final DeferredItem<Item> URANIUM_DUST = material("uranium_dust");
    public static final DeferredItem<Item> URANIUM_INGOT = material("uranium_ingot");
    public static final DeferredItem<Item> ENRICHED_URANIUM = material("enriched_uranium");
    public static final DeferredItem<Item> DEPLETED_URANIUM = material("depleted_uranium");
    public static final DeferredItem<Item> FUEL_ROD = material("fuel_rod", 16);
    public static final DeferredItem<Item> DEPLETED_FUEL_ROD = material("depleted_fuel_rod", 16);
    public static final DeferredItem<Item> NUCLEAR_WASTE = material("nuclear_waste");
    public static final DeferredItem<Item> RADIOISOTOPE_PELLET = material("radioisotope_pellet", 16);
    public static final DeferredItem<Item> EMPTY_CELL = material("empty_cell");
    public static final DeferredItem<Item> COOLANT_CELL = material("coolant_cell", 16);
    public static final DeferredItem<Item> DEUTERIUM_CELL = material("deuterium_cell", 16);
    public static final DeferredItem<Item> TRITIUM_CELL = material("tritium_cell", 16);

    /** Helium-3: the planets content may register it (mined on the Moon); if not, we do (see {@link #register}). */
    public static final Identifier HELIUM_3_ID = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "helium_3");
    private static boolean ownsHelium3;
    private static Item helium3;

    public static Item helium3() {
        if (helium3 == null || helium3 == Items.AIR) helium3 = BuiltInRegistries.ITEM.getValue(HELIUM_3_ID);
        return helium3;
    }

    /** Whether helium_3 was registered here (no other content provides it). */
    public static boolean ownsHelium3() {
        return ownsHelium3;
    }

    // ---------------------------------------------------------------- gear

    public static final ResourceKey<EquipmentAsset> HAZMAT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "hazmat"));
    public static final ArmorMaterial HAZMAT = new ArmorMaterial(18,
            Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 3, ArmorType.CHESTPLATE, 4, ArmorType.HELMET, 1, ArmorType.BODY, 3),
            8, SoundEvents.ARMOR_EQUIP_LEATHER, 0f, 0.1f,
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/lead")), HAZMAT_ASSET);

    public static final DeferredItem<GeigerCounterItem> GEIGER_COUNTER = tool(ITEMS.registerItem("geiger_counter",
            GeigerCounterItem::new, p -> p.stacksTo(1)));
    public static final DeferredItem<HazmatSuitItem> HAZMAT_HELMET = hazmat("hazmat_helmet", ArmorType.HELMET);
    public static final DeferredItem<HazmatSuitItem> HAZMAT_CHESTPLATE = hazmat("hazmat_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<HazmatSuitItem> HAZMAT_LEGGINGS = hazmat("hazmat_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<HazmatSuitItem> HAZMAT_BOOTS = hazmat("hazmat_boots", ArmorType.BOOTS);

    private static <T extends Item> DeferredItem<T> tool(DeferredItem<T> item) {
        TOOLS_TAB.add(item);
        return item;
    }

    private static DeferredItem<HazmatSuitItem> hazmat(String name, ArmorType type) {
        return tool(ITEMS.registerItem(name, HazmatSuitItem::new, p -> p.humanoidArmor(HAZMAT, type)));
    }

    // ---------------------------------------------------------------- block entities

    static {
        for (Generator g : Generator.VALUES) {
            GENERATOR_TYPES.put(g, BLOCK_ENTITIES.register(g.id(), () -> new BlockEntityType<PowerBlockEntity>(
                    (pos, state) -> switch (g) {
                        case KINETIC_DYNAMO -> new KineticDynamoBlockEntity(pos, state);
                        case STEAM_ENGINE -> new SteamEngineBlockEntity(pos, state);
                        case WIND_TURBINE -> new WindTurbineBlockEntity(pos, state);
                        case BIOGAS_GENERATOR -> new BiogasGeneratorBlockEntity(pos, state);
                        case MAGMATIC_GENERATOR -> new MagmaticGeneratorBlockEntity(pos, state);
                        case SOLAR_ARRAY -> new SolarArrayBlockEntity(pos, state);
                        case RTG -> new RtgBlockEntity(pos, state);
                    }, GENERATORS.get(g).get())));
        }
    }

    public static final Supplier<BlockEntityType<ReactorControllerBlockEntity>> REACTOR_CONTROLLER_BE = BLOCK_ENTITIES.register(
            "reactor_controller", () -> new BlockEntityType<>(ReactorControllerBlockEntity::new, REACTOR_CONTROLLER.get()));
    public static final Supplier<BlockEntityType<ReactorPortBlockEntity>> REACTOR_PORT_BE = BLOCK_ENTITIES.register(
            "reactor_port", () -> new BlockEntityType<>(ReactorPortBlockEntity::new, REACTOR_ACCESS_PORT.get(), REACTOR_POWER_PORT.get(),
                    REACTOR_COOLANT_PORT.get(), REACTOR_REDSTONE_PORT.get()));
    public static final Supplier<BlockEntityType<WasteBarrelBlockEntity>> WASTE_BARREL_BE = BLOCK_ENTITIES.register(
            "waste_barrel", () -> new BlockEntityType<>(WasteBarrelBlockEntity::new, WASTE_BARREL.get()));
    public static final Supplier<BlockEntityType<FusionPortBlockEntity>> FUSION_PORT_BE = BLOCK_ENTITIES.register(
            "fusion_port", () -> new BlockEntityType<>(FusionPortBlockEntity::new, FUSION_PORT.get()));
    public static final Supplier<BlockEntityType<TokamakCoreBlockEntity>> TOKAMAK_CORE_BE = BLOCK_ENTITIES.register(
            "tokamak_core", () -> new BlockEntityType<>(TokamakCoreBlockEntity::new, TOKAMAK_CORE.get()));

    // ---------------------------------------------------------------- menus, effect, sounds, attachment

    public static final Supplier<MenuType<PowerMenu>> POWER_MENU = MENUS.register("power_generator",
            () -> IMenuTypeExtension.create(PowerMenu::fromNetwork));
    public static final Supplier<MenuType<ReactorMenu>> REACTOR_MENU = MENUS.register("reactor",
            () -> IMenuTypeExtension.create(ReactorMenu::fromNetwork));
    public static final Supplier<MenuType<TokamakMenu>> TOKAMAK_MENU = MENUS.register("tokamak",
            () -> IMenuTypeExtension.create(TokamakMenu::fromNetwork));

    public static final DeferredHolder<MobEffect, RadiationEffect> RADIATION = EFFECTS.register("radiation", RadiationEffect::new);

    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_CLICK = sound("geiger_click");
    public static final DeferredHolder<SoundEvent, SoundEvent> REACTOR_ALARM = sound("reactor_alarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_HUM = sound("fusion_hum");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name)));
    }

    /** A player's absorbed radiation dose and current dose rate, synced to that player only. */
    public static final Supplier<AttachmentType<RadiationState>> RADIATION_STATE = ATTACHMENTS.register("radiation",
            () -> AttachmentType.builder(() -> RadiationState.NONE).serialize(RadiationState.MAP_CODEC)
                    .sync((holder, to) -> holder == to, RadiationState.STREAM_CODEC).build());

    private PowerContent() {}

    // ---------------------------------------------------------------- lookups

    public static DeferredBlock<PowerBlock> generator(Generator g) {
        return GENERATORS.get(g);
    }

    public static Supplier<BlockEntityType<PowerBlockEntity>> generatorType(Generator g) {
        return GENERATOR_TYPES.get(g);
    }

    // ---------------------------------------------------------------- registration

    /** Called from the mod constructor. */
    public static void register(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, PowerConfig.SPEC, FactoryAscent.MOD_ID + "-power-server.toml");
        modBus.addListener(PowerContent::registerCapabilities);
        // Helium-3 belongs to the Moon; register it only if nothing else (the planets content) did.
        modBus.addListener(EventPriority.LOWEST, (RegisterEvent e) -> {
            if (!e.getRegistryKey().equals(Registries.ITEM) || BuiltInRegistries.ITEM.containsKey(HELIUM_3_ID)) return;
            ownsHelium3 = true;
            e.register(Registries.ITEM, HELIUM_3_ID, () -> new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, HELIUM_3_ID))));
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (!e.getEntity().level().isClientSide() && e.getEntity().tickCount % 20 == 0) Radiation.tick(e.getEntity());
        });
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent e) {
        for (Generator g : Generator.VALUES) {
            var type = GENERATOR_TYPES.get(g).get();
            e.registerBlockEntity(Capabilities.Item.BLOCK, type, (be, side) -> be.itemHandler(side));
            e.registerBlockEntity(Capabilities.Energy.BLOCK, type, (be, side) -> be.energyHandler(side));
        }
        e.registerBlockEntity(Capabilities.Item.BLOCK, REACTOR_CONTROLLER_BE.get(), (be, side) -> be.itemHandler());
        e.registerBlockEntity(Capabilities.Energy.BLOCK, REACTOR_CONTROLLER_BE.get(), (be, side) -> be.energyHandler());
        e.registerBlockEntity(Capabilities.Item.BLOCK, REACTOR_PORT_BE.get(), (be, side) -> be.itemHandler());
        e.registerBlockEntity(Capabilities.Energy.BLOCK, REACTOR_PORT_BE.get(), (be, side) -> be.energyHandler());
        e.registerBlockEntity(Capabilities.Item.BLOCK, WASTE_BARREL_BE.get(), (be, side) -> VanillaContainerWrapper.of(be));
        e.registerBlockEntity(Capabilities.Item.BLOCK, FUSION_PORT_BE.get(), (be, side) -> be.itemHandler());
        e.registerBlockEntity(Capabilities.Energy.BLOCK, FUSION_PORT_BE.get(), (be, side) -> be.energyHandler());
    }

    /** Power tab, in age order. */
    public static List<Supplier<? extends ItemLike>> powerItems() {
        return POWER_TAB;
    }

    public static List<Supplier<? extends ItemLike>> materialItems() {
        List<Supplier<? extends ItemLike>> list = new ArrayList<>(MATERIALS_TAB);
        if (ownsHelium3) list.add(PowerContent::helium3);
        return list;
    }

    public static List<Supplier<? extends ItemLike>> toolItems() {
        return TOOLS_TAB;
    }

    public static List<Supplier<? extends ItemLike>> worldItems() {
        return WORLD_TAB;
    }

    /** Grants a code-triggered advancement (criterion "done"). */
    public static void award(ServerPlayer player, String key) {
        var server = player.level().getServer();
        if (server == null) return;
        AdvancementHolder holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, key));
        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
            player.getAdvancements().award(holder, "done");
        }
    }
}
