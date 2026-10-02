package net.juli2kapo.factoryascent.space.station;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.juli2kapo.factoryascent.space.SpaceConfig;
import net.juli2kapo.factoryascent.space.SpaceContent;
import net.juli2kapo.factoryascent.space.SuitItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Building in space: Station Hull panels (four colours, as blocks, slabs and stairs), the Station
 * Window, the Airlock door (air-tight while closed), the Docking Port (a shuttle parked on it
 * refuels from containers next to it), the Station Core (claims the station for its team and
 * reports its modules, power and air) and Magnetic Boots. Air itself is the Oxygen Sealer's and Air
 * Vent's job ({@link AirVolume}).
 */
public final class StationContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FactoryAscent.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);

    /** Hull colours, in creative-tab order. */
    public static final List<String> COLORS = List.of("white", "gray", "dark", "orange");
    private static final List<DeferredItem<? extends Item>> CREATIVE = new ArrayList<>();

    private static BlockBehaviour.Properties hull(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.0f, 9f).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    private static MapColor mapColor(String color) {
        return switch (color) {
            case "white" -> MapColor.SNOW;
            case "gray" -> MapColor.COLOR_LIGHT_GRAY;
            case "dark" -> MapColor.COLOR_GRAY;
            default -> MapColor.COLOR_ORANGE;
        };
    }

    private static <B extends Block> DeferredBlock<B> withItem(DeferredBlock<B> block) {
        CREATIVE.add(ITEMS.registerItem(block.getId().getPath(), p -> new FactoryBlockItem(block.get(), p), p -> p.useBlockDescriptionPrefix()));
        return block;
    }

    /** colour → [block, slab, stairs] */
    public static final java.util.Map<String, List<DeferredBlock<? extends Block>>> HULLS = new java.util.LinkedHashMap<>();

    static {
        for (String color : COLORS) {
            String name = "station_hull_" + color;
            DeferredBlock<Block> full = withItem(BLOCKS.registerSimpleBlock(name, () -> hull(mapColor(color))));
            DeferredBlock<SlabBlock> slab = withItem(BLOCKS.registerBlock(name + "_slab", SlabBlock::new, () -> hull(mapColor(color))));
            DeferredBlock<StairBlock> stairs = withItem(BLOCKS.registerBlock(name + "_stairs",
                    p -> new StairBlock(full.get().defaultBlockState(), p), () -> hull(mapColor(color))));
            HULLS.put(color, List.of(full, slab, stairs));
        }
    }

    public static final DeferredBlock<TransparentBlock> STATION_WINDOW = withItem(BLOCKS.registerBlock("station_window",
            TransparentBlock::new, () -> BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(2.0f, 9f)
                    .requiresCorrectToolForDrops().sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<DoorBlock> AIRLOCK_DOOR = BLOCKS.registerBlock("airlock_door",
            p -> new DoorBlock(BlockSetType.COPPER, p), () -> hull(MapColor.COLOR_LIGHT_GRAY).noOcclusion().pushReaction(PushReaction.DESTROY));
    public static final DeferredItem<BlockItem> AIRLOCK_DOOR_ITEM = ITEMS.registerItem("airlock_door",
            p -> new DoubleHighBlockItem(AIRLOCK_DOOR.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredBlock<DockingPortBlock> DOCKING_PORT = withItem(BLOCKS.registerBlock("docking_port",
            DockingPortBlock::new, () -> hull(MapColor.COLOR_YELLOW).lightLevel(s -> 7)));
    public static final DeferredBlock<StationCoreBlock> STATION_CORE = withItem(BLOCKS.registerBlock("station_core",
            StationCoreBlock::new, () -> hull(MapColor.COLOR_CYAN).strength(5f, 1200f).lightLevel(s -> 9)));

    public static final Supplier<BlockEntityType<StationCoreBlockEntity>> STATION_CORE_BE = BLOCK_ENTITIES.register("station_core",
            () -> new BlockEntityType<>(StationCoreBlockEntity::new, STATION_CORE.get()));

    public static final DeferredItem<SuitItems.SuitPieceItem> MAGNETIC_BOOTS = ITEMS.registerItem("magnetic_boots",
            p -> new MagneticBootsItem(p), p -> p.humanoidArmor(SuitItems.MATERIAL, ArmorType.BOOTS)
                    .component(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE));

    private StationContent() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(StationPayloads::register);
        NeoForge.EVENT_BUS.addListener(StationContent::onBreak);
        MagneticBoots.register();
    }

    /** For the Orbital creative tab: hulls, window, airlock, port, core, boots. */
    public static List<Supplier<? extends ItemLike>> creativeItems() {
        List<Supplier<? extends ItemLike>> out = new ArrayList<>();
        for (DeferredItem<? extends Item> item : CREATIVE) {
            out.add(item);
            if (item.getId().getPath().equals("station_window")) out.add(AIRLOCK_DOOR_ITEM);
        }
        out.add(MAGNETIC_BOOTS);
        return out;
    }

    /** Inside a claimed station only the owner's team may break blocks (config). */
    private static void onBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        if (!SpaceConfig.get(SpaceConfig.STATION_PROTECTION)) return;
        StationRegistry.Station station = StationRegistry.get(player.level().getServer())
                .claiming(player.level().dimension(), event.getPos(), SpaceConfig.get(SpaceConfig.STATION_RADIUS));
        if (station == null) return;
        String team = net.juli2kapo.factoryascent.orbital.FactoryTeams.get(player.level().getServer()).teamOf(player.getUUID());
        if (!station.team().equals(team)) {
            event.setCanceled(true);
            player.sendOverlayMessage(Component.translatable("message.factoryascent.station_protected", station.name())
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    /** Grants a code-triggered advancement (see {@link SpaceContent#award}). */
    static void award(ServerPlayer player, String key) {
        SpaceContent.award(player, key);
    }
}
