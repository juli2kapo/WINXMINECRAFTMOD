package net.juli2kapo.factoryascent.fluid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.juli2kapo.factoryascent.fluid.tank.FluidTankBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a tank's contents inside its glass: a block of the fluid's own texture filled to the
 * level it holds (smoothly following changes). Gases are drawn hazy and fill from the top.
 */
public final class TankRenderer implements BlockEntityRenderer<FluidTankBlockEntity, TankRenderer.State> {
    private static final float INSET = 2.05f / 16f, BOTTOM = 1.05f / 16f, TOP = 14.95f / 16f;

    public static final class State extends BlockEntityRenderState {
        Fluid fluid = Fluids.EMPTY;
        float fill;
    }

    public TankRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FluidTankBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        var tank = be.tank();
        state.fluid = tank.fluid();
        float target = tank.capacity() <= 0 ? 0 : Math.min(1f, (float) tank.amount() / tank.capacity());
        be.shownFill += (target - be.shownFill) * 0.15f;
        if (Math.abs(target - be.shownFill) < 0.002f) be.shownFill = target;
        state.fill = be.shownFill;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.fluid == Fluids.EMPTY || state.fill <= 0.001f) return;
        TextureAtlasSprite sprite = FluidRender.still(state.fluid);
        if (sprite == null) return;
        boolean gas = state.fluid.getFluidType().isLighterThanAir();
        int color = FluidRender.tint(state.fluid);
        if (gas) color = (color & 0x00FFFFFF) | 0xA0000000;
        float h = (TOP - BOTTOM) * Math.max(0.02f, state.fill);
        float y0 = gas ? TOP - h : BOTTOM, y1 = gas ? TOP : BOTTOM + h;
        int light = state.lightCoords;
        int c = color;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (p, b) ->
                FluidRender.box(p, b, sprite, c, light, INSET, y0, INSET, 1 - INSET, y1, 1 - INSET, true, true));
    }
}
