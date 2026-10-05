package net.juli2kapo.factoryascent.satellites;

import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Satellites you can reach: the Overworld's satellites fly in Earth orbit, high above the
 * stations, as real bodies ({@link OrbitingSatellite}) a shuttle can find (tracker on the HUD) and
 * crash into ({@link SatelliteBodies#collide}). Config: {@link SatelliteConfig}.
 */
public final class SatellitesContent {
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(FactoryAscent.MOD_ID);

    /** Not saved, not summonable: bodies only exist while {@link SatelliteBodies} keeps them. */
    public static final DeferredHolder<EntityType<?>, EntityType<OrbitingSatellite>> ORBITING_SATELLITE = ENTITIES.registerEntityType(
            "orbiting_satellite", OrbitingSatellite::new, MobCategory.MISC,
            b -> b.sized(5.0f, 3.0f).clientTrackingRange(16).updateInterval(20).noSave().noSummon().fireImmune());

    private SatellitesContent() {}

    static void register(IEventBus modBus, ModContainer container) {
        ENTITIES.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, SatelliteConfig.SPEC, FactoryAscent.MOD_ID + "-satellites-server.toml");
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> SatelliteBodies.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> SatelliteBodies.clear());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) -> SatelliteCommands.register(e.getDispatcher()));
    }
}
