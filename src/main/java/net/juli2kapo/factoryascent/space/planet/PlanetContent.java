package net.juli2kapo.factoryascent.space.planet;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The planets' blocks and materials, found only there ({@link Planet}): Moon regolith and rock with
 * Helium-3-rich regolith; Martian sand and rock with hematite ore and buried ice; Io's volcanic rock
 * with Ionite crystals. Also what they are for: the Helium-3 Fuel Cell (shuttle fuel), the suit's
 * Thermal Lining (Io's heat), the Ion Drive (cheaper, faster trips) and the Star Chart; and the
 * crater feature of the Moon and Mars.
 */
public final class PlanetContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, FactoryAscent.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, FactoryAscent.MOD_ID);

    private static final List<DeferredItem<? extends Item>> CREATIVE = new ArrayList<>();

    // ---------------------------------------------------------------- blocks

    private static BlockBehaviour.Properties rock(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
                .strength(1.5f, 6f).requiresCorrectToolForDrops().sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties soil(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(0.6f).sound(SoundType.SAND);
    }

    private static DeferredBlock<Block> block(String name, Supplier<BlockBehaviour.Properties> props) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, props);
        CREATIVE.add(ITEMS.registerItem(name, p -> new FactoryBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix()));
        return block;
    }

    public static final DeferredBlock<Block> MOON_REGOLITH = block("moon_regolith", () -> soil(MapColor.COLOR_LIGHT_GRAY));
    public static final DeferredBlock<Block> MOON_ROCK = block("moon_rock", () -> rock(MapColor.STONE));
    public static final DeferredBlock<Block> HELIUM_3_REGOLITH = block("helium_3_regolith",
            () -> soil(MapColor.COLOR_LIGHT_BLUE).strength(1.2f).lightLevel(s -> 3));
    public static final DeferredBlock<Block> MARS_SAND = block("mars_sand", () -> soil(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<Block> MARS_ROCK = block("mars_rock", () -> rock(MapColor.TERRACOTTA_RED));
    public static final DeferredBlock<Block> HEMATITE_ORE = block("hematite_ore", () -> rock(MapColor.TERRACOTTA_RED).strength(3f, 3f));
    public static final DeferredBlock<Block> MARTIAN_ICE = block("martian_ice", () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.ICE).strength(0.8f).friction(0.98f).sound(SoundType.GLASS));
    public static final DeferredBlock<Block> IO_ROCK = block("io_rock", () -> rock(MapColor.COLOR_BLACK));
    public static final DeferredBlock<Block> IONITE_ORE = block("ionite_ore", () -> rock(MapColor.COLOR_BLACK).strength(3.5f, 3f)
            .lightLevel(s -> 6));

    // ---------------------------------------------------------------- items

    private static DeferredItem<Item> item(String name) {
        DeferredItem<Item> item = ITEMS.registerSimpleItem(name);
        CREATIVE.add(item);
        return item;
    }

    /** Helium-3, from the Moon's regolith: fusion fuel (the Helium-3 Fuel Cell, reactors). */
    public static final DeferredItem<Item> HELIUM_3 = item("helium_3");
    public static final DeferredItem<Item> RAW_HEMATITE = item("raw_hematite");
    public static final DeferredItem<Item> IONITE = item("ionite");
    public static final DeferredItem<Item> HELIUM_3_FUEL_CELL = item("helium_3_fuel_cell");
    public static final DeferredItem<ThermalLiningItem> THERMAL_LINING = register("thermal_lining", ThermalLiningItem::new);
    public static final DeferredItem<IonDriveItem> ION_DRIVE = register("ion_drive", IonDriveItem::new);
    public static final DeferredItem<StarChartItem> STAR_CHART = register("star_chart", StarChartItem::new);

    private static <T extends Item> DeferredItem<T> register(String name, java.util.function.Function<Item.Properties, T> factory) {
        DeferredItem<T> item = ITEMS.registerItem(name, factory, p -> p.stacksTo(name.equals("ion_drive") ? 1 : 16));
        CREATIVE.add(item);
        return item;
    }

    // ---------------------------------------------------------------- world gen and data

    public static final DeferredHolder<Feature<?>, CraterFeature> CRATER = FEATURES.register("crater",
            () -> new CraterFeature(NoneFeatureConfiguration.CODEC));

    /** A suit (chest) with a Thermal Lining sewn in: safe on Io's surface. */
    public static final Supplier<DataComponentType<Boolean>> LINED = COMPONENTS.registerComponentType("thermal_lining",
            b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    private PlanetContent() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FEATURES.register(modBus);
        COMPONENTS.register(modBus);
    }

    /** For the Orbital creative tab. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return new ArrayList<>(CREATIVE);
    }

    /** Shuttle tank units one item of fuel adds (0: not a shuttle fuel), given the Rocket Fuel value. */
    public static int shuttleFuelValue(net.minecraft.world.item.ItemStack stack, int rocketFuelUnits) {
        if (stack.is(net.juli2kapo.factoryascent.orbital.OrbitalContent.ROCKET_FUEL.get())) return rocketFuelUnits;
        if (stack.is(HELIUM_3_FUEL_CELL.get())) return rocketFuelUnits * 4;
        if (stack.is(net.juli2kapo.factoryascent.outpost.OutpostContent.HYDROLOX_FUEL_CELL.get())) return rocketFuelUnits * 2;
        return 0;
    }
}
