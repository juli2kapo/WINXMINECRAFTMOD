package net.juli2kapo.factoryascent.fluid;

import net.juli2kapo.factoryascent.fusion.ElectrolyzerBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.juli2kapo.factoryascent.power.BiogasGeneratorBlockEntity;
import net.juli2kapo.factoryascent.power.Generator;
import net.juli2kapo.factoryascent.power.MagmaticGeneratorBlockEntity;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.juli2kapo.factoryascent.power.SteamEngineBlockEntity;
import net.juli2kapo.factoryascent.registry.ModBlockEntities;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Real fluid handlers on the machines of the other features (their internal tanks stay as they
 * were; pipes now reach them): the Steam Engine (water, steam), Biogas Generator (biogas),
 * Magmatic Generator (lava), the reactor's Coolant Ports (water/coolant in, steam out), the
 * Tokamak and its Fusion Ports (deuterium, tritium, helium-3), the Electrolyzer (water in,
 * deuterium/tritium out); and fluid views of cells, Helium-3 and Rocket Fuel items.
 */
final class FluidIntegration {
    private FluidIntegration() {}

    static void register(IEventBus modBus) {}

    static void registerCapabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, PowerContent.generatorType(Generator.STEAM_ENGINE).get(),
                (be, side) -> be instanceof SteamEngineBlockEntity s ? s.fluidHandler() : null);
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, PowerContent.generatorType(Generator.BIOGAS_GENERATOR).get(),
                (be, side) -> be instanceof BiogasGeneratorBlockEntity b ? b.fluidHandler() : null);
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, PowerContent.generatorType(Generator.MAGMATIC_GENERATOR).get(),
                (be, side) -> be instanceof MagmaticGeneratorBlockEntity m ? m.fluidHandler() : null);
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, PowerContent.REACTOR_PORT_BE.get(), (be, side) -> be.fluidHandler());
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, PowerContent.TOKAMAK_CORE_BE.get(), (be, side) -> be.fluidHandler());
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, PowerContent.FUSION_PORT_BE.get(), (be, side) -> be.fluidHandler());
        e.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.machine(MachineType.ELECTROLYZER).get(),
                (be, side) -> be instanceof ElectrolyzerBlockEntity el ? el.fluidHandler() : null);
        // items that are fluid units
        Item[] items = {PowerContent.EMPTY_CELL.get(), PowerContent.DEUTERIUM_CELL.get(), PowerContent.TRITIUM_CELL.get(),
                PowerContent.COOLANT_CELL.get(), OrbitalContent.ROCKET_FUEL.get()};
        e.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new CellFluidHandler(access), items);
        Item he3 = PowerContent.helium3();
        if (he3 != null && he3 != Items.AIR) e.registerItem(Capabilities.Fluid.ITEM, (stack, access) -> new CellFluidHandler(access), he3);
    }
}
