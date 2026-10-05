package net.juli2kapo.factoryascent.mixin;

import net.juli2kapo.factoryascent.space.gravity.Gravity;
import net.juli2kapo.factoryascent.space.gravity.GravityFrame;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turned gravity: "would I fit standing / crouching here?" tests the turned box. Vanilla builds an
 * upright box from the feet, which on a wall pokes into the wall and would squeeze the player into
 * the crawling pose.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At("HEAD"), cancellable = true)
    private void factoryascent$turnedFit(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        Direction g = Gravity.of(self);
        if (g == Direction.DOWN) return;
        EntityDimensions dims = self.getDimensions(pose);
        cir.setReturnValue(self.level().noCollision(self, GravityFrame.box(g, self.position(), dims.width(), dims.height()).deflate(1.0E-7)));
    }
}
