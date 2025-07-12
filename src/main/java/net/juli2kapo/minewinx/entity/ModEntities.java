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

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}