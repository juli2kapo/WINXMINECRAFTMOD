package net.juli2kapo.factoryascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.ProcessingMachineBlockEntity;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

/**
 * Turns the quern's runner stone and crank. Every crank queues one full turn; the stone spins
 * through what is queued, fastest when several turns are waiting, and eases to a stop, so steady
 * cranking reads as a steadily turning mill.
 */
public final class QuernRenderer implements BlockEntityRenderer<AbstractMachineBlockEntity, QuernRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> RUNNER =
            new StandaloneModelKey<>(() -> FactoryAscent.MOD_ID + ":quern_runner");

    /** Degrees per tick: never slower than this while turning, never faster than the max. */
    private static final float MIN_SPEED = 9f;
    private static final float MAX_SPEED = 30f;

    public static void registerModel(ModelEvent.RegisterStandalone event) {
        event.register(RUNNER, SimpleUnbakedStandaloneModel.simpleModelWrapper(
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/quern_runner")));
    }

    public static final class State extends BlockEntityRenderState {
        float angle;
    }

    public QuernRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AbstractMachineBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        if (!(be instanceof ProcessingMachineBlockEntity quern) || be.getLevel() == null) return;
        float now = be.getLevel().getGameTime() + partialTicks;
        float dt = quern.runnerLastTime < 0 ? 0 : Math.max(0, Math.min(now - quern.runnerLastTime, 10));
        quern.runnerLastTime = now;
        if (quern.pendingTurnDegrees > 0) {
            float speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, quern.pendingTurnDegrees * 0.12f));
            float step = Math.min(quern.pendingTurnDegrees, speed * dt);
            quern.pendingTurnDegrees -= step;
            quern.runnerAngle = (quern.runnerAngle + step) % 360f;
        }
        state.angle = quern.runnerAngle;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart runner = Minecraft.getInstance().getModelManager().getStandaloneModel(RUNNER);
        if (runner == null) return;
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(-state.angle));
        pose.translate(-0.5f, 0f, -0.5f);
        collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(runner), new int[0],
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
