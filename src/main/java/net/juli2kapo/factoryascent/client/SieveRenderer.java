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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

/**
 * Turns the Sieve's crank (a separate model on the east rail, axle along the x axis). Like the
 * {@link QuernRenderer}, every crank (by hand, or from a Water Wheel / Windmill) queues one full
 * turn that the handle spins through and then eases to a stop.
 */
public final class SieveRenderer implements BlockEntityRenderer<AbstractMachineBlockEntity, SieveRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> CRANK =
            new StandaloneModelKey<>(() -> FactoryAscent.MOD_ID + ":sieve_crank");

    private static final float MIN_SPEED = 9f;
    private static final float MAX_SPEED = 30f;
    /** The axle: y = 10 px, z = 8 px (block model coordinates). */
    private static final float PIVOT_Y = 10f / 16f;
    private static final float PIVOT_Z = 0.5f;

    public static void registerModel(ModelEvent.RegisterStandalone event) {
        event.register(CRANK, SimpleUnbakedStandaloneModel.simpleModelWrapper(
                Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/sieve_crank")));
    }

    public static final class State extends BlockEntityRenderState {
        float angle;
    }

    public SieveRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AbstractMachineBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        if (!(be instanceof ProcessingMachineBlockEntity sieve) || be.getLevel() == null) return;
        float now = be.getLevel().getGameTime() + partialTicks;
        float dt = sieve.runnerLastTime < 0 ? 0 : Math.max(0, Math.min(now - sieve.runnerLastTime, 10));
        sieve.runnerLastTime = now;
        if (sieve.pendingTurnDegrees > 0) {
            float speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, sieve.pendingTurnDegrees * 0.12f));
            float step = Math.min(sieve.pendingTurnDegrees, speed * dt);
            sieve.pendingTurnDegrees -= step;
            sieve.runnerAngle = (sieve.runnerAngle + step) % 360f;
        }
        state.angle = sieve.runnerAngle;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart crank = Minecraft.getInstance().getModelManager().getStandaloneModel(CRANK);
        if (crank == null) return;
        pose.pushPose();
        pose.translate(0f, PIVOT_Y, PIVOT_Z);
        pose.mulPose(Axis.XP.rotationDegrees(state.angle));
        pose.translate(0f, -PIVOT_Y, -PIVOT_Z);
        collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(crank), new int[0],
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(AbstractMachineBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1);
    }
}
