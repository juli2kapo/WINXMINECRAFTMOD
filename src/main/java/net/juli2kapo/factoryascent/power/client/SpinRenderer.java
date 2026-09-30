package net.juli2kapo.factoryascent.power.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.power.PowerBlock;
import net.juli2kapo.factoryascent.power.PowerBlockEntity;
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
 * Turns the moving part of a generator: the Kinetic Dynamo's armature, the Steam Engine's
 * flywheel, the Wind Turbine's rotor. The part is a separate model built facing north at
 * {@code 1/scale} size around its pivot (block px); it spins up while the block is ACTIVE and
 * coasts to a stop when it isn't.
 */
public final class SpinRenderer implements BlockEntityRenderer<PowerBlockEntity, SpinRenderer.State> {
    public enum SpinAxis { X, Y, Z }

    /** @param pivot pivot in block px (model space, facing north); @param speed degrees per tick at full speed */
    public record Spec(StandaloneModelKey<BlockStateModelPart> model, float px, float py, float pz, SpinAxis axis,
                       float speed, float scale, float reach) {}

    public static final class State extends BlockEntityRenderState {
        float angle;
        float facingY;
    }

    private final Spec spec;

    public SpinRenderer(Spec spec, BlockEntityRendererProvider.Context context) {
        this.spec = spec;
    }

    public static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> FactoryAscent.MOD_ID + ":" + name);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PowerBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        var bs = be.getBlockState();
        state.facingY = (bs.hasProperty(PowerBlock.FACING) ? bs.getValue(PowerBlock.FACING) : Direction.NORTH).toYRot();
        if (be.getLevel() == null) return;
        float now = be.getLevel().getGameTime() + partialTicks;
        float dt = be.spinLastTime < 0 ? 0 : Math.max(0, Math.min(now - be.spinLastTime, 10));
        be.spinLastTime = now;
        float target = bs.hasProperty(PowerBlock.ACTIVE) && bs.getValue(PowerBlock.ACTIVE) ? spec.speed() : 0f;
        be.spinSpeed += (target - be.spinSpeed) * Math.min(1f, dt * 0.04f);
        be.spinAngle = (be.spinAngle + be.spinSpeed * dt) % 360f;
        state.angle = be.spinAngle;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart part = Minecraft.getInstance().getModelManager().getStandaloneModel(spec.model());
        if (part == null) return;
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - state.facingY));
        pose.translate(-0.5f, 0f, -0.5f);
        pose.translate(spec.px() / 16f, spec.py() / 16f, spec.pz() / 16f);
        pose.mulPose(switch (spec.axis()) {
            case X -> Axis.XP.rotationDegrees(state.angle);
            case Y -> Axis.YP.rotationDegrees(state.angle);
            case Z -> Axis.ZP.rotationDegrees(state.angle);
        });
        pose.scale(spec.scale(), spec.scale(), spec.scale());
        pose.translate(-0.5f, -0.5f, -0.5f);
        collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(part), new int[0],
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(PowerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(spec.reach());
    }
}
