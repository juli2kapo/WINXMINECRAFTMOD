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

                // Las winx ya no vuelan: la transformación no otorga vuelo.
                // Solo limpiamos vuelo residual (p. ej. guardados viejos donde
                // mayfly quedó persistido en el NBT del jugador).
                if (!player.isCreative() && !player.isSpectator()) {
                    if (player.getAbilities().mayfly || player.getAbilities().flying) {
                        player.getAbilities().mayfly = false;
                        player.getAbilities().flying = false;
                        player.getAbilities().setFlyingSpeed(DEFAULT_FLY_SPEED);
                        player.onUpdateAbilities();
                    }
                }
            }
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
                // Las winx ya no vuelan: al entrar/reaparecer, limpiar cualquier
                // vuelo residual de guardados anteriores.
                if (!serverPlayer.isCreative() && !serverPlayer.isSpectator()) {
                    if (serverPlayer.getAbilities().mayfly || serverPlayer.getAbilities().flying) {
                        serverPlayer.getAbilities().mayfly = false;
                        serverPlayer.getAbilities().flying = false;
                        serverPlayer.onUpdateAbilities();
                    }
                }
            }
        }
    }
}