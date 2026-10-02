package net.juli2kapo.factoryascent.fluid;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.fluid.machine.BiogasDigesterBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.BoilerBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.DieselGeneratorBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachine;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachineBlock;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachineBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachineMenu;
import net.juli2kapo.factoryascent.fluid.machine.FuellingPortBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.OilDerrickBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.PumpBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.RefineryBlockEntity;
import net.juli2kapo.factoryascent.fluid.machine.RefineryTowerBlock;
import net.juli2kapo.factoryascent.fluid.machine.SteamTurbineBlockEntity;
import net.juli2kapo.factoryascent.fluid.pipe.FluidNetworkManager;
import net.juli2kapo.factoryascent.fluid.pipe.FluidPipeBlock;
import net.juli2kapo.factoryascent.fluid.pipe.FluidPipeBlockEntity;
import net.juli2kapo.factoryascent.fluid.pipe.FluidPipePayloads;
import net.juli2kapo.factoryascent.fluid.tank.FluidTankBlock;
import net.juli2kapo.factoryascent.fluid.tank.FluidTankBlockEntity;
import net.juli2kapo.factoryascent.fluid.tank.FluidTankItem;
import net.juli2kapo.factoryascent.fluid.tank.TankSize;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
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
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Real fluids for Factory Ascent: the fluids themselves ({@link ModFluids}), fluid pipes in the
 * four stage colours, tanks, the pump, steam (Boiler, Steam Turbine), biogas (Digester), the oil
 * chain (Oil Derrick, Refinery, Diesel Generator, Plastic, Tar, Asphalt) and the Fuelling Port.
 * Machines of other features expose their tanks as fluid handlers too (see {@link #registerCapabilities}).
 */
public final class FluidContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FactoryAscent.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);

    private static final List<Supplier<? extends ItemLike>> LOGISTICS_TAB = new ArrayList<>();
    private static final List<Supplier<? extends ItemLike>> POWER_TAB = new ArrayList<>();
    private static final List<Supplier<? extends ItemLike>> PROCESSING_TAB = new ArrayList<>();
    private static final List<Supplier<? extends ItemLike>> MATERIALS_TAB = new ArrayList<>();
    private static final List<Supplier<? extends ItemLike>> WORLD_TAB = new ArrayList<>();

    public static final Map<Tier, String> PIPE_NAMES = Map.of(Tier.LV, "bronze_fluid_pipe", Tier.MV, "steel_fluid_pipe",
            Tier.HV, "aluminum_fluid_pipe", Tier.EV, "titanium_fluid_pipe");

    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    // ---------------------------------------------------------------- pipes & tanks

    public static final EnumMap<Tier, DeferredBlock<FluidPipeBlock>> FLUID_PIPES = new EnumMap<>(Tier.class);
    public static final EnumMap<TankSize, DeferredBlock<FluidTankBlock>> TANKS = new EnumMap<>(TankSize.class);

    static {
        for (Tier tier : Tier.VALUES) {
            FLUID_PIPES.put(tier, BLOCKS.registerBlock(PIPE_NAMES.get(tier), p -> new FluidPipeBlock(tier, p),
                    () -> metal(MapColor.METAL).strength(1.5f, 6f).noOcclusion().forceSolidOn()));
        }
        for (TankSize size : TankSize.VALUES) {
            TANKS.put(size, BLOCKS.registerBlock(size.id(), p -> new FluidTankBlock(size, p),
                    () -> metal(MapColor.METAL).strength(3f, 8f).noOcclusion()
                            .isValidSpawn((s, l, pos, e) -> false).isRedstoneConductor((s, l, pos) -> false)
                            .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos) -> false)));
        }
    }

    // ---------------------------------------------------------------- machines and parts

    private static final Map<FluidMachine, DeferredBlock<FluidMachineBlock>> MACHINES = new EnumMap<>(FluidMachine.class);
    private static final Map<FluidMachine, Supplier<BlockEntityType<FluidMachineBlockEntity>>> MACHINE_TYPES = new EnumMap<>(FluidMachine.class);

    private static BlockBehaviour.Properties machineProps(FluidMachine m) {
        BlockBehaviour.Properties p = switch (m) {
            case BOILER -> metal(MapColor.COLOR_RED).lightLevel(s -> s.getValue(FluidMachineBlock.ACTIVE) ? 10 : 0);
            case BIOGAS_DIGESTER -> metal(MapColor.COLOR_GREEN);
            case DIESEL_GENERATOR -> metal(MapColor.COLOR_YELLOW).lightLevel(s -> s.getValue(FluidMachineBlock.ACTIVE) ? 6 : 0);
            case OIL_DERRICK -> metal(MapColor.COLOR_BLACK);
            case REFINERY -> metal(MapColor.COLOR_LIGHT_GRAY).lightLevel(s -> s.getValue(FluidMachineBlock.ACTIVE) ? 8 : 0);
            case STEAM_TURBINE -> metal(MapColor.COLOR_BLUE);
            case FUELLING_PORT -> metal(MapColor.COLOR_ORANGE);
            default -> metal(MapColor.METAL);
        };
        return p.noOcclusion(); // shaped models (insets, pipes, gauges): never hide a neighbour's faces
    }

    static {
        for (FluidMachine m : FluidMachine.VALUES) {
            MACHINES.put(m, BLOCKS.registerBlock(m.id(), p -> new FluidMachineBlock(m, p), () -> machineProps(m)));
        }
    }

    public static final DeferredBlock<Block> DERRICK_BASE = BLOCKS.registerSimpleBlock("derrick_base",
            () -> metal(MapColor.COLOR_GRAY).strength(4f, 8f));
    public static final DeferredBlock<RefineryTowerBlock> REFINERY_TOWER = BLOCKS.registerBlock("refinery_tower", RefineryTowerBlock::new,
            () -> metal(MapColor.COLOR_LIGHT_GRAY).noOcclusion());
    /** A road: dark, solid, and fast to walk or ride on. */
    public static final DeferredBlock<Block> ASPHALT = BLOCKS.registerSimpleBlock("asphalt",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.8f, 6f).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE).speedFactor(1.35f));

    // ---------------------------------------------------------------- block items (tab order)

    static {
        for (Tier tier : Tier.VALUES) LOGISTICS_TAB.add(blockItem(FLUID_PIPES.get(tier)));
        for (TankSize size : TankSize.VALUES) {
            DeferredBlock<FluidTankBlock> b = TANKS.get(size);
            LOGISTICS_TAB.add(ITEMS.registerItem(b.getId().getPath(), p -> new FluidTankItem(b.get(), p), p -> p.useBlockDescriptionPrefix()));
        }
        LOGISTICS_TAB.add(blockItem(MACHINES.get(FluidMachine.PUMP)));
        LOGISTICS_TAB.add(blockItem(MACHINES.get(FluidMachine.FUELLING_PORT)));
        for (FluidMachine m : List.of(FluidMachine.BOILER, FluidMachine.BIOGAS_DIGESTER, FluidMachine.DIESEL_GENERATOR, FluidMachine.STEAM_TURBINE)) {
            POWER_TAB.add(blockItem(MACHINES.get(m)));
        }
        PROCESSING_TAB.add(blockItem(MACHINES.get(FluidMachine.OIL_DERRICK)));
        PROCESSING_TAB.add(blockItem(DERRICK_BASE));
        PROCESSING_TAB.add(blockItem(MACHINES.get(FluidMachine.REFINERY)));
        PROCESSING_TAB.add(blockItem(REFINERY_TOWER));
        WORLD_TAB.add(blockItem(ASPHALT));
    }

    private static DeferredItem<BlockItem> blockItem(DeferredBlock<?> block) {
        return ITEMS.registerItem(block.getId().getPath(), p -> new FactoryBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix());
    }

    public static final DeferredItem<Item> PLASTIC = material("plastic");
    public static final DeferredItem<Item> TAR = material("tar");

    private static DeferredItem<Item> material(String name) {
        DeferredItem<Item> item = ITEMS.registerItem(name, Item::new, p -> p);
        MATERIALS_TAB.add(item);
        return item;
    }

    // ---------------------------------------------------------------- block entities, menu, components

    static {
        for (FluidMachine m : FluidMachine.VALUES) {
            MACHINE_TYPES.put(m, BLOCK_ENTITIES.register(m.id(), () -> new BlockEntityType<FluidMachineBlockEntity>(
                    (pos, state) -> switch (m) {
                        case BOILER -> new BoilerBlockEntity(pos, state);
                        case PUMP -> new PumpBlockEntity(pos, state);
                        case BIOGAS_DIGESTER -> new BiogasDigesterBlockEntity(pos, state);
                        case DIESEL_GENERATOR -> new DieselGeneratorBlockEntity(pos, state);
                        case OIL_DERRICK -> new OilDerrickBlockEntity(pos, state);
                        case REFINERY -> new RefineryBlockEntity(pos, state);
                        case STEAM_TURBINE -> new SteamTurbineBlockEntity(pos, state);
                        case FUELLING_PORT -> new FuellingPortBlockEntity(pos, state);
                    }, MACHINES.get(m).get())));
        }
    }

    public static final Supplier<BlockEntityType<FluidPipeBlockEntity>> FLUID_PIPE_BE = BLOCK_ENTITIES.register("fluid_pipe",
            () -> new BlockEntityType<>(FluidPipeBlockEntity::new, FLUID_PIPES.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));
    public static final Supplier<BlockEntityType<FluidTankBlockEntity>> FLUID_TANK_BE = BLOCK_ENTITIES.register("fluid_tank",
            () -> new BlockEntityType<>(FluidTankBlockEntity::new, TANKS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)));

    public static final Supplier<MenuType<FluidMachineMenu>> MACHINE_MENU = MENUS.register("fluid_machine",
            () -> IMenuTypeExtension.create(FluidMachineMenu::fromNetwork));

    /** What a tank item holds (kept when the tank is broken). */
    public static final Supplier<DataComponentType<SimpleFluidContent>> TANK_CONTENTS = COMPONENTS.registerComponentType("tank_contents",
            b -> b.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    private FluidContent() {}

    public static DeferredBlock<FluidMachineBlock> machine(FluidMachine m) {
        return MACHINES.get(m);
    }

    public static Supplier<BlockEntityType<FluidMachineBlockEntity>> machineType(FluidMachine m) {
        return MACHINE_TYPES.get(m);
    }

    // ---------------------------------------------------------------- registration

    public static void register(IEventBus modBus, ModContainer container) {
        ModFluids.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, FluidsConfig.SPEC, FactoryAscent.MOD_ID + "-fluids-server.toml");
        modBus.addListener(FluidContent::registerCapabilities);
        modBus.addListener((RegisterPayloadHandlersEvent e) -> FluidPipePayloads.register(e));
        modBus.addListener(FluidContent::creativeTabs);
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof ServerLevel level) FluidNetworkManager.remove(level);
        });
        FluidIntegration.register(modBus);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent e) {
        for (FluidMachine m : FluidMachine.VALUES) {
            var type = MACHINE_TYPES.get(m).get();
            e.registerBlockEntity(Capabilities.Fluid.BLOCK, type, (be, side) -> be.fluidHandler(side));
            e.registerBlockEntity(Capabilities.Item.BLOCK, type, (be, side) -> be.itemHandler(side));
            e.registerBlockEntity(Capabilities.Energy.BLOCK, type, (be, side) -> be.energyHandler(side));
        }
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_PIPE_BE.get(), (be, side) -> be.fluidHandler(side));
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, FLUID_TANK_BE.get(), (be, side) -> be.fluidHandler(side));
        e.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, be, side) -> RefineryTowerBlock.fluidHandler(level, pos), REFINERY_TOWER.get());
        e.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, be, side) -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (level.getBlockEntity(pos.offset(dx, 0, dz)) instanceof OilDerrickBlockEntity d) return d.fluidHandler(side);
                }
            }
            return null;
        }, DERRICK_BASE.get());
        FluidIntegration.registerCapabilities(e);
    }

    private static final ResourceKey<CreativeModeTab> TAB_LOGISTICS = tab("logistics"), TAB_POWER = tab("power"),
            TAB_PROCESSING = tab("processing"), TAB_MATERIALS = tab("materials"), TAB_WORLD = tab("world");

    private static ResourceKey<CreativeModeTab> tab(String name) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, name));
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey().equals(TAB_LOGISTICS)) LOGISTICS_TAB.forEach(i -> e.accept(i.get()));
        else if (e.getTabKey().equals(TAB_POWER)) POWER_TAB.forEach(i -> e.accept(i.get()));
        else if (e.getTabKey().equals(TAB_PROCESSING)) PROCESSING_TAB.forEach(i -> e.accept(i.get()));
        else if (e.getTabKey().equals(TAB_MATERIALS)) {
            MATERIALS_TAB.forEach(i -> e.accept(i.get()));
            for (ModFluids.Def d : ModFluids.all()) if (d.hasBucket()) e.accept(d.bucket());
        } else if (e.getTabKey().equals(TAB_WORLD)) WORLD_TAB.forEach(i -> e.accept(i.get()));
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
