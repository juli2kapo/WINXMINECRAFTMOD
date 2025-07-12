package net.juli2kapo.minewinx.effect;

import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MineWinx.MOD_ID);

    public static final RegistryObject<MobEffect> FIRE_BARRIER = MOB_EFFECTS.register("fire_barrier",
            () -> new FireBarrierEffect(MobEffectCategory.BENEFICIAL, 0xFF4500));

    public static final RegistryObject<MobEffect> FIRE_LASER = MOB_EFFECTS.register("fire_laser",
            () -> new FireLaserEffect(MobEffectCategory.BENEFICIAL, 0xFF0000));

    public static final RegistryObject<MobEffect> DROWNING_TARGET = MOB_EFFECTS.register("drowning_target",
            () -> new DrowningTargetEffect(MobEffectCategory.HARMFUL, 0x4287f5));

    public static final RegistryObject<MobEffect> WATER_PRESSURE = MOB_EFFECTS.register("water_pressure",
            () -> new WaterPressureEffect(MobEffectCategory.HARMFUL, 0x0000FF));

    public static final RegistryObject<MobEffect> SLEEPY = MOB_EFFECTS.register("sleepy",
            () -> new SleepyEffect(MobEffectCategory.HARMFUL, 0x99cc33));

    public static final RegistryObject<MobEffect> SLEEP = MOB_EFFECTS.register("sleep",
            () -> new SleepEffect(MobEffectCategory.HARMFUL, 0x336600));

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}