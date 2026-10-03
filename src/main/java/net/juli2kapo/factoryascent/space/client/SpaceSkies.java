package net.juli2kapo.factoryascent.space.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
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
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.juli2kapo.factoryascent.space.planet.Planet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * What the mod adds to the skies, drawn right after the vanilla sky (the dimension types give the
 * sky colour, the stars and the sun):
 *
 * <ul>
 *   <li>Earth orbit: the planet below filling the lower sky, turning slowly, and the Moon.</li>
 *   <li>The Moon: Earth hanging in the black sky.</li>
 *   <li>Mars: its two little moons, Phobos racing across the sky and Deimos crawling, and Earth as
 *       a blue evening star.</li>
 *   <li>Io: giant banded Jupiter filling a fifth of the sky, and Europa passing by.</li>
 *   <li>The Overworld at night: every satellite in orbit as a small moving point of light
 *       ({@link SatelliteSkyClient}).</li>
 * </ul>
 *
 * Then, in orbit and on the planets, the {@link SkyHooks} sun decorators, and last, on the
 * planets, the ground disc that hides whatever is below the horizon (see {@link #groundDisc}).
 */
final class SpaceSkies {
    private static final Identifier EARTH = tex("earth"), MOON = tex("moon_disc"), PHOBOS = tex("phobos"), DEIMOS = tex("deimos"),
            JUPITER = tex("jupiter"), EUROPA = tex("europa"), DOT = tex("satellite_dot");
    /** Like the sun and moon's pipeline, but blended normally so a planet hides the stars behind it. */
    static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION)
            .withLocation(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "pipeline/orbit_planet"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build();
    /** Half-size of Earth under the orbit dimension at distance 100 (it fills most of the view straight down). */
    private static final float EARTH_BELOW = 150f;

    private static @Nullable GpuBuffer quad, ground;

    private SpaceSkies() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "textures/environment/" + name + ".png");
    }

    private static GpuBuffer quad() {
        if (quad == null) {
            try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
                BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
                b.addVertex(-1f, 0f, -1f).setUv(0f, 0f);
                b.addVertex(1f, 0f, -1f).setUv(1f, 0f);
                b.addVertex(1f, 0f, 1f).setUv(1f, 1f);
                b.addVertex(-1f, 0f, 1f).setUv(0f, 1f);
                try (MeshData mesh = b.buildOrThrow()) {
                    quad = RenderSystem.getDevice().createBuffer(() -> "Space sky quad", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                }
            }
        }
        return quad;
    }

    /** A disc under the camera, like vanilla's bottom sky disc: the planet's surface below the horizon. */
    private static GpuBuffer ground() {
        if (ground == null) {
            try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(10 * DefaultVertexFormat.POSITION.getVertexSize())) {
                BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
                b.addVertex(0f, -16f, 0f);
                for (int i = -180; i <= 180; i += 45) {
                    float a = (float) Math.toRadians(i);
                    b.addVertex(-512f * (float) Math.cos(a), -16f, 512f * (float) Math.sin(a));
                }
                try (MeshData mesh = b.buildOrThrow()) {
                    ground = RenderSystem.getDevice().createBuffer(() -> "Planet ground disc", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                }
            }
        }
        return ground;
    }

    /**
     * Hides everything below the horizon of a planet: the planet itself is there. Without it the
     * sun, the moons and the rest of the sky went on under your feet, so a shuttle coming down from
     * high above the terrain saw pictures of moons and planets (and the vanilla moon, parked at the
     * nadir) beneath it. Drawn in the fog colour, like the planet's surface fading into haze.
     */
    private static void groundDisc(Matrix4f modelView, ClientLevel level, float partial) {
        int fog;
        try {
            fog = Minecraft.getInstance().gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.FOG_COLOR, partial);
        } catch (RuntimeException e) {
            fog = 0;
        }
        Vector4f color = new Vector4f(((fog >> 16) & 0xFF) / 255f, ((fog >> 8) & 0xFF) / 255f, (fog & 0xFF) / 255f, 1f);
        Matrix4f pose = new Matrix4f(modelView).translate(0f, 12f, 0f);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(pose, color);
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Planet ground",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.SKY);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, ground().slice());
            pass.draw(10, 1, 0, 0);
        }
    }

    /** Whether a disc of half-size {@code size} (at distance 100) in direction {@code dir} shows above the horizon. */
    static boolean aboveHorizon(Vector3f dir, float size) {
        Vector3f d = new Vector3f(dir).normalize();
        return d.y > -size / 100f;
    }

    /** Draws a textured disc (a quad at distance 100 facing the camera's origin) with the given transform. */
    static void draw(Matrix4f pose, Identifier texture, Vector4f color) {
        Minecraft mc = Minecraft.getInstance();
        AbstractTexture t = mc.getTextureManager().getTexture(texture);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(pose, color);
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(6);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Space sky object",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", t.getTextureView(), t.getSampler());
            pass.setVertexBuffer(0, quad().slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(6, 1, 0, 0, 0);
        }
    }

    /**
     * A disc of half-size {@code size} in the direction {@code dir} (sky space), kept upright (the
     * texture's rows stay level with the horizon) and then turned by {@code roll} about its own axis.
     */
    static void disc(Matrix4f modelView, Vector3f dir, float size, float roll, Identifier texture, Vector4f color) {
        if (!aboveHorizon(dir, size) && Planet.of(Minecraft.getInstance().level.dimension()) != null) return; // inside the planet
        Vector3f d = new Vector3f(dir).normalize();
        float yaw = (float) Math.atan2(d.x, d.z);
        float tilt = (float) Math.acos(Math.max(-1f, Math.min(1f, d.y)));
        Matrix4f pose = new Matrix4f(modelView)
                .rotateY(yaw)
                .rotateX(tilt)
                .rotateY(roll)
                .translate(0f, 100f, 0f)
                .scale(size, 1f, size);
        draw(pose, texture, color);
    }

    /** Direction from azimuth (degrees, 0 = south, 90 = west, like yaw) and elevation (degrees above the horizon). */
    static Vector3f dir(double azimuth, double elevation) {
        double a = Math.toRadians(azimuth), e = Math.toRadians(elevation);
        return new Vector3f((float) (-Math.sin(a) * Math.cos(e)), (float) Math.sin(e), (float) (Math.cos(a) * Math.cos(e)));
    }

    static void afterSky(RenderLevelStageEvent.AfterSky event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        var dim = level.dimension();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double time = level.getGameTime() + partial;
        Matrix4f mv = new Matrix4f(event.getModelViewMatrix());
        Vector4f white = new Vector4f(1f, 1f, 1f, 1f);
        Planet planet = Planet.of(dim);
        if (dim == SpaceRules.ORBIT) {
            float spin = (float) ((level.getGameTime() % 72000L) * 0.005f);
            Matrix4f earth = new Matrix4f(mv).rotateX((float) Math.PI).rotateY((float) Math.toRadians(spin))
                    .translate(0f, 100f, 0f).scale(EARTH_BELOW, 1f, EARTH_BELOW);
            draw(earth, EARTH, white);
            disc(mv, dir(110, 24), 5.5f, 0.4f, MOON, white);
        } else if (planet == Planet.MOON) {
            float spin = (float) ((time % 240000.0) * 0.0004);
            disc(mv, dir(200, 52), 11f, spin, EARTH, white);
        } else if (planet == Planet.MARS) {
            float night = 1f - brightness(level, partial);
            // Phobos rises in the west and races east in a few minutes; Deimos crawls the other way.
            double pa = (time * 0.05) % 360.0, da = (time * 0.006 + 140) % 360.0;
            disc(mv, orbitDir(pa, 28), 3.6f, 0f, PHOBOS, new Vector4f(1f, 1f, 1f, 0.55f + 0.45f * night));
            disc(mv, orbitDir(da, -18), 2.0f, 0f, DEIMOS, new Vector4f(1f, 1f, 1f, 0.45f + 0.55f * night));
            if (night > 0.3f) disc(mv, dir(250, 20), 0.55f, 0f, DOT, new Vector4f(0.55f, 0.75f, 1f, night));
        } else if (planet == Planet.IO) {
            disc(mv, dir(10, 34), 58f, 0.05f, JUPITER, white);
            double ea = (time * 0.012) % 360.0;
            disc(mv, orbitDir(ea, 12), 2.2f, 0f, EUROPA, white);
        }
        SatelliteSkyClient.afterSky(mv, level, partial);
        if (SkyHooks.any() && (dim == SpaceRules.ORBIT || planet != null)) sunDecorations(mv, level, partial, dim);
        if (planet != null) groundDisc(mv, level, partial);
    }

    /** A moon crossing the sky on a great circle tilted by {@code tilt} degrees; {@code angle} along it. */
    private static Vector3f orbitDir(double angle, double tilt) {
        double a = Math.toRadians(angle), t = Math.toRadians(tilt);
        Vector3f v = new Vector3f((float) Math.cos(a), (float) Math.sin(a), 0f);
        return new Vector3f(v.x, (float) (v.y * Math.cos(t)), (float) (v.y * Math.sin(t)));
    }

    /** 1 at noon, 0 at night (the sky light factor). */
    static float brightness(ClientLevel level, float partial) {
        Minecraft mc = Minecraft.getInstance();
        try {
            return Math.max(0f, Math.min(1f, mc.gameRenderer.mainCamera().attributeProbe()
                    .getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partial)));
        } catch (RuntimeException e) {
            return 1f;
        }
    }

    /** Stars' brightness right now (0 by day in the Overworld). */
    static float starBrightness(float partial) {
        Minecraft mc = Minecraft.getInstance();
        try {
            return mc.gameRenderer.mainCamera().attributeProbe().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partial);
        } catch (RuntimeException e) {
            return 0f;
        }
    }

    private static void sunDecorations(Matrix4f mv, ClientLevel level, float partial, net.minecraft.resources.ResourceKey<Level> dim) {
        float sunAngle;
        try {
            sunAngle = Minecraft.getInstance().gameRenderer.mainCamera().attributeProbe()
                    .getValue(EnvironmentAttributes.SUN_ANGLE, partial) * ((float) Math.PI / 180f);
        } catch (RuntimeException e) {
            return;
        }
        PoseStack pose = new PoseStack();
        pose.last().pose().set(mv);
        pose.mulPose(Axis.YP.rotationDegrees(-90f));
        pose.mulPose(Axis.XP.rotation(sunAngle));
        SkyHooks.drawAroundSun(pose, partial, dim);
    }
}
