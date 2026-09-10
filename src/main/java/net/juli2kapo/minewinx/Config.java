package net.juli2kapo.minewinx;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Config por mundo/servidor (serverconfig/minewinx-server.toml dentro del
 * mundo). Editable también antes de crear el mundo en defaultconfigs/.
 */
@Mod.EventBusSubscriber(modid = MineWinx.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.DoubleValue COOLDOWN_MULTIPLIER = BUILDER
            .comment("Multiplicador global de los cooldowns de poderes.",
                    "1.0 = normal, 0.5 = mitad, 2.0 = doble, 0 = sin cooldowns.")
            .defineInRange("cooldownMultiplier", 1.0D, 0.0D, 10.0D);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double cooldownMultiplier = 1.0D;

    /** Cambia el multiplicador en runtime (comando) y lo persiste en el config. */
    public static void setCooldownMultiplier(double value) {
        cooldownMultiplier = value;
        try {
            COOLDOWN_MULTIPLIER.set(value); // persiste en el toml del mundo
        } catch (IllegalStateException ignored) {
            // config aún no cargado: queda el valor runtime igual
        }
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            cooldownMultiplier = COOLDOWN_MULTIPLIER.get();
        }
    }
}
