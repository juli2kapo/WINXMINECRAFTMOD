package net.juli2kapo.factoryascent.mixin;

import java.util.List;
import net.juli2kapo.factoryascent.space.gravity.GravityFrame;
import net.juli2kapo.factoryascent.space.gravity.GravityHolder;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turned gravity, the entity part (see {@code space.gravity.Gravity}). Every hook first checks the
 * entity's gravity field and returns straight away when it is DOWN, so entities that never wear
 * Magnetic Boots (all of them but players) run pure vanilla code.
 *
 * <ul>
 *   <li>{@code makeBoundingBox}: the box is the usual one turned about the feet.</li>
 *   <li>{@code move}: collision is still vanilla (the turned box is axis-aligned), but step-up,
 *       on-ground and which collisions count as "horizontal" are worked out along the turned axes.</li>
 *   <li>eyes and the view vector: yaw and pitch are angles in the turned frame, so the look vector
 *       and the eyes are turned into the world (block picking, interaction range checks on the
 *       server, projectiles).</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements GravityHolder {
    @Shadow private EntityDimensions dimensions;
    @Shadow private float eyeHeight;
    @Shadow protected Vec3 stuckSpeedMultiplier;

    @Unique private Direction factoryascent$gravity = Direction.DOWN;
    /** Non-null while the delta movement is held in this frame (LivingEntityMixin, around travel and jumping). */
    @Unique private Direction factoryascent$frame;

    @Override
    public Direction factoryascent$gravity() {
        return factoryascent$gravity == null ? Direction.DOWN : factoryascent$gravity;
    }

    @Override
    public void factoryascent$setGravityRaw(Direction gravity) {
        factoryascent$gravity = gravity == null ? Direction.DOWN : gravity;
    }

    @Override
    public Direction factoryascent$localFrame() {
        return factoryascent$frame;
    }

    @Override
    public void factoryascent$setLocalFrame(Direction frame) {
        factoryascent$frame = frame;
    }

    @Unique
    private boolean factoryascent$turned() {
        return factoryascent$gravity != null && factoryascent$gravity != Direction.DOWN;
    }

    // ---------------------------------------------------------------- box

    @Inject(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private void factoryascent$turnedBox(Vec3 position, CallbackInfoReturnable<AABB> cir) {
        if (factoryascent$turned() && dimensions != null) {
            cir.setReturnValue(GravityFrame.box(factoryascent$gravity, position, dimensions.width(), dimensions.height()));
        }
    }

    // ---------------------------------------------------------------- eyes and looking

    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void factoryascent$turnedEye(CallbackInfoReturnable<Vec3> cir) {
        if (factoryascent$turned()) {
            cir.setReturnValue(GravityFrame.eye(factoryascent$gravity, ((Entity) (Object) this).position(), eyeHeight));
        }
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void factoryascent$turnedEyeLerp(float partial, CallbackInfoReturnable<Vec3> cir) {
        if (factoryascent$turned()) {
            Entity self = (Entity) (Object) this;
            Vec3 feet = new Vec3(Mth.lerp(partial, self.xo, self.getX()), Mth.lerp(partial, self.yo, self.getY()), Mth.lerp(partial, self.zo, self.getZ()));
            cir.setReturnValue(GravityFrame.eye(factoryascent$gravity, feet, eyeHeight));
        }
    }

    @Inject(method = "getEyeY", at = @At("HEAD"), cancellable = true)
    private void factoryascent$turnedEyeY(CallbackInfoReturnable<Double> cir) {
        if (factoryascent$turned()) {
            cir.setReturnValue(GravityFrame.eye(factoryascent$gravity, ((Entity) (Object) this).position(), eyeHeight).y);
        }
    }

    @Inject(method = "calculateViewVector", at = @At("RETURN"), cancellable = true)
    private void factoryascent$turnedView(float xRot, float yRot, CallbackInfoReturnable<Vec3> cir) {
        if (factoryascent$turned()) cir.setReturnValue(GravityFrame.toWorld(factoryascent$gravity, cir.getReturnValue()));
    }

    /**
     * The server measures falls from the client's world-Y movement, which on a wall is just
     * walking: while the boots hold a turned gravity there is no falling to measure.
     */
    @Inject(method = "doCheckFallDamage", at = @At("HEAD"), cancellable = true)
    private void factoryascent$noFallWhileTurned(double xa, double ya, double za, boolean onGround, CallbackInfo ci) {
        if (factoryascent$turned()) {
            ((Entity) (Object) this).resetFallDistance();
            ci.cancel();
        }
    }

    // ---------------------------------------------------------------- moving

    /**
     * A turned entity moves by this instead of the vanilla body (which is written for -Y gravity).
     * Same steps: cobweb slow-down, collision (vanilla, with the turned box), step-up along local
     * up, then the collision flags and on-ground along the local axes, and the collided speed
     * components zeroed. While the velocity is held in the local frame the delta is local too.
     */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void factoryascent$turnedMove(MoverType type, Vec3 delta, CallbackInfo ci) {
        Direction frame = factoryascent$frame;
        if (!factoryascent$turned() && frame == null) return;
        ci.cancel();
        Entity self = (Entity) (Object) this;
        Direction g = factoryascent$gravity();
        Vec3 world = frame != null && type == MoverType.SELF ? GravityFrame.toWorld(frame, delta) : delta;
        if (self.noPhysics) {
            self.setPos(self.position().add(world));
            self.horizontalCollision = self.verticalCollision = self.verticalCollisionBelow = self.minorHorizontalCollision = false;
            return;
        }
        if (stuckSpeedMultiplier.lengthSqr() > 1.0E-7) {
            if (type != MoverType.PISTON) {
                Vec3 local = GravityFrame.toLocal(g, world);
                Vec3 stuck = stuckSpeedMultiplier;
                world = GravityFrame.toWorld(g, local.x * stuck.x, local.y * stuck.y, local.z * stuck.z);
            }
            stuckSpeedMultiplier = Vec3.ZERO;
            self.setDeltaMovement(Vec3.ZERO);
        }
        Vec3 movement = factoryascent$collide(self, g, world);
        double movementLength = movement.lengthSqr();
        if (movementLength > 1.0E-7 || world.lengthSqr() - movementLength < 1.0E-7) {
            self.setPos(self.position().add(movement));
        }
        Vec3 lw = GravityFrame.toLocal(g, world), lm = GravityFrame.toLocal(g, movement);
        boolean xCollision = !Mth.equal(lw.x, lm.x), zCollision = !Mth.equal(lw.z, lm.z);
        self.horizontalCollision = xCollision || zCollision;
        boolean movedVertically = Math.abs(lw.y) > 0.0;
        if (movedVertically || self.isLocalInstanceAuthoritative()) {
            self.verticalCollision = lw.y != lm.y;
            self.verticalCollisionBelow = self.verticalCollision && lw.y < 0.0;
            self.setOnGroundWithMovement(self.verticalCollisionBelow, self.horizontalCollision, movement);
        }
        self.minorHorizontalCollision = false;
        // the boots hold on: no fall damage builds up along a turned gravity
        if (g != Direction.DOWN) self.resetFallDistance();
        if (xCollision || zCollision || self.verticalCollision && movedVertically) {
            Vec3 v = self.getDeltaMovement();
            Vec3 vl = GravityFrame.toLocal(g, frame != null ? GravityFrame.toWorld(frame, v) : v);
            vl = new Vec3(xCollision ? 0 : vl.x, self.verticalCollision && movedVertically ? 0 : vl.y, zCollision ? 0 : vl.z);
            Vec3 vw = GravityFrame.toWorld(g, vl);
            self.setDeltaMovement(frame != null ? GravityFrame.toLocal(frame, vw) : vw);
        }
    }

    /** Vanilla collision with the turned box, then a step up (along local up) over low obstacles. */
    @Unique
    private static Vec3 factoryascent$collide(Entity self, Direction g, Vec3 world) {
        AABB box = self.getBoundingBox();
        List<VoxelShape> entities = self.level().getEntityCollisions(self, box.expandTowards(world));
        Vec3 step = world.lengthSqr() == 0.0 ? world : Entity.collideBoundingBox(self, world, box, self.level(), entities);
        Vec3 lw = GravityFrame.toLocal(g, world), ls = GravityFrame.toLocal(g, step);
        boolean horizontal = lw.x != ls.x || lw.z != ls.z;
        boolean groundAfter = lw.y != ls.y && lw.y < 0.0;
        float maxStep = self.maxUpStep();
        if (maxStep > 0.0F && (groundAfter || self.onGround()) && horizontal) {
            AABB grounded = groundAfter ? box.move(GravityFrame.toWorld(g, 0, ls.y, 0)) : box;
            Vec3 up = GravityFrame.toWorld(g, lw.x, maxStep, lw.z);
            List<VoxelShape> around = self.level().getEntityCollisions(self, grounded.expandTowards(up));
            Vec3 a = Entity.collideBoundingBox(self, up, grounded, self.level(), around);
            double rose = GravityFrame.toLocal(g, a).y;
            Vec3 down = rose > 0 ? Entity.collideBoundingBox(self, GravityFrame.toWorld(g, 0, -rose, 0), grounded.move(a), self.level(), around) : Vec3.ZERO;
            Vec3 total = a.add(down);
            Vec3 lt = GravityFrame.toLocal(g, total);
            if (lt.x * lt.x + lt.z * lt.z > ls.x * ls.x + ls.z * ls.z + 1.0E-7) {
                return total.add(GravityFrame.toWorld(g, 0, groundAfter ? ls.y : 0, 0));
            }
        }
        return step;
    }
}
