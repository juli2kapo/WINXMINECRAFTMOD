package net.juli2kapo.factoryascent.orbital;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

/** Client side of the Orbital age: the rocket on the Launch Pad and the Ground Station's sweeping dish. */
public final class OrbitalContentClient {
    static final StandaloneModelKey<BlockStateModelPart> ROCKET_SURVEY = key("orbital_rocket_survey");
    static final StandaloneModelKey<BlockStateModelPart> ROCKET_UPLINK = key("orbital_rocket_uplink");
    static final StandaloneModelKey<BlockStateModelPart> DISH = key("ground_station_dish");

    private OrbitalContentClient() {}

    private static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> FactoryAscent.MOD_ID + ":" + name);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((ModelEvent.RegisterStandalone e) -> {
            for (var entry : List.of(
                    java.util.Map.entry(ROCKET_SURVEY, "orbital_rocket_survey"),
                    java.util.Map.entry(ROCKET_UPLINK, "orbital_rocket_uplink"),
                    java.util.Map.entry(DISH, "ground_station_dish"))) {
                e.register(entry.getKey(), SimpleUnbakedStandaloneModel.simpleModelWrapper(
                        Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/" + entry.getValue())));
            }
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(OrbitalContent.LAUNCH_CONTROLLER_BE.get(), RocketRenderer::new);
            e.registerBlockEntityRenderer(OrbitalContent.GROUND_STATION_BE.get(), DishRenderer::new);
        });
    }

    // ---------------------------------------------------------------- rocket

    static final class RocketState extends BlockEntityRenderState {
        @Nullable SatelliteType type;
        /** Blocks above the pad. */
        float height;
        /** Pre-launch rumble offset. */
        float shakeX, shakeZ;
    }

    /**
     * The rocket standing on the pad while a satellite is mounted. During a launch it rumbles, then
     * climbs at {@link LaunchControllerBlockEntity#ACCEL}·t² blocks, the same curve the server's
     * exhaust trail follows.
     */
    static final class RocketRenderer implements BlockEntityRenderer<LaunchControllerBlockEntity, RocketState> {
        RocketRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public RocketState createRenderState() {
            return new RocketState();
        }

        @Override
        public void extractRenderState(LaunchControllerBlockEntity pad, RocketState state, float partialTicks, Vec3 camera,
                                       ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderer.super.extractRenderState(pad, state, partialTicks, camera, breakProgress);
            state.type = pad.satelliteType();
            state.height = 0;
            state.shakeX = state.shakeZ = 0;
            if (pad.launchStart() >= 0 && pad.getLevel() != null) {
                float t = pad.getLevel().getGameTime() - pad.launchStart() + partialTicks;
                if (t > LaunchControllerBlockEntity.LIFTOFF) {
                    float dt = t - LaunchControllerBlockEntity.LIFTOFF;
                    state.height = LaunchControllerBlockEntity.ACCEL * dt * dt;
                } else if (t > LaunchControllerBlockEntity.LIFTOFF / 2f) {
                    float amp = 0.02f * (t / LaunchControllerBlockEntity.LIFTOFF);
                    state.shakeX = (float) Math.sin(t * 7.3) * amp;
                    state.shakeZ = (float) Math.cos(t * 9.1) * amp;
                }
                if (state.height > 0) state.lightCoords = 0xF000F0; // lit by its own exhaust
            }
        }

        @Override
        public void submit(RocketState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            if (state.type == null) return;
            BlockStateModelPart rocket = Minecraft.getInstance().getModelManager()
                    .getStandaloneModel(state.type == SatelliteType.UPLINK ? ROCKET_UPLINK : ROCKET_SURVEY);
            if (rocket == null) return;
            pose.pushPose();
            pose.translate(0.5f + state.shakeX, 0.25f + state.height, 0.5f + state.shakeZ);
            pose.scale(1.5f, 1.5f, 1.5f);
            pose.translate(-0.5f, 0f, -0.5f);
            collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(rocket), new int[0],
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }

        @Override
        public boolean shouldRenderOffScreen() {
            return true;
        }

        @Override
        public int getViewDistance() {
            return 256;
        }

        @Override
        public AABB getRenderBoundingBox(LaunchControllerBlockEntity pad) {
            var p = pad.getBlockPos();
            return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 160, p.getZ() + 2);
        }
    }

    // ---------------------------------------------------------------- dish

    static final class DishState extends BlockEntityRenderState {
        float yaw;
    }

    /** The Ground Station's dish slowly sweeps around, as if tracking something overhead. */
    static final class DishRenderer implements BlockEntityRenderer<GroundStationBlockEntity, DishState> {
        DishRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public DishState createRenderState() {
            return new DishState();
        }

        @Override
        public void extractRenderState(GroundStationBlockEntity station, DishState state, float partialTicks, Vec3 camera,
                                       ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderer.super.extractRenderState(station, state, partialTicks, camera, breakProgress);
            float t = station.getLevel() == null ? 0 : (station.getLevel().getGameTime() % 24000L) + partialTicks;
            float phase = (station.getBlockPos().hashCode() & 0xFF) * 1.4f;
            // Sweep back and forth over ~150°, easing at the ends.
            state.yaw = phase + 75f * (float) Math.sin(t / 160.0);
        }

        @Override
        public void submit(DishState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            BlockStateModelPart dish = Minecraft.getInstance().getModelManager().getStandaloneModel(DISH);
            if (dish == null) return;
            pose.pushPose();
            pose.translate(0.5f, 0f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(state.yaw));
            pose.translate(-0.5f, 0f, -0.5f);
            collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(dish), new int[0],
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
