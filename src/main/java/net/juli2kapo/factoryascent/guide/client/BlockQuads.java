package net.juli2kapo.factoryascent.guide.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The real block models as raw quads, so the Manual's 3D viewer and the hologram can draw them
 * with their own colour and alpha (ghosted layers, red "wrong block" tint) through a custom
 * geometry submit.
 */
public final class BlockQuads {
    private static final Map<BlockState, List<BakedQuad>> CACHE = new IdentityHashMap<>();
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private BlockQuads() {}

    /** Forget cached quads (after a resource reload the models are new objects). */
    public static void clear() {
        CACHE.clear();
    }

    public static List<BakedQuad> quads(BlockState state) {
        return CACHE.computeIfAbsent(state, s -> {
            List<BakedQuad> out = new ArrayList<>();
            BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(s);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(BlockAndTintGetter.EMPTY, BlockPos.ZERO, s, RandomSource.create(42L), parts);
            for (BlockStateModelPart part : parts) {
                for (Direction d : SIDES) out.addAll(part.getQuads(d));
            }
            return out;
        });
    }

    /** Emits the block's model at the pose's origin (a unit cube from 0 to 1) in {@code argb}. */
    public static void emit(PoseStack.Pose pose, VertexConsumer buffer, BlockState state, int argb, int light) {
        QuadInstance instance = new QuadInstance();
        instance.setColor(argb);
        instance.setLightCoords(light);
        for (BakedQuad quad : quads(state)) buffer.putBakedQuad(pose, quad, instance);
    }
}
