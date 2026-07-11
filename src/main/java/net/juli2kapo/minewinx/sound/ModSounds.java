package net.juli2kapo.minewinx.sound;

import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sonidos auténticos de PvZ extraídos para las plantas de Flora. */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MineWinx.MOD_ID);

    public static final RegistryObject<SoundEvent> PEA_SHOOT = register("plants.pea_shoot");
    public static final RegistryObject<SoundEvent> PEA_HIT = register("plants.pea_hit");
    public static final RegistryObject<SoundEvent> FIREPEA = register("plants.firepea");
    public static final RegistryObject<SoundEvent> SNOWPEA_SHOOT = register("plants.snowpea_shoot");
    public static final RegistryObject<SoundEvent> FROZEN = register("plants.frozen");
    public static final RegistryObject<SoundEvent> CHOMP = register("plants.chomp");
    public static final RegistryObject<SoundEvent> GULP = register("plants.gulp");
    public static final RegistryObject<SoundEvent> CHERRYBOMB = register("plants.cherrybomb");
    public static final RegistryObject<SoundEvent> DOOMSHROOM = register("plants.doomshroom");
    public static final RegistryObject<SoundEvent> EXPLOSION = register("plants.explosion");
    public static final RegistryObject<SoundEvent> COBLAUNCH = register("plants.coblaunch");
    public static final RegistryObject<SoundEvent> PLANT = register("plants.plant");
    public static final RegistryObject<SoundEvent> TAP = register("plants.tap");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MineWinx.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
