package net.juli2kapo.minewinx.particles;

import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MineWinx.MOD_ID);

    public static final RegistryObject<SimpleParticleType> FIRE_PARTICLE =
            PARTICLE_TYPES.register("fire_particle", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> SPORE_PARTICLE =
            PARTICLE_TYPES.register("spore_particle", () -> new SimpleParticleType(true));

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}