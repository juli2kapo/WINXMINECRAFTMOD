package net.juli2kapo.factoryascent.fluid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.juli2kapo.factoryascent.fluid.pipe.FluidPipeBlock;
import net.juli2kapo.factoryascent.fluid.pipe.FluidPipeBlockEntity;
import net.juli2kapo.factoryascent.pipe.PipeConnection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fluid seen through a pipe's glass window while it flows: the core and every connected arm
 * fill with the fluid that last went through, drawn with its flowing (animated) texture.
 */
public final class PipeFluidRenderer implements BlockEntityRenderer<FluidPipeBlockEntity, PipeFluidRenderer.State> {
    private static final float LO = 5.2f / 16f, HI = 10.8f / 16f;

    public static final class State extends BlockEntityRenderState {
        Fluid fluid = Fluids.EMPTY;
        final boolean[] arms = new boolean[6];
    }

    public PipeFluidRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FluidPipeBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        long now = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        state.fluid = now - be.lastFlow() < 50 ? be.shownFluid() : Fluids.EMPTY;
        var bs = be.getBlockState();
        for (Direction d : Direction.values()) {
            state.arms[d.ordinal()] = bs.hasProperty(FluidPipeBlock.PROPERTIES.get(d)) && bs.getValue(FluidPipeBlock.PROPERTIES.get(d)) != PipeConnection.NONE;
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.fluid == Fluids.EMPTY) return;
        TextureAtlasSprite sprite = FluidRender.flowing(state.fluid);
        if (sprite == null) return;
        int color = FluidRender.tint(state.fluid);
        if (state.fluid.getFluidType().isLighterThanAir()) color = (color & 0x00FFFFFF) | 0xB0000000;
        int light = state.lightCoords, c = color;
        boolean[] arms = state.arms.clone();
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (p, b) -> {
            FluidRender.box(p, b, sprite, c, light, LO, LO, LO, HI, HI, HI, !arms[Direction.UP.ordinal()], !arms[Direction.DOWN.ordinal()]);
            for (Direction d : Direction.values()) {
                if (!arms[d.ordinal()]) continue;
                float x0 = LO, y0 = LO, z0 = LO, x1 = HI, y1 = HI, z1 = HI;
                switch (d) {
                    case DOWN -> { y0 = 0; y1 = LO; }
                    case UP -> { y0 = HI; y1 = 1; }
                    case NORTH -> { z0 = 0; z1 = LO; }
                    case SOUTH -> { z0 = HI; z1 = 1; }
                    case WEST -> { x0 = 0; x1 = LO; }
                    case EAST -> { x0 = HI; x1 = 1; }
                }
                FluidRender.box(p, b, sprite, c, light, x0, y0, z0, x1, y1, z1, d.getAxis() != Direction.Axis.Y, d.getAxis() != Direction.Axis.Y);
            }
        });
    }
}
