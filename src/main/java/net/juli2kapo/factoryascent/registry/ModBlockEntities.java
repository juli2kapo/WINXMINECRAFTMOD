package net.juli2kapo.factoryascent.registry;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.farm.AutoFarmerBlockEntity;
import net.juli2kapo.factoryascent.storage.CrateBlockEntity;
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
                        case ENERGY_CELL, ADVANCED_ENERGY_CELL, INDUSTRIAL_ENERGY_CELL, QUANTUM_ENERGY_CELL ->
                                new EnergyCellBlockEntity(type, pos, state);
                        case AUTO_FARMER -> new AutoFarmerBlockEntity(pos, state);
                        case WATER_WHEEL, WINDMILL -> new net.juli2kapo.factoryascent.kinetic.KineticBlockEntity(type, pos, state);
                        case CHARGER -> new net.juli2kapo.factoryascent.automation.ChargerBlockEntity(pos, state);
                        case FLOODLIGHT -> new net.juli2kapo.factoryascent.automation.FloodlightBlockEntity(pos, state);
                        case BLOCK_BREAKER -> new net.juli2kapo.factoryascent.automation.BlockBreakerBlockEntity(pos, state);
                        case BLOCK_PLACER -> new net.juli2kapo.factoryascent.automation.BlockPlacerBlockEntity(pos, state);
                        case VACUUM_HOPPER -> new net.juli2kapo.factoryascent.automation.VacuumHopperBlockEntity(pos, state);
                        case TREE_FARM -> new net.juli2kapo.factoryascent.automation.TreeFarmBlockEntity(pos, state);
                        case MOB_FARM -> new net.juli2kapo.factoryascent.automation.MobFarmBlockEntity(pos, state);
                        case ELECTROLYZER -> new net.juli2kapo.factoryascent.fusion.ElectrolyzerBlockEntity(pos, state);
                        default -> new ProcessingMachineBlockEntity(type, pos, state);
                    },
                    ModBlocks.machine(type).get())));
        }
    }

    public static final Supplier<BlockEntityType<PowerCableBlockEntity>> POWER_CABLE = BLOCK_ENTITIES.register("power_cable",
            () -> new BlockEntityType<>(PowerCableBlockEntity::new, collect(ModBlocks.POWER_CABLES.values())));
    public static final Supplier<BlockEntityType<ItemPipeBlockEntity>> ITEM_PIPE = BLOCK_ENTITIES.register("item_pipe",
            () -> new BlockEntityType<>(ItemPipeBlockEntity::new, collect(ModBlocks.ITEM_PIPES.values())));

    public static final Supplier<BlockEntityType<CrateBlockEntity>> CRATE = BLOCK_ENTITIES.register("crate",
            () -> new BlockEntityType<>(CrateBlockEntity::new, ModBlocks.WOODEN_CRATE.get(), ModBlocks.BRONZE_CRATE.get()));

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
