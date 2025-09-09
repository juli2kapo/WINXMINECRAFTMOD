package net.juli2kapo.minewinx.event;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class PlayerEvents {

    @Mod.EventBusSubscriber(modid = MineWinx.MOD_ID)
    public static class ServerEvents {
        
        private static final float DEFAULT_FLY_SPEED = 0.05F;

        // Mantiene el estado de vuelo y la velocidad en cada tick del jugador
        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
                // Si el jugador está dormido, no se le permite volar.
                if (player.hasEffect(ModEffects.SLEEP.get())) {
                    if (player.getAbilities().mayfly || player.getAbilities().flying) {
                        player.getAbilities().mayfly = false;
                        player.getAbilities().flying = false;
                        player.onUpdateAbilities();
                    }
                    return; // Salta el resto de la lógica si está dormido.
                }

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
                    if (player.getAbilities().mayfly || player.getAbilities().flying) {
                        player.getAbilities().mayfly = false;
                        player.getAbilities().flying = false;
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
            return DEFAULT_FLY_SPEED + (stage - 1) * 0.025f;
        }

        // Asegura que el estado de vuelo se aplique al reaparecer o cambiar de dimensión
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            updateFlight((Player) event.getEntity());
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (!event.isEndConquered()) {
                updateFlight((Player) event.getEntity());
            }
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            updateFlight((Player) event.getEntity());
        }

        private static void updateFlight(Player player) {
            if (player instanceof ServerPlayer serverPlayer) {
                // No permitir volar si el jugador tiene el efecto SLEEP
                if (serverPlayer.hasEffect(ModEffects.SLEEP.get())) {
                    if (serverPlayer.getAbilities().mayfly) {
                        serverPlayer.getAbilities().mayfly = false;
                        serverPlayer.getAbilities().flying = false;
                        serverPlayer.onUpdateAbilities();
                    }
                    return;
                }

                if (PlayerDataProvider.isTransformed(serverPlayer)) {
                    if (!serverPlayer.getAbilities().mayfly) {
                        serverPlayer.getAbilities().mayfly = true;
                        serverPlayer.onUpdateAbilities();
                    }
                }
            }
        }
    }
}