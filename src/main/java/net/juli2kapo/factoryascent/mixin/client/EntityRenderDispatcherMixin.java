package net.juli2kapo.factoryascent.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.juli2kapo.factoryascent.space.gravity.client.GravityClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Turned gravity: players (yours in third person, and everyone else's) are drawn turned with their
 * gravity, about their eyes during the turn animation. Only in the world: the inventory doll uses a
 * camera state that was never initialised, and stays upright.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private void factoryascent$turnedPlayer(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack poseStack,
                                            SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        Entity entity = null;
        if (state instanceof AvatarRenderState avatar && camera.initialized && Minecraft.getInstance().level != null) {
            entity = Minecraft.getInstance().level.getEntity(avatar.id);
        }
        if (entity == null || !GravityClient.active(entity)) {
            original.call(renderer, state, poseStack, collector, camera);
            return;
        }
        poseStack.pushPose();
        GravityClient.applyModelTurn(entity, state.partialTick, poseStack);
        original.call(renderer, state, poseStack, collector, camera);
        poseStack.popPose();
    }
}
