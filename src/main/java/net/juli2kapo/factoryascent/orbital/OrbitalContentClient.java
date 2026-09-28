package net.juli2kapo.factoryascent.orbital;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.orbital.client.RadarScreen;
import net.juli2kapo.factoryascent.orbital.client.TeamScreen;
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
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the Orbital age: the rocket on the Launch Pad, the Ground Station's sweeping dish,
 * the Orbital Radar's spinning antenna, and the Team and Radar screens opened by server packets.
 */
public final class OrbitalContentClient {
    // The launch vehicle (tools/features/satellites.py): stages, payload adapter, fairing halves,
    // the anti-satellite kill vehicle, the exhaust plume and the satellites with folded wings.
    static final StandaloneModelKey<BlockStateModelPart> LV_LOWER = key("lv_stage_lower");
    static final StandaloneModelKey<BlockStateModelPart> LV_UPPER = key("lv_stage_upper");
    static final StandaloneModelKey<BlockStateModelPart> LV_LOWER_ASAT = key("lv_stage_lower_asat");
    static final StandaloneModelKey<BlockStateModelPart> LV_UPPER_ASAT = key("lv_stage_upper_asat");
    static final StandaloneModelKey<BlockStateModelPart> LV_ADAPTER = key("lv_adapter");
    static final StandaloneModelKey<BlockStateModelPart> LV_FAIRING_A = key("lv_fairing_a");
    static final StandaloneModelKey<BlockStateModelPart> LV_FAIRING_B = key("lv_fairing_b");
    static final StandaloneModelKey<BlockStateModelPart> LV_KILL_VEHICLE = key("lv_kill_vehicle");
    static final StandaloneModelKey<BlockStateModelPart> LV_PLUME = key("lv_plume");
    static final StandaloneModelKey<BlockStateModelPart> PAYLOAD_SURVEY = key("satellite_survey_stowed");
    static final StandaloneModelKey<BlockStateModelPart> PAYLOAD_UPLINK = key("satellite_uplink_stowed");
    static final StandaloneModelKey<BlockStateModelPart> PAYLOAD_GUARDIAN = key("satellite_guardian_stowed");
    static final StandaloneModelKey<BlockStateModelPart> DISH = key("ground_station_dish");
    static final StandaloneModelKey<BlockStateModelPart> RADAR_ANTENNA = key("orbital_radar_antenna");

    private OrbitalContentClient() {}

