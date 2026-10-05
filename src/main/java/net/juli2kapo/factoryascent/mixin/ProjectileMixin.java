package net.juli2kapo.factoryascent.mixin;

import net.juli2kapo.factoryascent.space.gravity.Gravity;
import net.juli2kapo.factoryascent.space.gravity.GravityFrame;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turned gravity: bows, snowballs, pearls and tridents are aimed from the shooter's yaw and pitch,
 * which for a turned player are angles in its local frame. The aim is turned into the world the
 * same way the look vector is.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {
    @Inject(method = "shootFromRotation", at = @At("HEAD"), cancellable = true)
    private void factoryascent$turnedAim(Entity source, float xRot, float yRot, float yOffset, float pow, float uncertainty, CallbackInfo ci) {
        Direction g = Gravity.of(source);
        if (g == Direction.DOWN) return;
        Projectile self = (Projectile) (Object) this;
        float r = Mth.DEG_TO_RAD;
        Vec3 local = new Vec3(-Mth.sin(yRot * r) * Mth.cos(xRot * r), -Mth.sin((xRot + yOffset) * r), Mth.cos(yRot * r) * Mth.cos(xRot * r));
        Vec3 aim = GravityFrame.toWorld(g, local);
        self.shoot(aim.x, aim.y, aim.z, pow, uncertainty);
        Vec3 sourceMovement = source.getKnownMovement();
        Vec3 inherited = source.onGround() ? GravityFrame.toWorld(g, GravityFrame.toLocal(g, sourceMovement).multiply(1, 0, 1)) : sourceMovement;
        self.setDeltaMovement(self.getDeltaMovement().add(inherited));
        ci.cancel();
    }
}
