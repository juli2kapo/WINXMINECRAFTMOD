package net.juli2kapo.factoryascent.power.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.juli2kapo.factoryascent.fusion.TokamakCoreBlock;
import net.juli2kapo.factoryascent.fusion.TokamakCoreBlockEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

/**
 * The tokamak's plasma: a glowing ring through the vacuum channel two blocks around the core,
 * swirling (two counter-rotating filament layers) and breathing, fading in as it ignites and out
 * when it goes cold. The ring models are built at a quarter scale around (8, 8, 8).
 */
public final class PlasmaRenderer implements BlockEntityRenderer<TokamakCoreBlockEntity, PlasmaRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> RING = SpinRenderer.key("plasma_ring");
    public static final StandaloneModelKey<BlockStateModelPart> FILAMENT = SpinRenderer.key("plasma_filament");

    public static final class State extends BlockEntityRenderState {
        float angle;
        float shown;
        float time;
    }

    public PlasmaRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TokamakCoreBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        if (be.getLevel() == null) return;
        float now = be.getLevel().getGameTime() + partialTicks;
        float dt = be.spinLastTime < 0 ? 0 : Math.max(0, Math.min(now - be.spinLastTime, 10));
        be.spinLastTime = now;
        boolean lit = be.getBlockState().getValue(TokamakCoreBlock.ACTIVE);
        be.plasmaShown = Math.max(0f, Math.min(1f, be.plasmaShown + (lit ? 0.02f : -0.04f) * dt));
        be.spinAngle = (be.spinAngle + 9f * be.plasmaShown * dt) % 360f;
        state.angle = be.spinAngle;
        state.shown = be.plasmaShown;
        state.time = now;
        state.lightCoords = 0xF000F0;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.shown <= 0.01f) return;
        var models = Minecraft.getInstance().getModelManager();
        BlockStateModelPart ring = models.getStandaloneModel(RING);
        BlockStateModelPart filament = models.getStandaloneModel(FILAMENT);
        float breathe = 1f + 0.06f * (float) Math.sin(state.time * 0.35f);
        float thick = state.shown * breathe;
        for (int layer = 0; layer < 2; layer++) {
            BlockStateModelPart part = layer == 0 ? ring : filament;
            if (part == null) continue;
            pose.pushPose();
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(layer == 0 ? state.angle : -state.angle * 1.7f));
            pose.scale(4f, 4f * thick, 4f);
            pose.translate(-0.5f, -0.5f, -0.5f);
            collector.submitBlockModel(pose, Sheets.translucentBlockItemSheet(), List.of(part), new int[0],
                    0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(TokamakCoreBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3.5);
    }
}
