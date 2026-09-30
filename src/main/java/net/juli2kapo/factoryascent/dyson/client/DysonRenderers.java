package net.juli2kapo.factoryascent.dyson.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.dyson.DysonMonitorBlockEntity;
import net.juli2kapo.factoryascent.dyson.DysonReceiverBlockEntity;
import net.juli2kapo.factoryascent.dyson.MassDriverBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Block entity renderers of the Dyson project. */
final class DysonRenderers {
    static final StandaloneModelKey<BlockStateModelPart> DISH =
            new StandaloneModelKey<>(() -> FactoryAscent.MOD_ID + ":dyson_receiver_dish");

    private DysonRenderers() {}

    /** A glowing box outline-ish face pair: both windings so it shows from both sides. */
    private static void quad(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int argb) {
        b.addVertex(m, x0, y0, z0).setColor(argb);
        b.addVertex(m, x1, y1, z1).setColor(argb);
        b.addVertex(m, x2, y2, z2).setColor(argb);
        b.addVertex(m, x3, y3, z3).setColor(argb);
        b.addVertex(m, x3, y3, z3).setColor(argb);
        b.addVertex(m, x2, y2, z2).setColor(argb);
        b.addVertex(m, x1, y1, z1).setColor(argb);
        b.addVertex(m, x0, y0, z0).setColor(argb);
    }

    /** The four vertical sides of a box centred on x/z = 0.5 with half-size h, from y0 to y1. */
    private static void band(VertexConsumer b, Matrix4f m, float h, float y0, float y1, int argb) {
        float lo = 0.5f - h, hi = 0.5f + h;
        quad(b, m, lo, y0, lo, hi, y0, lo, hi, y1, lo, lo, y1, lo, argb);
        quad(b, m, lo, y0, hi, hi, y0, hi, hi, y1, hi, lo, y1, hi, argb);
        quad(b, m, lo, y0, lo, lo, y0, hi, lo, y1, hi, lo, y1, lo, argb);
        quad(b, m, hi, y0, lo, hi, y0, hi, hi, y1, hi, hi, y1, lo, argb);
    }

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------- mass driver

    static final class DriverState extends BlockEntityRenderState {
        int charge;
        float shot = -1;
        float time;
    }

    /**
     * The Mass Driver: its coils glow rail by rail as the capacitor charges, flash white when it
     * fires, and the shot leaves as a blinding streak that climbs out of sight, then fades.
     */
    static final class MassDriverRenderer implements BlockEntityRenderer<MassDriverBlockEntity, DriverState> {
        MassDriverRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public DriverState createRenderState() {
            return new DriverState();
        }

        @Override
        public void extractRenderState(MassDriverBlockEntity driver, DriverState state, float partialTicks, Vec3 camera,
                                       ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderer.super.extractRenderState(driver, state, partialTicks, camera, breakProgress);
            state.charge = driver.chargeRails();
            state.shot = driver.getLevel() == null ? -1 : driver.shotAge(driver.getLevel(), partialTicks);
            if (state.shot > MassDriverBlockEntity.SHOT_TICKS) state.shot = -1;
            state.time = driver.getLevel() == null ? 0 : (driver.getLevel().getGameTime() % 24000L) + partialTicks;
        }