    private static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> FactoryAscent.MOD_ID + ":" + name);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((ModelEvent.RegisterStandalone e) -> {
            for (var entry : List.of(
                    java.util.Map.entry(LV_LOWER, "lv_stage_lower"),
                    java.util.Map.entry(LV_UPPER, "lv_stage_upper"),
                    java.util.Map.entry(LV_LOWER_ASAT, "lv_stage_lower_asat"),
                    java.util.Map.entry(LV_UPPER_ASAT, "lv_stage_upper_asat"),
                    java.util.Map.entry(LV_ADAPTER, "lv_adapter"),
                    java.util.Map.entry(LV_FAIRING_A, "lv_fairing_a"),
                    java.util.Map.entry(LV_FAIRING_B, "lv_fairing_b"),
                    java.util.Map.entry(LV_KILL_VEHICLE, "lv_kill_vehicle"),
                    java.util.Map.entry(LV_PLUME, "lv_plume"),
                    java.util.Map.entry(PAYLOAD_SURVEY, "satellite_survey_stowed"),
                    java.util.Map.entry(PAYLOAD_UPLINK, "satellite_uplink_stowed"),
                    java.util.Map.entry(PAYLOAD_GUARDIAN, "satellite_guardian_stowed"),
                    java.util.Map.entry(DISH, "ground_station_dish"),
                    java.util.Map.entry(RADAR_ANTENNA, "orbital_radar_antenna"))) {
                e.register(entry.getKey(), SimpleUnbakedStandaloneModel.simpleModelWrapper(
                        Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "block/" + entry.getValue())));
            }
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(OrbitalContent.LAUNCH_CONTROLLER_BE.get(), RocketRenderer::new);
            e.registerBlockEntityRenderer(OrbitalContent.GROUND_STATION_BE.get(), DishRenderer::new);
            e.registerBlockEntityRenderer(OrbitalContent.ORBITAL_RADAR_BE.get(), RadarRenderer::new);
        });
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> {
            e.register(OrbitalPayloads.TeamView.TYPE, (payload, context) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof TeamScreen screen) {
                    screen.update(payload);
                } else if (payload.open()) {
                    mc.gui.setScreen(new TeamScreen(payload));
                }
            });
            e.register(OrbitalPayloads.RadarView.TYPE, (payload, context) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gui.screen() instanceof RadarScreen screen && screen.pos().equals(payload.pos())) {
                    screen.update(payload);
                } else if (payload.open()) {
                    mc.gui.setScreen(new RadarScreen(payload));
                }
            });
        });
    }

    // ---------------------------------------------------------------- rocket

    static final class RocketState extends BlockEntityRenderState {
        /** Whether a payload is mounted (no rocket otherwise). */
        boolean present;
        /** The satellite inside the fairing, or null for the anti-satellite missile. */
        @Nullable StandaloneModelKey<BlockStateModelPart> payload;
        boolean missile;
        /** Blocks above the pad. */
        float height;
        /** Pre-launch rumble offset. */
        float shakeX, shakeZ;
        /** Fairing halves: opening angle (degrees) and, once jettisoned, their drift from the rocket. */
        float fairingAngle, fairingOut, fairingDrop;
        /** Exhaust plume length (0: off) and flicker. */
        float plume;
    }

    /**
     * The launch vehicle standing on the pad while a payload is mounted: two stages, a black
     * interstage, grid fins and five engine bells, with the satellite (wings folded) inside a
     * fairing whose halves stand open on the pad so everyone can see what is going up; the
     * anti-satellite missile rides bare as a dark kill vehicle. When the countdown starts the
     * fairing closes; the rocket rumbles, then climbs at {@link LaunchControllerBlockEntity#ACCEL}·t²
     * blocks (the same curve the server's exhaust trail follows) on a flickering plume, and
     * jettisons the fairing halves once {@link #FAIRING_SEPARATION} blocks up.
     */
    static final class RocketRenderer implements BlockEntityRenderer<LaunchControllerBlockEntity, RocketState> {
        // Mirrors SEGMENT_BASE / FAIRING_BASE / PAYLOAD_BASE / PAYLOAD_SCALE / RF in tools/features/satellites.py (rocket pixels).
        private static final float LOWER_BASE = 15.5f, UPPER_BASE = 63.5f, ADAPTER_BASE = 80f, FAIRING_MODEL_BASE = 110f,
                KV_BASE = 102f, PLUME_BASE = -30.5f, FAIRING_HINGE = 94f, PAYLOAD_BASE = 95f, PAYLOAD_SCALE = 1.3f, FAIRING_RADIUS = 8.5f;
        private static final float OPEN_ANGLE = 45f;
        /** Ticks the fairing takes to close when the countdown starts. */
        private static final float CLOSE_TICKS = 20f;
        static final float FAIRING_SEPARATION = 30f;

        RocketRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public RocketState createRenderState() {
            return new RocketState();
        }

        @Override
        public void extractRenderState(LaunchControllerBlockEntity pad, RocketState state, float partialTicks, Vec3 camera,
                                       ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderer.super.extractRenderState(pad, state, partialTicks, camera, breakProgress);
            SatelliteType type = pad.satelliteType();
            state.missile = pad.hasMissile();
            state.present = state.missile || type != null;
            state.payload = type == null ? null : switch (type) {
                case SURVEY -> PAYLOAD_SURVEY;
                case UPLINK -> PAYLOAD_UPLINK;
                case DEFENSE -> PAYLOAD_GUARDIAN;
            };
            state.height = 0;
            state.shakeX = state.shakeZ = 0;
            state.fairingAngle = OPEN_ANGLE;
            state.fairingOut = state.fairingDrop = 0;
            state.plume = 0;
            if (pad.launchStart() >= 0 && pad.getLevel() != null) {
                float t = pad.getLevel().getGameTime() - pad.launchStart() + partialTicks;
                float liftoff = LaunchControllerBlockEntity.LIFTOFF;
                float accel = LaunchControllerBlockEntity.ACCEL;
                state.fairingAngle = OPEN_ANGLE * Math.max(0f, 1f - t / CLOSE_TICKS);
                float flicker = 0.85f + 0.15f * (float) Math.sin(t * 2.7) * (float) Math.cos(t * 1.3);
                if (t > liftoff) {
                    float dt = t - liftoff;
                    state.height = accel * dt * dt;
                    state.plume = Math.min(1.4f, 0.8f + dt * 0.03f) * flicker;
                    float sep = (float) Math.sqrt(FAIRING_SEPARATION / accel);
                    if (dt > sep) {
                        // Jettisoned: the halves swing open, drift apart and fall behind the rocket.
                        float tau = dt - sep;
                        state.fairingAngle = Math.min(160f, 12f + tau * 9f);
                        state.fairingOut = 0.25f * tau;
                        state.fairingDrop = (accel + 0.04f) * tau * tau;
                    }
                } else if (t > liftoff - 10) {
                    state.plume = 0.35f * flicker; // ignition
                }
                if (t > liftoff / 2f && t <= liftoff) {
                    float amp = 0.02f * (t / liftoff);
                    state.shakeX = (float) Math.sin(t * 7.3) * amp;
                    state.shakeZ = (float) Math.cos(t * 9.1) * amp;
                }
                if (state.height > 0) state.lightCoords = 0xF000F0; // lit by its own exhaust
            }
        }

        private static void part(PoseStack pose, SubmitNodeCollector collector, StandaloneModelKey<BlockStateModelPart> key,
                                 float basePx, int light) {
            BlockStateModelPart model = Minecraft.getInstance().getModelManager().getStandaloneModel(key);
            if (model == null) return;
            pose.pushPose();
            pose.translate(-0.5f, basePx / 16f, -0.5f);
            collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(model), new int[0],
                    light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }

        @Override
        public void submit(RocketState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            if (!state.present) return;
            int light = state.lightCoords;
            pose.pushPose();
            pose.translate(0.5f + state.shakeX, 0.25f + state.height, 0.5f + state.shakeZ);
            part(pose, collector, state.missile ? LV_LOWER_ASAT : LV_LOWER, LOWER_BASE, light);
            part(pose, collector, state.missile ? LV_UPPER_ASAT : LV_UPPER, UPPER_BASE, light);
            if (state.missile) {
                part(pose, collector, LV_KILL_VEHICLE, KV_BASE, light);
            } else {
                part(pose, collector, LV_ADAPTER, ADAPTER_BASE, light);
                if (state.payload != null) {
                    BlockStateModelPart sat = Minecraft.getInstance().getModelManager().getStandaloneModel(state.payload);
                    if (sat != null) {
                        pose.pushPose();
                        pose.translate(0f, PAYLOAD_BASE / 16f, 0f);
                        pose.scale(PAYLOAD_SCALE, PAYLOAD_SCALE, PAYLOAD_SCALE);
                        pose.translate(-0.5f, 0f, -0.5f);
                        collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(sat), new int[0],
                                light, OverlayTexture.NO_OVERLAY, 0);
                        pose.popPose();
                    }
                }
                for (int side : new int[] {1, -1}) {
                    float hinge = side * FAIRING_RADIUS / 16f;
                    pose.pushPose();
                    pose.translate(side * state.fairingOut, -state.fairingDrop, 0f);
                    pose.translate(hinge, FAIRING_HINGE / 16f, 0f);
                    pose.mulPose(Axis.ZP.rotationDegrees(-side * state.fairingAngle));
                    pose.translate(-hinge, -FAIRING_HINGE / 16f, 0f);
                    part(pose, collector, side > 0 ? LV_FAIRING_A : LV_FAIRING_B, FAIRING_MODEL_BASE, light);
                    pose.popPose();
                }
            }
            if (state.plume > 0) {
                BlockStateModelPart plume = Minecraft.getInstance().getModelManager().getStandaloneModel(LV_PLUME);
                if (plume != null) {
                    pose.pushPose();
                    pose.translate(0f, 0.5f / 16f, 0f); // the bells' lip
                    pose.scale(1f, state.plume, 1f);
                    pose.translate(-0.5f, (PLUME_BASE - 0.5f) / 16f, -0.5f);
                    collector.submitBlockModel(pose, Sheets.translucentBlockItemSheet(), List.of(plume), new int[0],
                            0xF000F0, OverlayTexture.NO_OVERLAY, 0);
                    pose.popPose();
                }
            }
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

    // ---------------------------------------------------------------- radar

    /**
     * The Orbital Radar's antenna turns steadily around; while it tracks a contact it spins
     * three times as fast.
     */
    static final class RadarRenderer implements BlockEntityRenderer<OrbitalRadarBlockEntity, DishState> {
        RadarRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public DishState createRenderState() {
            return new DishState();
        }

        @Override
        public void extractRenderState(OrbitalRadarBlockEntity radar, DishState state, float partialTicks, Vec3 camera,
                                       ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderer.super.extractRenderState(radar, state, partialTicks, camera, breakProgress);
            float t = radar.getLevel() == null ? 0 : (radar.getLevel().getGameTime() % 72000L) + partialTicks;
            state.yaw = (t * (radar.isActive() ? 6f : 2f)) % 360f;
        }

        @Override
        public void submit(DishState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            BlockStateModelPart antenna = Minecraft.getInstance().getModelManager().getStandaloneModel(RADAR_ANTENNA);
            if (antenna == null) return;
            pose.pushPose();
            pose.translate(0.5f, 0f, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(state.yaw));
            pose.translate(-0.5f, 0f, -0.5f);
            collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(antenna), new int[0],
                    state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
