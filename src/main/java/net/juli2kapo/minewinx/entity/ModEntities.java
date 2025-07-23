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

    public static final RegistryObject<EntityType<KnutEntity>> KNUTMOB = ENTITY_TYPES.register("hostile_mob",
            () -> EntityType.Builder.of(KnutEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .build("hostile_mob"));


    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}