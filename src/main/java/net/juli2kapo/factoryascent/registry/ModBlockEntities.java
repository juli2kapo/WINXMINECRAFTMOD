package net.juli2kapo.factoryascent.registry;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.energy.PowerCableBlockEntity;
import net.juli2kapo.factoryascent.generator.CombustionGeneratorBlockEntity;
import net.juli2kapo.factoryascent.generator.EnergyCellBlockEntity;
import net.juli2kapo.factoryascent.generator.GeothermalGeneratorBlockEntity;
import net.juli2kapo.factoryascent.generator.SolarPanelBlockEntity;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.machine.ProcessingMachineBlockEntity;
import net.juli2kapo.factoryascent.miner.MinerBlockEntity;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FactoryAscent.MOD_ID);

    private static final Map<MachineType, Supplier<BlockEntityType<AbstractMachineBlockEntity>>> MACHINES =
            new EnumMap<>(MachineType.class);

    static {
        for (MachineType type : MachineType.VALUES) {
            MACHINES.put(type, BLOCK_ENTITIES.register(type.id(), () -> new BlockEntityType<AbstractMachineBlockEntity>(
                    (pos, state) -> switch (type) {
                        case MINER -> new MinerBlockEntity(pos, state);
                        case COMBUSTION_GENERATOR -> new CombustionGeneratorBlockEntity(pos, state);
                        case SOLAR_PANEL -> new SolarPanelBlockEntity(pos, state);
                        case GEOTHERMAL_GENERATOR -> new GeothermalGeneratorBlockEntity(pos, state);
                        case ENERGY_CELL -> new EnergyCellBlockEntity(pos, state);
                        default -> new ProcessingMachineBlockEntity(type, pos, state);
                    },
                    blocks(type))));
        }
    }

    public static final Supplier<BlockEntityType<PowerCableBlockEntity>> POWER_CABLE = BLOCK_ENTITIES.register("power_cable",
            () -> new BlockEntityType<>(PowerCableBlockEntity::new, collect(ModBlocks.POWER_CABLES.values())));
    public static final Supplier<BlockEntityType<ItemPipeBlockEntity>> ITEM_PIPE = BLOCK_ENTITIES.register("item_pipe",
            () -> new BlockEntityType<>(ItemPipeBlockEntity::new, collect(ModBlocks.ITEM_PIPES.values())));

    private static Set<Block> blocks(MachineType type) {
        Set<Block> set = new HashSet<>();
        for (Tier tier : Tier.VALUES) set.add(ModBlocks.machine(type, tier).get());
        return set;
    }

    private static Set<Block> collect(Iterable<? extends Supplier<? extends Block>> blocks) {
        Set<Block> set = new HashSet<>();
        for (var b : blocks) set.add(b.get());
        return set;
    }

    public static Supplier<BlockEntityType<AbstractMachineBlockEntity>> machine(MachineType type) {
        return MACHINES.get(type);
    }

    private ModBlockEntities() {}
}
