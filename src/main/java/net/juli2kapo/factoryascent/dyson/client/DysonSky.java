package net.juli2kapo.factoryascent.dyson.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Optional;
import java.util.OptionalDouble;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.space.client.SkyHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * The Dyson swarm around the sun, in the sky of every player whose team has one.
 *
 * <p>In the Overworld (and any other dimension with the vanilla sky) it is drawn right after the
 * vanilla sky ({@link RenderLevelStageEvent.AfterSky}) in the sun's own frame; the mod's own skies
 * (orbit, planets) call it through {@link SkyHooks} instead. Three passes: the far side (the
 * shell's sunlit inside through the gaps, far collectors glinting, the orbits), then the near side's
 * dark panels and the collectors' silhouettes against the sun, then the glowing seams and near
 * glints on top. Nothing writes depth, so the terrain drawn later hides the swarm like the sun.
 */
public final class DysonSky {
    /** Additive (like the stars and the sun): light added to the sky. */
    static final RenderPipeline GLOW = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withLocation(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "pipeline/dyson_glow"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();
    /** Normal alpha blending: dark panels and silhouettes that hide what is behind them. */
    static final RenderPipeline SHADE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withLocation(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "pipeline/dyson_shade"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();

    /** The visible sun disc is the middle quarter of the vanilla sun texture's width. */
    private static final float DISC_OF_QUAD = 8f / 32f;
    /** The vanilla sun quad's half-size. */
    private static final float VANILLA_SUN = 30f;

    private static final Layer FAR = new Layer("far", GLOW);
    private static final Layer NEAR_SHADE = new Layer("near shade", SHADE);
    private static final Layer NEAR_GLOW = new Layer("near glow", GLOW);

    private DysonSky() {}

    /** One pass: its geometry is rebuilt every frame into a reused vertex buffer. */
    private static final class Layer {
        final String name;
        final RenderPipeline pipeline;
        final ByteBufferBuilder bytes = new ByteBufferBuilder(1 << 16);
        @Nullable BufferBuilder builder;
        @Nullable GpuBuffer buffer;

        Layer(String name, RenderPipeline pipeline) {
            this.name = name;
            this.pipeline = pipeline;
        }

        BufferBuilder begin() {
            builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
            return builder;
        }

        void draw(Matrix4f pose, float alpha) {
            if (builder == null) return;
            try (MeshData mesh = builder.build()) {
                builder = null;
                if (mesh == null) return;
                int size = mesh.vertexBuffer().remaining();
                if (buffer == null || buffer.size() < size) {
                    if (buffer != null) buffer.close();
                    int cap = Integer.highestOneBit(Math.max(size, 1 << 16) - 1) << 1;
                    buffer = RenderSystem.getDevice().createBuffer(() -> "Dyson sky " + name, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, cap);
                }
                CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                encoder.writeToBuffer(buffer.slice(0, size), mesh.vertexBuffer());
                int indexCount = mesh.drawState().indexCount();
                GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(pose, new Vector4f(1f, 1f, 1f, alpha));
                RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                GpuBuffer indexBuffer = indices.getBuffer(indexCount);
                try (RenderPass pass = encoder.createRenderPass(() -> "Dyson swarm " + name,
                        target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                    pass.setPipeline(pipeline);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", transforms);
                    pass.setVertexBuffer(0, buffer.slice());
                    pass.setIndexBuffer(indexBuffer, indices.type());
                    pass.drawIndexed(indexCount, 1, 0, 0, 0);
                }
            }
        }
    }

    // ---------------------------------------------------------------- entry points

    /**
     * Vanilla skies: after the sky, in the sun's frame. The mod's own skies (dimensions of this
     * mod, or any sky NeoForge lets a custom renderer draw) decorate the sun through SkyHooks.
     */
    static void afterSky(RenderLevelStageEvent.AfterSky event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || DysonClient.collectors() <= 0) return;
        if (event.getLevelRenderState().customSkyboxRenderer != null) return;
        if (FactoryAscent.MOD_ID.equals(mc.level.dimension().identifier().getNamespace())) return;
        SkyRenderState sky = event.getLevelRenderState().skyRenderState;
        if (sky.skybox != DimensionType.Skybox.OVERWORLD) return;
        PoseStack pose = new PoseStack();
        pose.last().pose().set(event.getModelViewMatrix());
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.XP.rotation(sky.sunAngle));
        draw(pose.last().pose(), VANILLA_SUN * DISC_OF_QUAD, sky.rainBrightness);
    }

    /** The SkyHooks decorator (mod skies: orbit and planets). */
    static void aroundSun(PoseStack pose, float partialTick, ResourceKey<Level> dimension) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || DysonClient.collectors() <= 0) return;
        float alpha = 1f - mc.level.getRainLevel(partialTick);
        draw(pose.last().pose(), SkyHooks.sunHalfSize(dimension) * DISC_OF_QUAD, alpha);
    }

    // ---------------------------------------------------------------- the swarm

    private static void draw(Matrix4f sunFrame, float disc, float alpha) {
        if (alpha <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level == null ? 0 : (mc.level.getGameTime() % 240000L + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20f;
        long collectors = DysonClient.collectors();
        int target = DysonClient.target();
        DysonShape.Stage stage = DysonShape.stage(collectors, target);
        BufferBuilder far = FAR.begin(), shade = NEAR_SHADE.begin(), glow = NEAR_GLOW.begin();
        Emitter emitter = new Emitter(far, shade, glow, disc, stage, time);
        DysonShape.build(collectors, target, time, emitter, 1500, true, 48);
        // At high completion the closing shell dims the star a little (daylight is untouched).
        if (stage.completion() > 0.75f) {
            float dim = 0.35f * Math.min(1f, (stage.completion() - 0.75f) / 0.25f);
            quad(shade, -disc * 1.05f, 100f, -disc * 1.05f, disc * 1.05f, disc * 1.05f, 10, 6, 4, dim);
        }
        FAR.draw(sunFrame, alpha);
        NEAR_SHADE.draw(sunFrame, alpha);
        NEAR_GLOW.draw(sunFrame, alpha);
    }

    /** Turns the shape into quads in the sky's sun frame (sun at y = 100, the sky plane is x/z). */
    private static final class Emitter implements DysonShape.Sink {
        final BufferBuilder far, shade, glow;
        final float s;
        final DysonShape.Stage stage;
        final float time;

        Emitter(BufferBuilder far, BufferBuilder shade, BufferBuilder glow, float disc, DysonShape.Stage stage, float time) {
            this.far = far;
            this.shade = shade;
            this.glow = glow;
            this.s = disc;
            this.stage = stage;
            this.time = time;
        }

        private static boolean onDisc(float x, float z) {
            return Math.abs(x) < 1f && Math.abs(z) < 1f;
        }

        @Override
        public void collector(float x, float y, float z, float phase) {
            float px = x * s, pz = z * s, py = 100f + y * s;
            float size = 0.055f * s;
            if (y < 0 && onDisc(x, z)) {
                // Transiting the sun: a dark speck against the disc.
                quad(shade, px - size, py, pz - size, px + size, pz + size, 16, 12, 20, 0.92f);
                return;
            }
            if (y > 0 && onDisc(x, z)) return; // behind the sun's glare
            float hidden = 0f;
            if (y > 0 && stage.shell() > 0 && Math.max(Math.abs(x), Math.abs(z)) < DysonShape.SHELL * 1.2f) hidden = 0.85f * stage.shell();
            // A slow shimmer plus the odd bright flash as a panel catches the light.
            float wave = (float) Math.sin(time * (1.3 + phase * 2.1) + phase * 40.0);
            float flash = (float) Math.pow(Math.max(0, wave), 24);
            float light = (0.55f + 0.25f * wave * 0.5f + 0.9f * flash) * (1f - hidden);
            if (light <= 0.02f) return;
            BufferBuilder b = y > 0 ? far : glow;
            quad(b, px - size, py, pz - size, px + size, pz + size, 255, 236, 196, Math.min(1f, light));
            if (flash > 0.25f) {
                float arm = size * (2f + 6f * flash), thin = size * 0.28f;
                quad(b, px - arm, py, pz - thin, px + arm, pz + thin, 255, 246, 220, flash * 0.8f * (1f - hidden));
                quad(b, px - thin, py, pz - arm, px + thin, pz + arm, 255, 246, 220, flash * 0.8f * (1f - hidden));
            }
        }

        @Override
        public void panel(float[] c, float shade01) {
            float cy = (c[1] + c[4] + c[7] + c[10]) / 4f;
            float cx = (c[0] + c[3] + c[6] + c[9]) / 4f, cz = (c[2] + c[5] + c[8] + c[11]) / 4f;
            if (cy > 0) {
                // The far half: its sunlit inside shows through the gaps, glowing warm.
                if (onDisc(cx, cz)) return;
                float a = 0.22f + 0.1f * shade01;
                quad4(far, c, 255, 150, 60, a);
            } else {
                int base = 26 + (int) (shade01 * 22);
                quad4(shade, c, base, base - 4, base + 6, 0.80f);
            }
        }

        @Override
        public void line(float ax, float ay, float az, float bx, float by, float bz, int kind, float alpha) {
            boolean back = ay + by > 0;
            switch (kind) {
                case DysonShape.TRACK -> segment(back ? far : glow, ax, ay, az, bx, by, bz, 0.018f * s, 255, 214, 150, 0.10f);
                case DysonShape.STRUT -> {
                    if (back && onDisc((ax + bx) / 2, (az + bz) / 2)) return;
                    segment(back ? far : glow, ax, ay, az, bx, by, bz, 0.03f * s, 255, 184, 96, alpha * (back ? 0.22f : 0.55f));
                }
                default -> {
                    if (back) return;
                    float hot = onDisc((ax + bx) / 2, (az + bz) / 2) ? 1f : 0.65f;
                    segment(glow, ax, ay, az, bx, by, bz, 0.035f * s, 255, 196, 90, alpha * 0.85f * hot);
                }
            }
        }

        private void quad4(BufferBuilder b, float[] c, int r, int g, int bl, float a) {
            int ai = Math.round(Math.min(1f, a) * 255);
            for (int i = 0; i < 4; i++) {
                b.addVertex(c[i * 3] * s, 100f + c[i * 3 + 1] * s, c[i * 3 + 2] * s).setColor(r, g, bl, ai);
            }
        }

        /** A flat strip in the sky plane from a to b. */
        private void segment(BufferBuilder b, float ax, float ay, float az, float bx, float by, float bz, float width,
                             int r, int g, int bl, float a) {
            if (a <= 0.004f) return;
            float x0 = ax * s, z0 = az * s, x1 = bx * s, z1 = bz * s;
            float dx = x1 - x0, dz = z1 - z0;
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len < 1e-4f) return;
            float nx = -dz / len * width, nz = dx / len * width;
            float y0 = 100f + ay * s, y1 = 100f + by * s;
            int ai = Math.round(Math.min(1f, a) * 255);
            b.addVertex(x0 - nx, y0, z0 - nz).setColor(r, g, bl, ai);
            b.addVertex(x0 + nx, y0, z0 + nz).setColor(r, g, bl, ai);
            b.addVertex(x1 + nx, y1, z1 + nz).setColor(r, g, bl, ai);
            b.addVertex(x1 - nx, y1, z1 - nz).setColor(r, g, bl, ai);
        }
    }

    /** An axis-aligned quad in the sky plane at depth y. */
    private static void quad(BufferBuilder b, float x0, float y, float z0, float x1, float z1, int r, int g, int bl, float a) {
        int ai = Math.round(Math.max(0f, Math.min(1f, a)) * 255);
        if (ai == 0) return;
        b.addVertex(x0, y, z0).setColor(r, g, bl, ai);
        b.addVertex(x1, y, z0).setColor(r, g, bl, ai);
        b.addVertex(x1, y, z1).setColor(r, g, bl, ai);
        b.addVertex(x0, y, z1).setColor(r, g, bl, ai);
    }
}