        @Override
        public void submit(DriverState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            int rails = MassDriverBlockEntity.RAILS;
            float shot = state.shot;
            float flash = shot >= 0 ? Math.max(0f, 1f - shot / 8f) : 0f;
            float pulse = 0.75f + 0.25f * (float) Math.sin(state.time * 0.6f);
            collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                Matrix4f m = p.pose();
                for (int i = 0; i < rails; i++) {
                    boolean lit = i < state.charge;
                    int a = flash > 0 ? (int) (90 + 165 * flash) : lit ? (int) (150 * pulse) : 18;
                    int r = flash > 0 ? 220 : 60, g = flash > 0 ? 250 : 200, bl = 255;
                    for (float coil : new float[] {4f / 16f, 10f / 16f}) {
                        float y = 1 + i + coil;
                        band(b, m, 6.4f / 16f, y, y + 2f / 16f, argb(a, r, g, bl));
                    }
                }
                if (shot >= 0) {
                    // The trail: a glowing column from the muzzle to the slug, fading after the shot.
                    float muzzle = rails + 1f;
                    float top = muzzle + Math.min(300f, 2f + shot * shot * 2.5f);
                    float fade = 1f - shot / MassDriverBlockEntity.SHOT_TICKS;
                    int a = (int) (200 * fade);
                    band(b, m, 0.09f, muzzle, top, argb(a, 150, 240, 255));
                    band(b, m, 0.2f, muzzle, top, argb(a / 3, 90, 200, 255));
                    // the slug itself
                    float s = top - 0.6f;
                    band(b, m, 0.16f, s, top, argb((int) (255 * Math.max(0.3f, fade)), 255, 255, 255));
                }
            });
            if (shot >= 0 && shot < 6) {
                int h = (int) Math.min(512, 4 + shot * shot * 2.5f);
                BeaconRenderer.submitBeaconBeam(pose, collector, BeaconRenderer.BEAM_LOCATION, 1f, state.time, rails + 1, h,
                        0xFFB8F4FF, 0.14f, 0.26f);
            }
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
        public AABB getRenderBoundingBox(MassDriverBlockEntity driver) {
            var p = driver.getBlockPos();
            return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 320, p.getZ() + 2);
        }
    }

    // ---------------------------------------------------------------- receiver

    static final class ReceiverState extends BlockEntityRenderState {
        float tilt;
        boolean active;
        float time;
    }

    /**
     * The Dyson Receiver's 3-block dish follows the sun across the sky (parks facing up at
     * night) and, while the swarm beams power down, a thin golden beam links it to the sun.
     */
    static final class ReceiverRenderer implements BlockEntityRenderer<DysonReceiverBlockEntity, ReceiverState> {
        ReceiverRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public ReceiverState createRenderState() {
            return new ReceiverState();
        }

        @Override
        public void extractRenderState(DysonReceiverBlockEntity receiver, ReceiverState state, float partialTicks, Vec3 camera,
                                       ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderer.super.extractRenderState(receiver, state, partialTicks, camera, breakProgress);
            float sun = Minecraft.getInstance().gameRenderer.mainCamera().attributeProbe()
                    .getValue(EnvironmentAttributes.SUN_ANGLE, partialTicks);
            sun = ((sun % 360f) + 540f) % 360f - 180f; // (-180, 180], 0 = noon
            state.tilt = Math.abs(sun) > 95f ? 0f : Math.max(-72f, Math.min(72f, sun));
            state.active = receiver.active();
            state.time = receiver.getLevel() == null ? 0 : (receiver.getLevel().getGameTime() % 24000L) + partialTicks;
            if (state.active) state.lightCoords = 0xF000F0;
        }

        @Override
        public void submit(ReceiverState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            BlockStateModelPart dish = Minecraft.getInstance().getModelManager().getStandaloneModel(DISH);
            pose.pushPose();
            pose.translate(0.5f, 10f / 16f, 0.5f);
            pose.mulPose(Axis.ZP.rotationDegrees(state.tilt));
            if (dish != null) {
                pose.pushPose();
                pose.scale(3f, 3f, 3f);
                pose.translate(-0.5f, 0f, -0.5f);
                collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(dish), new int[0],
                        state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
            if (state.active) {
                pose.pushPose();
                pose.translate(-0.5f, 0f, -0.5f);
                BeaconRenderer.submitBeaconBeam(pose, collector, BeaconRenderer.BEAM_LOCATION, 1f, state.time, 2, 400,
                        0xFFFFD480, 0.05f, 0.11f);
                pose.popPose();
                float glow = 0.7f + 0.3f * (float) Math.sin(state.time * 0.4f);
                collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                    Matrix4f m = new Matrix4f(p.pose()).translate(-0.5f, 0f, -0.5f);
                    band(b, m, 0.12f, 1.7f, 1.95f, argb((int) (200 * glow), 255, 210, 120));
                });
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
        public AABB getRenderBoundingBox(DysonReceiverBlockEntity receiver) {
            var p = receiver.getBlockPos();
            return new AABB(p.getX() - 2, p.getY(), p.getZ() - 2, p.getX() + 3, p.getY() + 400, p.getZ() + 3);
        }
    }

    // ---------------------------------------------------------------- monitor hologram

    static final class MonitorState extends BlockEntityRenderState {
        float time;
        long collectors;
        int target;
    }

    /** A turning hologram of the team's sphere floats over the Dyson Monitor. */
    static final class MonitorRenderer implements BlockEntityRenderer<DysonMonitorBlockEntity, MonitorState> {
        private static final float SCALE = 0.19f;

        MonitorRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public MonitorState createRenderState() {
            return new MonitorState();
        }

        @Override
        public void extractRenderState(DysonMonitorBlockEntity monitor, MonitorState state, float partialTicks, Vec3 camera,
                                       ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderer.super.extractRenderState(monitor, state, partialTicks, camera, breakProgress);
            state.time = monitor.getLevel() == null ? 0 : ((monitor.getLevel().getGameTime() % 240000L) + partialTicks) / 20f;
            state.collectors = DysonClient.collectors();
            state.target = DysonClient.target();
        }

        @Override
        public void submit(MonitorState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            pose.pushPose();
            pose.translate(0.5f, 1.55f, 0.5f);
            pose.mulPose(Axis.YP.rotation(state.time * 0.35f));
            pose.mulPose(Axis.XP.rotationDegrees(-90f)); // shape depth (y) → horizontal, sky-plane z → up
            pose.scale(SCALE, SCALE, SCALE);
            collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
                Matrix4f m = p.pose();
                // the star
                float pulse = 0.85f + 0.15f * (float) Math.sin(state.time * 3f);
                cube(b, m, 0, 0, 0, 0.9f, argb((int) (230 * pulse), 255, 220, 90));
                cube(b, m, 0, 0, 0, 1.25f, argb(60, 255, 170, 40));
                DysonShape.build(state.collectors, state.target, state.time * 3f, new DysonShape.Sink() {
                    @Override
                    public void collector(float x, float y, float z, float phase) {
                        cube(b, m, x, y, z, 0.07f, argb(220, 120, 240, 255));
                    }

                    @Override
                    public void panel(float[] c, float shade) {
                        int col = argb(70 + (int) (shade * 40), 60, 170, 255);
                        quad(b, m, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10], c[11], col);
                    }

                    @Override
                    public void line(float ax, float ay, float az, float bx, float by, float bz, int kind, float alpha) {
                        int a = (int) ((kind == DysonShape.TRACK ? 50 : kind == DysonShape.SEAM ? 160 : 120) * alpha);
                        float w = 0.035f;
                        quad(b, m, ax - w, ay, az, ax + w, ay, az, bx + w, by, bz, bx - w, by, bz, argb(a, 120, 220, 255));
                        quad(b, m, ax, ay, az - w, ax, ay, az + w, bx, by, bz + w, bx, by, bz - w, argb(a, 120, 220, 255));
                    }
                }, 400, true, 32);
            });
            pose.popPose();
        }

        private static void cube(VertexConsumer b, Matrix4f m, float x, float y, float z, float h, int col) {
            quad(b, m, x - h, y - h, z - h, x + h, y - h, z - h, x + h, y + h, z - h, x - h, y + h, z - h, col);
            quad(b, m, x - h, y - h, z + h, x + h, y - h, z + h, x + h, y + h, z + h, x - h, y + h, z + h, col);
            quad(b, m, x - h, y - h, z - h, x - h, y - h, z + h, x - h, y + h, z + h, x - h, y + h, z - h, col);
            quad(b, m, x + h, y - h, z - h, x + h, y - h, z + h, x + h, y + h, z + h, x + h, y + h, z - h, col);
            quad(b, m, x - h, y + h, z - h, x + h, y + h, z - h, x + h, y + h, z + h, x - h, y + h, z + h, col);
            quad(b, m, x - h, y - h, z - h, x + h, y - h, z - h, x + h, y - h, z + h, x - h, y - h, z + h, col);
        }

        @Override
        public AABB getRenderBoundingBox(DysonMonitorBlockEntity monitor) {
            var p = monitor.getBlockPos();
            return new AABB(p.getX() - 1, p.getY(), p.getZ() - 1, p.getX() + 2, p.getY() + 3, p.getZ() + 2);
        }
    }
}
