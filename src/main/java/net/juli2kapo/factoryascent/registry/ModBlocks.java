package net.juli2kapo.factoryascent.registry;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.energy.PowerCableBlock;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlock;
import net.juli2kapo.factoryascent.storage.CrateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);

    /** Cable names per infrastructure tier (GregTech-style: the conductor material is the tier). */
    public static final Map<Tier, String> CABLE_NAMES = Map.of(
            Tier.LV, "copper_cable", Tier.MV, "aluminum_cable", Tier.HV, "titanium_cable", Tier.EV, "superconductor_cable");
    public static final Map<Tier, String> PIPE_NAMES = Map.of(
            Tier.LV, "bronze_item_pipe", Tier.MV, "steel_item_pipe", Tier.HV, "aluminum_item_pipe", Tier.EV, "titanium_item_pipe");

    private static final Map<MachineType, DeferredBlock<MachineBlock>> MACHINES = new EnumMap<>(MachineType.class);
    public static final EnumMap<Tier, DeferredBlock<PowerCableBlock>> POWER_CABLES = new EnumMap<>(Tier.class);
    public static final EnumMap<Tier, DeferredBlock<ItemPipeBlock>> ITEM_PIPES = new EnumMap<>(Tier.class);
    /** Ores, storage and structure blocks by registry name, in creative-tab order. */
    public static final Map<String, DeferredBlock<Block>> SIMPLE = new LinkedHashMap<>();

    public static final DeferredBlock<CrateBlock> WOODEN_CRATE = BLOCKS.registerBlock("wooden_crate",
            p -> new CrateBlock(3, p), () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD));
    public static final DeferredBlock<CrateBlock> BRONZE_CRATE = BLOCKS.registerBlock("bronze_crate",
            p -> new CrateBlock(6, p), () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3f, 6f)
                    .requiresCorrectToolForDrops().sound(SoundType.COPPER));
    public static final DeferredBlock<Block> COKE_OVEN_BRICKS = simple("coke_oven_bricks",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2f, 6f).requiresCorrectToolForDrops().sound(SoundType.STONE));
    public static final DeferredBlock<Block> FIRE_BRICKS = simple("fire_bricks",
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).strength(2.5f, 8f).requiresCorrectToolForDrops().sound(SoundType.STONE));

    static {
        for (MachineType type : MachineType.VALUES) {
            MACHINES.put(type, BLOCKS.registerBlock(type.id(), p -> new MachineBlock(type, p), () -> machineProperties(type)));
        }
        for (Tier tier : Tier.VALUES) {
            POWER_CABLES.put(tier, BLOCKS.registerBlock(CABLE_NAMES.get(tier), p -> new PowerCableBlock(tier, p),
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.6f).sound(SoundType.WOOL).noOcclusion()));
            ITEM_PIPES.put(tier, BLOCKS.registerBlock(PIPE_NAMES.get(tier), p -> new ItemPipeBlock(tier, p),
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.COPPER).noOcclusion()));
        }
        ore("tin_ore", false);
        ore("deepslate_tin_ore", true);
        ore("bauxite_ore", false);
        ore("deepslate_titanium_ore", true);
        for (String metal : new String[]{"tin", "bronze", "steel", "aluminum", "titanium", "quantum_alloy"}) {
            simple(metal + "_block", BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 6f)
                    .requiresCorrectToolForDrops().sound(SoundType.METAL));
        }
    }

    private static DeferredBlock<Block> simple(String name, BlockBehaviour.Properties properties) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, () -> properties);
        SIMPLE.put(name, block);
        return block;
    }

    private static void ore(String name, boolean deepslate) {
        simple(name, BlockBehaviour.Properties.of()
                .mapColor(deepslate ? MapColor.DEEPSLATE : MapColor.STONE)
                .strength(deepslate ? 4.5f : 3f, 3f)
                .requiresCorrectToolForDrops()
                .sound(deepslate ? SoundType.DEEPSLATE : SoundType.STONE));
    }

    private static BlockBehaviour.Properties machineProperties(MachineType type) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().strength(3.5f, 6f).requiresCorrectToolForDrops();
        p = switch (type.age()) {
            case STONE -> p.mapColor(MapColor.STONE).sound(SoundType.STONE).strength(2.5f, 6f);
            case BRONZE -> p.mapColor(MapColor.COLOR_ORANGE).sound(SoundType.COPPER);
            default -> p.mapColor(MapColor.METAL).sound(SoundType.METAL);
        };
        if (type == MachineType.COKE_OVEN) p = p.mapColor(MapColor.COLOR_BROWN).sound(SoundType.STONE);
        if (type == MachineType.BLAST_FURNACE) p = p.mapColor(MapColor.TERRACOTTA_YELLOW).sound(SoundType.STONE);
        if (type.isWooden()) {
            // Wooden stone-age contraptions: an axe job, and they drop without the right tool.
            p = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f, 3f).sound(SoundType.WOOD).noOcclusion();
        }
        if (type == MachineType.SOLAR_PANEL || type == MachineType.QUERN || type == MachineType.FLOODLIGHT
                || type == MachineType.VACUUM_HOPPER || type == MachineType.CHARGER) p = p.noOcclusion();
        if (type == MachineType.FLOODLIGHT) p = p.lightLevel(state -> MachineBlock.isActive(state) ? 15 : 0);
        if (type.power() == MachineType.Power.FUEL || type == MachineType.ELECTRIC_FURNACE
                || type == MachineType.ALLOY_SMELTER || type == MachineType.COKE_OVEN || type == MachineType.GEOTHERMAL_GENERATOR) {
            p = p.lightLevel(state -> MachineBlock.isActive(state) ? 12 : 0);
        }
        return p;
    }

    public static DeferredBlock<MachineBlock> machine(MachineType type) {
        return MACHINES.get(type);
    }

    private ModBlocks() {}
}
