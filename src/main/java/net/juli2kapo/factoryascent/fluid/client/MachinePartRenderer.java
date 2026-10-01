package net.juli2kapo.factoryascent.fluid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachineBlock;
import net.juli2kapo.factoryascent.fluid.machine.FluidMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

/**
 * Moving parts of the fluid machines. The Steam Turbine spins its rotor; the Oil Derrick, once
 * formed, stands a whole pumpjack on its platform: frame, a nodding walking beam with its
 * horse head, the turning crank with counterweights and the polished rod going up and down.
 * The pumpjack parts are modelled at 1/3 scale over the 3×3 platform and drawn ×3.
 */
public final class MachinePartRenderer implements BlockEntityRenderer<FluidMachineBlockEntity, MachinePartRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> TURBINE_ROTOR = key("steam_turbine_rotor");
    public static final StandaloneModelKey<BlockStateModelPart> DERRICK_FRAME = key("oil_derrick_frame");
    public static final StandaloneModelKey<BlockStateModelPart> DERRICK_BEAM = key("oil_derrick_beam");
    public static final StandaloneModelKey<BlockStateModelPart> DERRICK_CRANK = key("oil_derrick_crank");
    public static final StandaloneModelKey<BlockStateModelPart> DERRICK_ROD = key("oil_derrick_rod");

    /** Pumpjack geometry in block px, relative to the controller (facing north): beam pivot, crank axle, horse-head arc. */
    static final float BEAM_PIVOT_Y = 60, BEAM_PIVOT_Z = 34, CRANK_Y = 34, CRANK_Z = 42, HEAD_Z = 5;
    private static final float BEAM_SWING = 14f;

    public enum Kind { TURBINE, DERRICK }

    public static final class State extends BlockEntityRenderState {
        float angle, facingY;
        boolean formed;
    }

    private final Kind kind;

    public MachinePartRenderer(Kind kind, BlockEntityRendererProvider.Context context) {
        this.kind = kind;
    }

    static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> FactoryAscent.MOD_ID + ":" + name);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FluidMachineBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        var bs = be.getBlockState();
        state.facingY = (bs.hasProperty(FluidMachineBlock.FACING) ? bs.getValue(FluidMachineBlock.FACING) : Direction.NORTH).toYRot();
        state.formed = bs.hasProperty(FluidMachineBlock.FORMED) && bs.getValue(FluidMachineBlock.FORMED);
        if (be.getLevel() == null) return;
        // the controllers are solid blocks: light the parts with what is in front of / above them
        var lightPos = kind == Kind.TURBINE ? be.getBlockPos().relative(bs.hasProperty(FluidMachineBlock.FACING)
                ? bs.getValue(FluidMachineBlock.FACING) : Direction.NORTH) : be.getBlockPos().above(2);
        state.lightCoords = net.minecraft.util.LightCoordsUtil.getLightCoords(be.getLevel(), lightPos);
        float now = be.getLevel().getGameTime() + partialTicks;
        float dt = be.animLastTime < 0 ? 0 : Math.max(0, Math.min(now - be.animLastTime, 10));
        be.animLastTime = now;
        boolean on = bs.hasProperty(FluidMachineBlock.ACTIVE) && bs.getValue(FluidMachineBlock.ACTIVE);
        float top = kind == Kind.TURBINE ? 24f : 6f;
        be.animSpeed += ((on ? top : 0f) - be.animSpeed) * Math.min(1f, dt * (kind == Kind.TURBINE ? 0.02f : 0.05f));
        be.animAngle = (be.animAngle + be.animSpeed * dt) % 360f;
        state.angle = be.animAngle;
    }

    private static @Nullable BlockStateModelPart part(StandaloneModelKey<BlockStateModelPart> key) {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(key);
    }

    private static void draw(PoseStack pose, SubmitNodeCollector collector, BlockStateModelPart part, int light) {
        collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(part), new int[0], light, OverlayTexture.NO_OVERLAY, 0);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - state.facingY));
        pose.translate(-0.5f, 0f, -0.5f);
        if (kind == Kind.TURBINE) {
            BlockStateModelPart rotor = part(TURBINE_ROTOR);
            if (rotor != null) {
                pose.translate(0.5f, 0.5f, 0.5f);
                pose.mulPose(Axis.ZP.rotationDegrees(state.angle));
                pose.translate(-0.5f, -0.5f, -0.5f);
                draw(pose, collector, rotor, state.lightCoords);
            }
        } else if (state.formed) {
            derrick(state, pose, collector);
        }
        pose.popPose();
    }

    /** The pumpjack: one crank turn per nod. */
    private void derrick(State state, PoseStack pose, SubmitNodeCollector collector) {
        int light = state.lightCoords;
        double a = Math.toRadians(state.angle);
        float tilt = (float) (Math.sin(a) * BEAM_SWING);
        BlockStateModelPart frame = part(DERRICK_FRAME), beam = part(DERRICK_BEAM), crank = part(DERRICK_CRANK), rod = part(DERRICK_ROD);
        if (frame != null) scaled(pose, collector, frame, light);
        if (crank != null) {
            pose.pushPose();
            pose.translate(8 / 16f, CRANK_Y / 16f, CRANK_Z / 16f);
            pose.mulPose(Axis.XP.rotationDegrees(state.angle));
            pose.translate(-8 / 16f, -CRANK_Y / 16f, -CRANK_Z / 16f);
            scaled(pose, collector, crank, light);
            pose.popPose();
        }
        if (beam != null) {
            pose.pushPose();
            pose.translate(8 / 16f, BEAM_PIVOT_Y / 16f, BEAM_PIVOT_Z / 16f);
            pose.mulPose(Axis.XP.rotationDegrees(tilt));
            pose.translate(-8 / 16f, -BEAM_PIVOT_Y / 16f, -BEAM_PIVOT_Z / 16f);
            scaled(pose, collector, beam, light);
            pose.popPose();
        }
        if (rod != null) {
            // the horse head end of the beam rises and falls; the rod hangs from it
            float lift = (float) (Math.sin(Math.toRadians(tilt)) * (BEAM_PIVOT_Z - HEAD_Z));
            pose.pushPose();
            pose.translate(0, lift / 16f, 0);
            scaled(pose, collector, rod, light);
            pose.popPose();
        }
    }

    /** Draws a 1/3-scale part over the 3×3 platform: model space 0..16 maps to -16..32 px (x, z) and 16..64 px (y). */
    private static void scaled(PoseStack pose, SubmitNodeCollector collector, BlockStateModelPart part, int light) {
        pose.pushPose();
        pose.translate(-1f, 1f, -1f);
        pose.scale(3f, 3f, 3f);
        draw(pose, collector, part, light);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(FluidMachineBlockEntity be) {
        return kind == Kind.DERRICK ? new AABB(be.getBlockPos()).inflate(3, 0, 3).expandTowards(0, 5, 0) : new AABB(be.getBlockPos());
    }
}
