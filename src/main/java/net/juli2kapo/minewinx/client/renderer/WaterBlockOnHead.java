package net.juli2kapo.minewinx.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.juli2kapo.minewinx.effect.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "minewinx", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WaterBlockOnHead {

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();

        if (player.hasEffect(ModEffects.DROWNING_TARGET.get())) {
            player.sendSystemMessage(Component.literal("Rendering water helmet for player: " + player.getName().getString()));
            renderWaterHelmet(event.getPoseStack(), event.getMultiBufferSource(), player, event.getPackedLight());
        }
    }

    private static void renderWaterHelmet(PoseStack poseStack, MultiBufferSource bufferSource, Player player, int packedLight) {
        player.sendSystemMessage(Component.literal("Starting water helmet render"));

        poseStack.pushPose();

        // Posicionar el bloque de agua en la cabeza
        poseStack.translate(0.0, 1.8, 0.0);
        poseStack.scale(0.8f, 0.8f, 0.8f);

        // Renderizar el bloque de agua
        BlockState waterState = Blocks.WATER.defaultBlockState();
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                waterState,
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );

        poseStack.popPose();

        player.sendSystemMessage(Component.literal("Water helmet render completed"));
    }
}