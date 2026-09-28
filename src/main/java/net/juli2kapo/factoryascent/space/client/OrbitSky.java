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
import java.util.Optional;
import java.util.OptionalDouble;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * The planet below the orbit dimension. The dimension type gives the black sky, the stars and the
 * sun (fixed environment attributes, no timeline); after the vanilla sky this draws the planet
 * as a big disc (textures/environment/earth.png) filling the lower sky, turning very slowly.
 */
final class OrbitSky {
    private static final Identifier EARTH = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "textures/environment/earth.png");
    /** Like the sun and moon's pipeline, but blended normally so the planet hides the stars behind it. */
    static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.MATRICES_PROJECTION)
            .withLocation(Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "pipeline/orbit_planet"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();
    /** Half-size of the planet quad at distance 100 (it fills most of the view straight down). */
    private static final float SIZE = 150f;

    private static @Nullable GpuBuffer quad;

    private OrbitSky() {}

    private static GpuBuffer quad() {
        if (quad == null) {
            try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
                BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
                b.addVertex(-1f, 0f, -1f).setUv(0f, 0f);
                b.addVertex(1f, 0f, -1f).setUv(1f, 0f);
                b.addVertex(1f, 0f, 1f).setUv(1f, 1f);
                b.addVertex(-1f, 0f, 1f).setUv(0f, 1f);
                try (MeshData mesh = b.buildOrThrow()) {
                    quad = RenderSystem.getDevice().createBuffer(() -> "Orbit planet quad", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                }
            }
        }
        return quad;
    }

    static void afterSky(RenderLevelStageEvent.AfterSky event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != SpaceRules.ORBIT) return;
        AbstractTexture texture = mc.getTextureManager().getTexture(EARTH);
        float spin = (mc.level.getGameTime() % 72000L) * 0.005f;
        Matrix4f pose = new Matrix4f(event.getModelViewMatrix())
                .rotateX((float) Math.PI)          // straight down, like the moon at its lowest
                .rotateY((float) Math.toRadians(spin))
                .translate(0f, 100f, 0f)
                .scale(SIZE, 1f, SIZE);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(pose, new Vector4f(1f, 1f, 1f, 1f));
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(6);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Orbit planet",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            pass.setVertexBuffer(0, quad().slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(6, 1, 0, 0, 0);
        }
    }
}
