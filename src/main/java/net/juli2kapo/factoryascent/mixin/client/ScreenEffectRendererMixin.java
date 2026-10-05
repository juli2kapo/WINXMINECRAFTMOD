package net.juli2kapo.factoryascent.mixin.client;

import net.juli2kapo.factoryascent.space.gravity.Gravity;
import net.juli2kapo.factoryascent.space.gravity.GravityFrame;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turned gravity: the "head inside a block" overlay samples around the feet's X/Z at eye height,
 * which on a wall is inside the wall. For a turned player it samples around the real eyes.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "getViewBlockingStateAndPos", at = @At("HEAD"), cancellable = true)
    private static void factoryascent$turnedViewBlock(Player player, CallbackInfoReturnable<Pair<BlockState, BlockPos>> cir) {
        Direction g = Gravity.of(player);
        if (g == Direction.DOWN || player.noPhysics) return;
        Vec3 eye = player.getEyePosition();
        double w = player.getBbWidth() * 0.8F, h = 0.1F * player.getScale();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 8; i++) {
            Vec3 off = GravityFrame.toWorld(g, ((i >> 0) % 2 - 0.5) * w, ((i >> 1) % 2 - 0.5) * h, ((i >> 2) % 2 - 0.5) * w);
            pos.set(eye.x + off.x, eye.y + off.y, eye.z + off.z);
            BlockState state = player.level().getBlockState(pos);
            if (state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(player.level(), pos)) {
                cir.setReturnValue(Pair.of(state, pos.immutable()));
                return;
            }
        }
        cir.setReturnValue(null);
    }
}
