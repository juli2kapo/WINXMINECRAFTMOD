package net.juli2kapo.minewinx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.entity.client.model.WaterBlobModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dibuja la burbuja de ahogo en ESPACIO DE MUNDO, no como capa del renderer de
 * la entidad. Motivo: renderers como el del slime o el ravager escalan/mueven
 * su pose interna y cualquier capa hereda esa transformación (la burbuja salía
 * gigante y desplazada). Acá partimos siempre de la posición interpolada real
 * de la entidad, así que funciona igual en zombies, slimes de cualquier tamaño,
 * ravagers, etc.
 */
@Mod.EventBusSubscriber(modid = MineWinx.MOD_ID, value = Dist.CLIENT)
public final class WaterBlobWorldRenderer {

    private static final ResourceLocation WATER_BLOB_TEXTURE = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/water_blob.png");
    private static WaterBlobModel<Entity> model;

    private WaterBlobWorldRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float partialTick = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        boolean rendered = false;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            // Jugadores: sus efectos sí se sincronizan → hasEffect. Mobs: vanilla
            // no sincroniza efectos al cliente → tracker (DrowningVisualS2CPacket).
            boolean drowning = living instanceof Player
                    ? living.hasEffect(ModEffects.DROWNING_TARGET.get())
                    : ClientDrowningTracker.isDrowning(living.getId());
            if (!drowning) continue;
            if (living == mc.player && mc.options.getCameraType().isFirstPerson()) {
                continue; // en primera persona no dibujar la propia burbuja encima de la cámara
            }

            if (model == null) {
                model = new WaterBlobModel<>(mc.getEntityModels().bakeLayer(WaterBlobModel.LAYER_LOCATION));
            }

            Vec3 pos = living.getPosition(partialTick);
            // Mobs con la cabeza adelantada respecto al centro del cuerpo (ravager):
            // desplazar la burbuja hacia donde mira la cabeza
            double fwd = headForwardOffset(living);
            if (fwd != 0.0) {
                float yaw = net.minecraft.util.Mth.lerp(partialTick, living.yHeadRotO, living.yHeadRot)
                        * ((float) Math.PI / 180.0F);
                pos = pos.add(-net.minecraft.util.Mth.sin(yaw) * fwd, 0.0, net.minecraft.util.Mth.cos(yaw) * fwd);
            }
            // Escala según el ancho real de la entidad: el blob mide ~0.75 bloques
            // de ancho a escala 1, con 1.4·ancho envuelve de verdad a un slime grande
            float s = Math.max(1.0F, living.getBbWidth() * 1.4F);
            double eyeY = pos.y + living.getEyeHeight();

            poseStack.pushPose();
            // Convención de modelos de entidad: tras scale(-1,-1,1)+translate(0,-1.501,0)
            // la geometría del blob queda en mundo_y [0, 0.876]·s sobre el origen del
            // pose → colocamos el origen para que esa franja quede centrada en los ojos.
            poseStack.translate(pos.x - cam.x, eyeY - 0.44 * s - cam.y, pos.z - cam.z);
            poseStack.scale(s, s, s);
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -1.501F, 0.0F);

            int light = LevelRenderer.getLightColor(mc.level, living.blockPosition().above());
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(WATER_BLOB_TEXTURE));
            model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, 0.6F, 0.8F, 1.0F, 0.45F);
            poseStack.popPose();
            rendered = true;
        }

        if (rendered) {
            buffer.endBatch();
        }
    }

    /** Cuánto sobresale la cabeza del mob por delante del centro de su hitbox. */
    private static double headForwardOffset(LivingEntity living) {
        // Slimes son "todo cabeza": burbuja centrada
        if (living instanceof net.minecraft.world.entity.monster.Slime) return 0.0;
        float w = living.getBbWidth();
        float h = living.getBbHeight();
        // Mobs altos y angostos (zombie, esqueleto, aldeano, enderman...) llevan
        // la cabeza centrada; cuadrúpedos y anchos (vaca, cerdo, oveja, ravager,
        // hoglin, araña) la llevan en el borde delantero de su hitbox.
        return h / w >= 1.7F ? 0.0 : w * 0.5;
    }
}
