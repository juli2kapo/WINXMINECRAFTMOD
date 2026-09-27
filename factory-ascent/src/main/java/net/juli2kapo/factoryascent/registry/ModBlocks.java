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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FactoryAscent.MOD_ID);

    private static final Map<MachineType, EnumMap<Tier, DeferredBlock<MachineBlock>>> MACHINES = new EnumMap<>(MachineType.class);
    public static final EnumMap<Tier, DeferredBlock<PowerCableBlock>> POWER_CABLES = new EnumMap<>(Tier.class);
    public static final EnumMap<Tier, DeferredBlock<ItemPipeBlock>> ITEM_PIPES = new EnumMap<>(Tier.class);
    /** Ores and storage blocks, by registry name, in creative-tab order. */
    public static final Map<String, DeferredBlock<Block>> SIMPLE = new LinkedHashMap<>();

    static {
        for (MachineType type : MachineType.VALUES) {
            EnumMap<Tier, DeferredBlock<MachineBlock>> byTier = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.VALUES) {
                byTier.put(tier, BLOCKS.registerBlock(tier.id() + "_" + type.id(),
                        p -> new MachineBlock(type, tier, p), () -> machineProperties(type)));
            }
            MACHINES.put(type, byTier);
        }
        for (Tier tier : Tier.VALUES) {
            POWER_CABLES.put(tier, BLOCKS.registerBlock(tier.id() + "_power_cable", p -> new PowerCableBlock(tier, p),
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.6f).sound(SoundType.WOOL).noOcclusion()));
            ITEM_PIPES.put(tier, BLOCKS.registerBlock(tier.id() + "_item_pipe", p -> new ItemPipeBlock(tier, p),
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8f).sound(SoundType.COPPER).noOcclusion()));
        }
        ore("tin_ore", false);
        ore("deepslate_tin_ore", true);
        ore("bauxite_ore", false);
        ore("deepslate_bauxite_ore", true);
        ore("deepslate_titanium_ore", true);
        for (String metal : new String[]{"tin", "bronze", "steel", "aluminum", "titanium", "quantum_alloy"}) {
            SIMPLE.put(metal + "_block", BLOCKS.registerSimpleBlock(metal + "_block",
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 6f)
                            .requiresCorrectToolForDrops().sound(SoundType.METAL)));
        }
    }

    private static void ore(String name, boolean deepslate) {
        SIMPLE.put(name, BLOCKS.registerSimpleBlock(name, () -> BlockBehaviour.Properties.of()
                .mapColor(deepslate ? MapColor.DEEPSLATE : MapColor.STONE)
                .strength(deepslate ? 4.5f : 3f, 3f)
                .requiresCorrectToolForDrops()
                .sound(deepslate ? SoundType.DEEPSLATE : SoundType.STONE)));
    }

    private static BlockBehaviour.Properties machineProperties(MachineType type) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5f, 6f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
        if (type == MachineType.SOLAR_PANEL) p = p.noOcclusion();
        if (type == MachineType.ELECTRIC_FURNACE || type == MachineType.ALLOY_SMELTER
                || type == MachineType.COMBUSTION_GENERATOR || type == MachineType.GEOTHERMAL_GENERATOR) {
            p = p.lightLevel(state -> MachineBlock.isActive(state) ? 12 : 0);
        }
        return p;
    }

    public static DeferredBlock<MachineBlock> machine(MachineType type, Tier tier) {
        return MACHINES.get(type).get(tier);
    }

    private ModBlocks() {}
}
