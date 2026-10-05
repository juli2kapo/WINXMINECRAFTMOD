package net.juli2kapo.factoryascent.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.juli2kapo.factoryascent.space.gravity.GravityFrame;
import net.juli2kapo.factoryascent.space.gravity.client.GravityClient;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turned gravity: the camera. Yaw and pitch (and any roll other features add) are angles in the
 * player's local frame, so the finished camera rotation is turned by the frame (smoothly while the
 * turn animation runs), and the eyes are placed along local up. Everything that reads the camera
 * afterwards (the view matrix, frustum culling, the third-person pull-back, sounds) follows.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private @Nullable Entity entity;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow @Final private static Vector3fc FORWARDS;
    @Shadow @Final private static Vector3fc UP;
    @Shadow @Final private static Vector3fc LEFT;
    @Shadow private int matrixPropertiesDirty;

    @Unique private float factoryascent$partial = 1.0F;

    @Inject(method = "alignWithEntity", at = @At("HEAD"))
    private void factoryascent$capturePartial(float partialTicks, CallbackInfo ci) {
        factoryascent$partial = partialTicks;
    }

    @Inject(method = "setRotation(FFF)V", at = @At("TAIL"))
    private void factoryascent$turnRotation(float yRot, float xRot, float roll, CallbackInfo ci) {
        Entity e = entity;
        if (e == null || !GravityClient.active(e)) return;
        Quaternionf frame = GravityClient.visual(e, factoryascent$partial);
        rotation.premul(frame);
        FORWARDS.rotate(rotation, forwards);
        UP.rotate(rotation, up);
        LEFT.rotate(rotation, left);
        matrixPropertiesDirty |= 3;
    }

    @WrapOperation(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void factoryascent$turnedEyes(Camera camera, double x, double y, double z, Operation<Void> original) {
        Entity e = entity;
        if (e == null || !GravityClient.active(e)) {
            original.call(camera, x, y, z);
            return;
        }
        float partial = factoryascent$partial;
        double feetY = Mth.lerp(partial, e.yo, e.getY());
        double eye = y - feetY;
        Vec3 pos = new Vec3(x, feetY, z).add(GravityFrame.toWorld(net.juli2kapo.factoryascent.space.gravity.Gravity.of(e), 0, eye, 0))
                .add(GravityClient.offset(e, partial));
        original.call(camera, pos.x, pos.y, pos.z);
    }
}
