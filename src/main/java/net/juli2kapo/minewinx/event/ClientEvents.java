package net.juli2kapo.minewinx.event;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.client.KeyBindings;
import net.juli2kapo.minewinx.client.gui.DrowningOverlay;
import net.juli2kapo.minewinx.client.gui.SleepOverlay;
import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.client.*;
import net.juli2kapo.minewinx.entity.client.model.*;
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
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
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
            // Flora usa otro orden: C (tecla 3) = bomba, Z (tecla 1) = elegir,
            // X (tecla 2) = plantar. El resto de elementos: Z/X/C = 1/2/3.
            boolean flora = "Nature".equalsIgnoreCase(net.juli2kapo.minewinx.client.ClientHudState.getElement());
            if (KeyBindings.USE_POWER_KEY1.consumeClick()) {
                PacketHandler.sendToServer(new UsePowerC2SPacket(flora ? 2 : 1)); // Z
            }
            if (KeyBindings.USE_POWER_KEY2.consumeClick()) {
                PacketHandler.sendToServer(new UsePowerC2SPacket(flora ? 3 : 2)); // X
            }
            if (KeyBindings.USE_POWER_KEY3.consumeClick()) {
                PacketHandler.sendToServer(new UsePowerC2SPacket(flora ? 1 : 3)); // C
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            net.juli2kapo.minewinx.client.ClientWingState.clear();
            net.juli2kapo.minewinx.client.VoxelMesh.clearCache();
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
            event.registerSpriteSet(ModParticles.POWIE_PARTICLE.get(), FireParticles.Factory::new);
            event.registerSpriteSet(ModParticles.DOOM_PARTICLE.get(), FireParticles.Factory::new);
        }

        @SubscribeEvent
        public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("drowning_overlay", DrowningOverlay.HUD_DROWNING);
            event.registerBelowAll("sleep_overlay", SleepOverlay.HUD_SLEEP);
            event.registerAboveAll("power_hud", net.juli2kapo.minewinx.client.gui.PowerHudOverlay.HUD_POWERS);
        }

        @SubscribeEvent
        public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(WaterBlobModel.LAYER_LOCATION, WaterBlobModel::createBodyLayer);
            event.registerLayerDefinition(IceCrystalModel.LAYER_LOCATION, IceCrystalModel::createBodyLayer);
            event.registerLayerDefinition(PistonModel.LAYER_LOCATION, PistonModel::createBodyLayer);
            event.registerLayerDefinition(SpeakerModel.LAYER_LOCATION, SpeakerModel::createBodyLayer);
            event.registerLayerDefinition(LightRayModel.LAYER_LOCATION, LightRayModel::createBodyLayer);
            event.registerLayerDefinition(TornadoModel.LAYER_LOCATION, TornadoModel::createBodyLayer);
            event.registerLayerDefinition(PrismModel.LAYER_LOCATION, PrismModel::createBodyLayer);
            event.registerLayerDefinition(DragonHeadModel.LAYER_LOCATION, DragonHeadModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new net.juli2kapo.minewinx.client.WingsLayer(renderer));
                }
            }
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.WATER_BLOB_PROJECTILE.get(), WaterBlobProjectileRenderer::new);
            event.registerEntityRenderer(ModEntities.TSUNAMI.get(), TsunamiEntityRenderer::new);
            event.registerEntityRenderer(ModEntities.ICE_ARROW.get(), IceArrowRenderer::new);
            event.registerEntityRenderer(ModEntities.ICE_CRYSTAL.get(), IceCrystalRenderer::new);
            event.registerEntityRenderer(ModEntities.SPEAKER.get(), SpeakerRenderer::new);
            event.registerEntityRenderer(ModEntities.PISTON.get(), PistonRenderer::new);
            event.registerEntityRenderer(ModEntities.SUN_RAY.get(), SunRayRenderer::new);
            event.registerEntityRenderer(ModEntities.PLAYER_ILLUSION.get(), PlayerIllusionEntityRenderer::new);
            event.registerEntityRenderer(ModEntities.LIGHT_RAY.get(), LightRayRenderer::new);
            event.registerEntityRenderer(ModEntities.TORNADO.get(), TornadoRenderer::new);
            event.registerEntityRenderer(ModEntities.PLANT.get(), PlantRenderer::new);
            event.registerEntityRenderer(ModEntities.PEA_PROJECTILE.get(), PeaRenderer::new);
            event.registerEntityRenderer(ModEntities.COB_PROJECTILE.get(), CobRenderer::new);
            event.registerEntityRenderer(ModEntities.PRISM.get(), PrismRenderer::new);
            event.registerEntityRenderer(ModEntities.DRAGON_HEAD.get(), DragonHeadRenderer::new);
        }
        @SubscribeEvent
        public static void registerAttributes(EntityAttributeCreationEvent event) {
            event.put(ModEntities.PLAYER_ILLUSION.get(), Zombie.createAttributes().build());
            event.put(ModEntities.PLANT.get(), net.juli2kapo.minewinx.entity.plants.PlantEntity.createAttributes().build());
        }

        // La burbuja de ahogo ya NO es una capa por-renderer: los renderers de
        // slimes/ravagers escalan su pose y la deformaban. Ahora la dibuja
        // WaterBlobWorldRenderer en espacio de mundo (RenderLevelStageEvent).
    }
}