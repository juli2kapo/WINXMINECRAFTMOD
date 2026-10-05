package net.juli2kapo.factoryascent.mixin;

import net.juli2kapo.factoryascent.space.gravity.GravityFrame;
import net.juli2kapo.factoryascent.space.gravity.GravityHolder;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turned gravity, the walking part. Vanilla {@code travel} (input, friction, gravity, drag) and
 * {@code jumpFromGround} only ever touch the velocity as "x/z sideways, y up"; for a turned player
 * the velocity is turned into the local frame on the way in and back on the way out, so all of that
 * code (and the yaw it uses for WASD) just works along the new floor. {@code EntityMixin.move}
 * knows the velocity is local in between.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow protected abstract void updateWalkAnimation(float distance);

    @Unique private Direction factoryascent$entered;

    @Unique
    private boolean factoryascent$enterLocal() {
        GravityHolder h = (GravityHolder) this;
        Direction g = h.factoryascent$gravity();
        if (g == Direction.DOWN || h.factoryascent$localFrame() != null) return false;
        LivingEntity self = (LivingEntity) (Object) this;
        self.setDeltaMovement(GravityFrame.toLocal(g, self.getDeltaMovement()));
        h.factoryascent$setLocalFrame(g);
        return true;
    }

    @Unique
    private void factoryascent$exitLocal(Direction g) {
        GravityHolder h = (GravityHolder) this;
        LivingEntity self = (LivingEntity) (Object) this;
        h.factoryascent$setLocalFrame(null);
        self.setDeltaMovement(GravityFrame.toWorld(g, self.getDeltaMovement()));
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void factoryascent$travelLocal(Vec3 input, CallbackInfo ci) {
        factoryascent$entered = factoryascent$enterLocal() ? ((GravityHolder) this).factoryascent$gravity() : null;
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void factoryascent$travelWorld(Vec3 input, CallbackInfo ci) {
        if (factoryascent$entered != null) {
            Direction g = factoryascent$entered;
            factoryascent$entered = null;
            factoryascent$exitLocal(g);
        }
    }

    @Unique private Direction factoryascent$jumpEntered;

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void factoryascent$jumpLocal(CallbackInfo ci) {
        factoryascent$jumpEntered = factoryascent$enterLocal() ? ((GravityHolder) this).factoryascent$gravity() : null;
    }

    @Inject(method = "jumpFromGround", at = @At("RETURN"))
    private void factoryascent$jumpWorld(CallbackInfo ci) {
        if (factoryascent$jumpEntered != null) {
            Direction g = factoryascent$jumpEntered;
            factoryascent$jumpEntered = null;
            factoryascent$exitLocal(g);
        }
    }

    /** The walking animation counts the distance walked along the turned floor, not the world's XZ. */
    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void factoryascent$turnedWalkAnimation(boolean useY, CallbackInfo ci) {
        Direction g = ((GravityHolder) this).factoryascent$gravity();
        if (g == Direction.DOWN) return;
        LivingEntity self = (LivingEntity) (Object) this;
        Vec3 d = GravityFrame.toLocal(g, new Vec3(self.getX() - self.xo, self.getY() - self.yo, self.getZ() - self.zo));
        float distance = (float) Mth.length(d.x, useY ? d.y : 0.0, d.z);
        if (!self.isPassenger() && self.isAlive()) this.updateWalkAnimation(distance);
        else self.walkAnimation.stop();
        ci.cancel();
    }
}
