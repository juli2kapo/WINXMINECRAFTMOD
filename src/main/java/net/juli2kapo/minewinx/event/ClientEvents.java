package net.juli2kapo.minewinx.event;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.client.KeyBindings;
import net.juli2kapo.minewinx.client.gui.DrowningOverlay;
import net.juli2kapo.minewinx.client.gui.SleepOverlay;
import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.client.*;
import net.juli2kapo.minewinx.entity.client.layer.WaterBlobOnHeadLayer;
import net.juli2kapo.minewinx.entity.client.model.IceCrystalModel;
import net.juli2kapo.minewinx.entity.client.model.WaterBlobModel;
import net.juli2kapo.minewinx.network.PacketHandler;
import net.juli2kapo.minewinx.network.TransformC2SPacket;
import net.juli2kapo.minewinx.network.UsePowerC2SPacket;
import net.juli2kapo.minewinx.particles.ModParticles;
import net.juli2kapo.minewinx.particles.custom.FireParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

public class ClientEvents {
    @Mod.EventBusSubscriber(modid = MineWinx.MOD_ID, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasEffect(ModEffects.SLEEP.get())) {
                return;
            }
            if (KeyBindings.TRANSFORM_KEY.consumeClick()) {
                PacketHandler.sendToServer(new TransformC2SPacket());
            }
            if (KeyBindings.USE_POWER_KEY1.consumeClick()) {
                PacketHandler.sendToServer(new UsePowerC2SPacket(1));
            }
            if (KeyBindings.USE_POWER_KEY2.consumeClick()) {
                PacketHandler.sendToServer(new UsePowerC2SPacket(2));
            }
            if (KeyBindings.USE_POWER_KEY3.consumeClick()) {
                PacketHandler.sendToServer(new UsePowerC2SPacket(3));
            }
        }
    }

    @Mod.EventBusSubscriber(modid = MineWinx.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModBusEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(KeyBindings.TRANSFORM_KEY);
            event.register(KeyBindings.USE_POWER_KEY1);
            event.register(KeyBindings.USE_POWER_KEY2);
            event.register(KeyBindings.USE_POWER_KEY3);
        }

        @SubscribeEvent
        public static void registerParticleFactories(final RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.FIRE_PARTICLE.get(), FireParticles.Factory::new);
            event.registerSpriteSet(ModParticles.SPORE_PARTICLE.get(), FireParticles.Factory::new);
        }

        @SubscribeEvent
        public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("drowning_overlay", DrowningOverlay.HUD_DROWNING);
            event.registerBelowAll("sleep_overlay", SleepOverlay.HUD_SLEEP);
        }

        @SubscribeEvent
        public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(WaterBlobModel.LAYER_LOCATION, WaterBlobModel::createBodyLayer);
            event.registerLayerDefinition(IceCrystalModel.LAYER_LOCATION, IceCrystalModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.WATER_BLOB_PROJECTILE.get(), WaterBlobProjectileRenderer::new);
            event.registerEntityRenderer(ModEntities.TSUNAMI.get(), TsunamiEntityRenderer::new);
            event.registerEntityRenderer(ModEntities.ICE_ARROW.get(), IceArrowRenderer::new);
            event.registerEntityRenderer(ModEntities.ICE_CRYSTAL.get(), IceCrystalRenderer::new);
            event.registerEntityRenderer(ModEntities.SPEAKER.get(), SpeakerRenderer::new);
        }

        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            // Añadir la capa a los renderizadores de jugadores (default y slim)
            for (String skin : event.getSkins()) {
                LivingEntityRenderer renderer = event.getSkin(skin);
                if (renderer instanceof PlayerRenderer) {
                    addWaterBlobLayer(renderer);
                }
            }
            
        }

        private static <T extends LivingEntity, M extends EntityModel<T>> void addWaterBlobLayer(LivingEntityRenderer<T, M> renderer) {
            ModelLayerLocation location = WaterBlobModel.LAYER_LOCATION;
            WaterBlobModel<T> model = new WaterBlobModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(location));
            renderer.addLayer(new WaterBlobOnHeadLayer<>(renderer, model));
        }
    }
}