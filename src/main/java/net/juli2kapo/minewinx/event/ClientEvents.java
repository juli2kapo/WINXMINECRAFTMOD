package net.juli2kapo.minewinx.event;

import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.client.KeyBindings;
import net.juli2kapo.minewinx.client.gui.DrowningOverlay;
import net.juli2kapo.minewinx.client.renderer.WaterBlockOnHead;
import net.juli2kapo.minewinx.network.PacketHandler;
import net.juli2kapo.minewinx.network.TransformC2SPacket;
import net.juli2kapo.minewinx.network.UsePowerC2SPacket;
import net.juli2kapo.minewinx.particles.ModParticles;
import net.juli2kapo.minewinx.particles.custom.FireParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

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
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("[CLIENTE] Tecla de poder presionada. Enviando paquete..."));
                PacketHandler.sendToServer(new UsePowerC2SPacket(1));
            }
            if (KeyBindings.USE_POWER_KEY2.consumeClick()) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("[CLIENTE] Tecla de poder presionada. Enviando paquete..."));
                PacketHandler.sendToServer(new UsePowerC2SPacket(2));
            }
            if (KeyBindings.USE_POWER_KEY3.consumeClick()) {
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

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // Registrar el renderer del bloque de agua en la cabeza
            MinecraftForge.EVENT_BUS.register(WaterBlockOnHead.class);
        }
    }
}