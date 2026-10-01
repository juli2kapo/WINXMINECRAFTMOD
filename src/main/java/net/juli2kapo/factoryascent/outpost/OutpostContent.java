package net.juli2kapo.factoryascent.outpost;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Planetary outposts: what the planets' materials are for, and getting home from them.
 *
 * <ul>
 *   <li>Moon: Lunar Glass (smelted regolith, clear and blast-proof) for station windows, solar arrays
 *       and the Dyson Receiver Array; Helium-3 goes into Stellar Alloy.</li>
 *   <li>Mars: Martian Steel (hematite alloyed with steel) for planetary-grade parts: Docking Ports,
 *       Mass Driver rails, fusion casing, the Ascent Module; Martian ice split into Oxygen Cells and
 *       synthesized into Hydrolox shuttle fuel.</li>
 *   <li>Io: Sulfur (crushed out of Io rock) for gunpowder and Sulfuric Acid Cells, which etch Ionite
 *       into Quantum Circuits: the Dyson Cube's brains.</li>
 *   <li>Decorative stone families of Moon, Mars and Io rock (polished, bricks, tiles, brick slabs,
 *       stairs and walls), airtight for station builders.</li>
 *   <li>Getting home: the Ascent Module ({@link AscentModuleBlock}), the Distress Beacon
 *       ({@link DistressBeaconBlock}) and the Fuel Synthesizer (a machine, {@code MachineType.FUEL_SYNTHESIZER}).</li>
 * </ul>
 */
public final class OutpostContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final ResourceKey<CreativeModeTab> ORBITAL_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "orbital"));
    private static final List<DeferredItem<? extends Item>> CREATIVE = new ArrayList<>();

    // ---------------------------------------------------------------- blocks

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                            Supplier<BlockBehaviour.Properties> props) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, props);
        CREATIVE.add(ITEMS.registerItem(name, p -> new FactoryBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix()));
        return block;
    }

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
                .strength(1.8f, 7f).requiresCorrectToolForDrops().sound(SoundType.STONE);
    }

    /** A decorative family cut from one planet's rock. */
    public record StoneFamily(String base, DeferredBlock<Block> polished, DeferredBlock<Block> bricks, DeferredBlock<Block> tiles,
                              DeferredBlock<SlabBlock> slab, DeferredBlock<StairBlock> stairs, DeferredBlock<WallBlock> wall) {
        public List<DeferredBlock<? extends Block>> all() {
            return List.of(polished, bricks, tiles, slab, stairs, wall);
        }
    }

    private static StoneFamily family(String base, MapColor color) {
        DeferredBlock<Block> polished = block("polished_" + base, Block::new, () -> stone(color));
        DeferredBlock<Block> bricks = block(base + "_bricks", Block::new, () -> stone(color));
        DeferredBlock<Block> tiles = block(base + "_tiles", Block::new, () -> stone(color));
        DeferredBlock<SlabBlock> slab = block(base + "_brick_slab", SlabBlock::new, () -> stone(color));
        DeferredBlock<StairBlock> stairs = block(base + "_brick_stairs", p -> new StairBlock(bricks.get().defaultBlockState(), p),
                () -> stone(color));
        DeferredBlock<WallBlock> wall = block(base + "_brick_wall", WallBlock::new, () -> stone(color).forceSolidOn());
        return new StoneFamily(base, polished, bricks, tiles, slab, stairs, wall);
    }

    public static final DeferredBlock<TransparentBlock> LUNAR_GLASS = block("lunar_glass", TransparentBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.2f, 12f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final StoneFamily MOON_STONE = family("moon_rock", MapColor.STONE);
    public static final StoneFamily MARS_STONE = family("mars_rock", MapColor.TERRACOTTA_RED);
    public static final StoneFamily IO_STONE = family("io_rock", MapColor.COLOR_BLACK);

    public static final DeferredBlock<AscentModuleBlock> ASCENT_MODULE = block("ascent_module", AscentModuleBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 6f).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<DistressBeaconBlock> DISTRESS_BEACON = block("distress_beacon", DistressBeaconBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2f, 6f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(s -> 12));

    // ---------------------------------------------------------------- items

    private static DeferredItem<OutpostItems.Described> described(String name, Function<Item.Properties, Item.Properties> props) {
        DeferredItem<OutpostItems.Described> item = ITEMS.registerItem(name, p -> new OutpostItems.Described(p, name), props::apply);
        CREATIVE.add(item);
        return item;
    }

    /** Sulfur, crushed out of Io's rock: gunpowder, Sulfuric Acid Cells, rocket propellant. */
    public static final DeferredItem<OutpostItems.Described> SULFUR = described("sulfur", p -> p);
    /** Martian Steel: hematite alloyed with steel, for planetary-grade parts. */
    public static final DeferredItem<OutpostItems.Described> MARTIAN_STEEL_INGOT = described("martian_steel_ingot", p -> p);
    /** Sulfuric acid in an Empty Cell: etches Ionite into Quantum Circuits. */
    public static final DeferredItem<OutpostItems.Described> SULFURIC_ACID_CELL = described("sulfuric_acid_cell", p -> p.stacksTo(16));
    /** Ionite etched into a circuit: what the Dyson Cube's machines think with. */
    public static final DeferredItem<OutpostItems.Described> QUANTUM_CIRCUIT = described("quantum_circuit", p -> p.rarity(Rarity.UNCOMMON));
    /** Liquid hydrogen and oxygen from Martian ice (Fuel Synthesizer): shuttle fuel worth 2 Rocket Fuel. */
    public static final DeferredItem<OutpostItems.Described> HYDROLOX_FUEL_CELL = described("hydrolox_fuel_cell", p -> p.stacksTo(16));
    public static final DeferredItem<OutpostItems.OxygenCell> OXYGEN_CELL = register("oxygen_cell", OutpostItems.OxygenCell::new);

    private static <T extends Item> DeferredItem<T> register(String name, Function<Item.Properties, T> factory) {
        DeferredItem<T> item = ITEMS.registerItem(name, factory, p -> p.stacksTo(16));
        CREATIVE.add(item);
        return item;
    }

    private OutpostContent() {}

    /** Called from {@link OutpostMod}. */
    static void register(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, OutpostConfig.SPEC, FactoryAscent.MOD_ID + "-outpost-server.toml");
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().equals(ORBITAL_TAB)) creativeItems().forEach(i -> e.accept(i.get()));
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> OutpostLaunches.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> OutpostLaunches.clear());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> OutpostLaunches.forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && OutpostLaunches.launching(sp)) OutpostLaunches.forget(sp.getUUID());
        });
    }

    /** Items for the Orbital creative tab, in order. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        return new ArrayList<>(CREATIVE);
    }

    public static List<StoneFamily> families() {
        return List.of(MOON_STONE, MARS_STONE, IO_STONE);
    }
}
