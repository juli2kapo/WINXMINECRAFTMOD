package net.juli2kapo.minewinx.entity;

import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MineWinx.MOD_ID);

    public static final RegistryObject<EntityType<TsunamiEntity>> TSUNAMI =
            ENTITY_TYPES.register("tsunami",
                    () -> EntityType.Builder.<TsunamiEntity>of(TsunamiEntity::new, MobCategory.MISC)
                            .sized(1f, 1f)
                            .build("tsunami"));

    public static final RegistryObject<EntityType<SporeBombEntity>> SPOREBOMB =
            ENTITY_TYPES.register("spore_bomb",
                    () -> EntityType.Builder.<SporeBombEntity>of(SporeBombEntity::new, MobCategory.MISC)
                            .sized(1f, 1f)
                            .build("spore_bomb"));

    public static final RegistryObject<EntityType<WaterBlobProjectileEntity>> WATER_BLOB_PROJECTILE =
            ENTITY_TYPES.register("water_blob_projectile",
                    () -> EntityType.Builder.<WaterBlobProjectileEntity>of(WaterBlobProjectileEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("water_blob_projectile"));

    public static final RegistryObject<EntityType<IceCrystalEntity>> ICE_CRYSTAL =
            ENTITY_TYPES.register("ice_crystal",
                    () -> EntityType.Builder.<IceCrystalEntity>of(IceCrystalEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("ice_crystal"));


    public static final RegistryObject<EntityType<IceArrowEntity>> ICE_ARROW =
            ENTITY_TYPES.register("ice_arrow",
                    () -> EntityType.Builder.<IceArrowEntity>of(IceArrowEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("ice_arrow"));

    public static final RegistryObject<EntityType<SpeakerEntity>> SPEAKER =
            ENTITY_TYPES.register("speaker",
                    () -> EntityType.Builder.<SpeakerEntity>of(SpeakerEntity::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(10)
                            .updateInterval(5)
                            .build("speaker"));

        public static final RegistryObject<EntityType<PistonEntity>> PISTON =
                ENTITY_TYPES.register("piston",
                        () -> EntityType.Builder.<PistonEntity>of(PistonEntity::new, MobCategory.MISC)
                                .sized(1f, 1f)
                                .clientTrackingRange(4)
                                .updateInterval(10)
                                .build("piston"));

    public static final RegistryObject<EntityType<SunRay>> SUN_RAY =
            ENTITY_TYPES.register("sun_ray",
                    () -> EntityType.Builder.<SunRay>of(SunRay::new, MobCategory.MISC)
                            .sized(1f, 1f)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .fireImmune()
                            .build("sun_ray"));

    public static final RegistryObject<EntityType<PlayerIllusionEntity>> PLAYER_ILLUSION =
            ENTITY_TYPES.register("player_illusion",
                    () -> EntityType.Builder.of(PlayerIllusionEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.8F) // Dimensiones similares a las de un jugador/zombie
                            .build("player_illusion"));

    public static final RegistryObject<EntityType<LightRayEntity>> LIGHT_RAY =
            ENTITY_TYPES.register("light_ray", // The unique ID for the entity (e.g., /summon minewinx:light_ray)
                    () -> EntityType.Builder.<LightRayEntity>of(LightRayEntity::new, MobCategory.MISC) // The factory and category
                            .sized(0.25F, 0.25F) // The hitbox size (width, height). Small for a projectile.
                            .clientTrackingRange(4) // How far away (in chunks) clients will see the entity.
                            .updateInterval(10) // How often (in ticks) the server sends position updates. 10 is the default for arrows.
                            .build("light_ray")); // Finalizes the builder with the name.



    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}