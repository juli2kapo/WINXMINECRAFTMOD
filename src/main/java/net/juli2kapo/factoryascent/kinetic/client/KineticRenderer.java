package net.juli2kapo.factoryascent.kinetic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.kinetic.KineticBlockEntity;
import net.juli2kapo.factoryascent.machine.AbstractMachineBlockEntity;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.machine.MachineType;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

/**
 * Turns the Water Wheel's paddle wheel and the Windmill's sails. The rotor is a separate model
 * (built facing north, turning about the north-south axis through {@code pivotY}); it spins up
 * while the machine is active (turning) and coasts to a stop when it isn't.
 */
public final class KineticRenderer implements BlockEntityRenderer<AbstractMachineBlockEntity, KineticRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> WHEEL = key("water_wheel_rotor");
    public static final StandaloneModelKey<BlockStateModelPart> SAILS = key("windmill_rotor");

    private static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> FactoryAscent.MOD_ID + ":" + name);
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        for (String name : new String[] {"water_wheel_rotor", "windmill_rotor"}) {
            event.register(name.startsWith("water") ? WHEEL : SAILS, SimpleUnbakedStandaloneModel.simpleModelWrapper(
                    Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/" + name)));
        }
    }

    public static final class State extends BlockEntityRenderState {
        float angle;
        float facingY;
        boolean windmill;
    }

    public KineticRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AbstractMachineBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.windmill = be.type() == MachineType.WINDMILL;
        Direction facing = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        state.facingY = facing.toYRot();
        if (!(be instanceof KineticBlockEntity rotor) || be.getLevel() == null) return;
        float now = be.getLevel().getGameTime() + partialTicks;
        float dt = rotor.rotorLastTime < 0 ? 0 : Math.max(0, Math.min(now - rotor.rotorLastTime, 10));
        rotor.rotorLastTime = now;
        float target = MachineBlock.isActive(be.getBlockState()) ? (state.windmill ? 2.2f : 3.5f) : 0f;
        // spin up and coast down smoothly
        rotor.rotorSpeed += (target - rotor.rotorSpeed) * Math.min(1f, dt * 0.05f);
        rotor.rotorAngle = (rotor.rotorAngle + rotor.rotorSpeed * dt) % 360f;
        state.angle = rotor.rotorAngle;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart rotor = Minecraft.getInstance().getModelManager().getStandaloneModel(state.windmill ? SAILS : WHEEL);
        if (rotor == null) return;
        float pivotY = state.windmill ? 11f / 16f : 0.5f;
        pose.pushPose();
        // Block models turn clockwise seen from above as the facing goes north → east; so does this.
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(180f - state.facingY));
        pose.translate(-0.5f, 0f, -0.5f);
        pose.translate(0.5f, pivotY, 0.5f);
        pose.mulPose(Axis.ZP.rotationDegrees(state.angle));
        pose.translate(-0.5f, -pivotY, -0.5f);
        collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(rotor), new int[0],
                state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(AbstractMachineBlockEntity be) {
        // The windmill's sails reach well past its block.
        return new AABB(be.getBlockPos()).inflate(2);
    }
}
