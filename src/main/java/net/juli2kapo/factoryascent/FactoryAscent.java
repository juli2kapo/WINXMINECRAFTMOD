package net.juli2kapo.factoryascent;

import net.juli2kapo.factoryascent.energy.EnergyNetworkManager;
import net.juli2kapo.factoryascent.gametest.ModGameTests;
import net.juli2kapo.factoryascent.machine.MachineType;
import net.juli2kapo.factoryascent.pipe.ItemNetworkManager;
import net.juli2kapo.factoryascent.registry.ModBlockEntities;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.juli2kapo.factoryascent.registry.ModCreativeTabs;
import net.juli2kapo.factoryascent.registry.ModItems;
import net.juli2kapo.factoryascent.registry.ModMenus;
import net.juli2kapo.factoryascent.registry.ModRecipes;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@Mod(FactoryAscent.MOD_ID)
public final class FactoryAscent {
    public static final String MOD_ID = "factoryascent";

    public FactoryAscent(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModRecipes.TYPES.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        ModGameTests.register(modBus);

        modBus.addListener(FactoryAscent::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(FactoryAscent::onLevelTick);
        NeoForge.EVENT_BUS.addListener(FactoryAscent::onLevelUnload);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (MachineType type : MachineType.VALUES) {
            var beType = ModBlockEntities.machine(type).get();
            event.registerBlockEntity(Capabilities.Item.BLOCK, beType, (be, side) -> be.itemHandler(side));
            event.registerBlockEntity(Capabilities.Energy.BLOCK, beType, (be, side) -> be.energyHandler(side));
        }
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.POWER_CABLE.get(), (be, side) -> be.energyHandler());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.ITEM_PIPE.get(), (be, side) -> be.itemHandler(side));
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) EnergyNetworkManager.tickLevel(level);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EnergyNetworkManager.remove(level);
            ItemNetworkManager.remove(level);
        }
    }
}
