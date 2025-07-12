package net.juli2kapo.minewinx.event;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.client.KeyBindings;
import net.juli2kapo.minewinx.client.gui.DrowningOverlay;
import net.juli2kapo.minewinx.network.PacketHandler;
import net.juli2kapo.minewinx.network.TransformC2SPacket;
import net.juli2kapo.minewinx.network.UsePowerC2SPacket;
import net.juli2kapo.minewinx.particles.ModParticles;
import net.juli2kapo.minewinx.particles.custom.FireParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public class ClientEvents {
    @Mod.EventBusSubscriber(modid = MineWinx.MOD_ID, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            if (KeyBindings.TRANSFORM_KEY.consumeClick()) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Tecla de transformación presionada. Enviando paquete..."));
                PacketHandler.sendToServer(new TransformC2SPacket());
            }
            if (KeyBindings.USE_POWER_KEY1.consumeClick()) {
                // Mensaje de depuración en el cliente
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("[CLIENTE] Tecla de poder presionada. Enviando paquete..."));
                PacketHandler.sendToServer(new UsePowerC2SPacket(1));
            }
            if (KeyBindings.USE_POWER_KEY2.consumeClick()) {
                // Mensaje de depuración en el cliente
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("[CLIENTE] Tecla de poder presionada. Enviando paquete..."));
                PacketHandler.sendToServer(new UsePowerC2SPacket(2));
            }
            if (KeyBindings.USE_POWER_KEY3.consumeClick()) {
                // Mensaje de depuración en el cliente
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("[CLIENTE] Tecla de poder presionada. Enviando paquete..."));
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
        }

        @SubscribeEvent
        public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("drowning_overlay", DrowningOverlay.HUD_DROWNING);
        }

//        @SubscribeEvent
//        public static void addPlayerRenderLayers(EntityRenderersEvent.AddLayers event) {
//            // Añadir a los modelos de jugador por defecto y slim
//            addLayerToPlayerRenderer(event, "default");
//            addLayerToPlayerRenderer(event, "slim");
//        }
//
//        private static void addLayerToPlayerRenderer(EntityRenderersEvent.AddLayers event, String skinName) {
//            PlayerRenderer renderer = event.getSkin(skinName);
//            if (renderer != null) {
//                renderer.addLayer(new DrowningHeadFeatureRenderer(renderer));
//            }
//        }
    }
}