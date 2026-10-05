package net.juli2kapo.factoryascent.mixin.client;

import net.juli2kapo.factoryascent.space.gravity.Gravity;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turned gravity, the client's own player: two helpers that assume an upright body are switched off
 * while the gravity is turned. The "pushed out of blocks" nudge tests upright columns at the feet,
 * which on a wall lie inside the wall and would shove the player off it; auto-jump measures steps
 * along world Y.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void factoryascent$noUprightNudge(double x, double z, CallbackInfo ci) {
        if (Gravity.isTurned((LocalPlayer) (Object) this)) ci.cancel();
    }

    @Inject(method = "updateAutoJump", at = @At("HEAD"), cancellable = true)
    private void factoryascent$noAutoJump(float xa, float za, CallbackInfo ci) {
        if (Gravity.isTurned((LocalPlayer) (Object) this)) ci.cancel();
    }
}
