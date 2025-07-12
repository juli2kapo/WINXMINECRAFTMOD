package net.juli2kapo.minewinx.event;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MineWinx.MOD_ID)
public class PlayerEvents {

    private static final float DEFAULT_FLY_SPEED = 0.05F;

    // Mantiene el estado de vuelo y la velocidad en cada tick del jugador
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
            boolean isTransformed = PlayerDataProvider.isTransformed(player);
            boolean needsUpdate = false;

            if (isTransformed) {
                // Si está transformado, concedemos la habilidad de volar.
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    needsUpdate = true;
                }

                // Ajustamos la velocidad de vuelo según la etapa.
                // Asume que tienes un método getStage(player) en PlayerDataProvider.
                int stage = PlayerDataProvider.getStage(player);
                float newFlySpeed = getFlySpeedForStage(stage);

                if (player.getAbilities().getFlyingSpeed() != newFlySpeed) {
                    player.getAbilities().setFlyingSpeed(newFlySpeed);
                    needsUpdate = true;
                }

            } else if (!player.isCreative() && !player.isSpectator()) {
                // Si no está transformado, quitamos la habilidad de volar.
                if (player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    needsUpdate = true;
                }
                // Y restauramos la velocidad de vuelo por defecto.
                if (player.getAbilities().getFlyingSpeed() != DEFAULT_FLY_SPEED) {
                    player.getAbilities().setFlyingSpeed(DEFAULT_FLY_SPEED);
                    needsUpdate = true;
                }
            }

            if (needsUpdate) {
                player.onUpdateAbilities();
            }
        }
    }

    // Devuelve la velocidad de vuelo correspondiente a la etapa.
    private static float getFlySpeedForStage(int stage) {
        return switch (stage) {
            case 1 -> DEFAULT_FLY_SPEED;      // Etapa 1: Velocidad normal
            case 2 -> DEFAULT_FLY_SPEED * 1.5f; // Etapa 2: 50% más rápido
            case 3 -> DEFAULT_FLY_SPEED * 2.0f; // Etapa 3: 100% más rápido
            default -> DEFAULT_FLY_SPEED;
        };
    }

    // Asegura que el estado de vuelo se aplique al reaparecer o cambiar de dimensión
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        updateFlight((Player) event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        updateFlight((Player) event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        updateFlight((Player) event.getEntity());
    }

    private static void updateFlight(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (PlayerDataProvider.isTransformed(serverPlayer)) {
                if (!serverPlayer.getAbilities().mayfly) {
                    serverPlayer.getAbilities().mayfly = true;
                    serverPlayer.onUpdateAbilities();
                }
            }
        }
    }
}