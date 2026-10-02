package io.github.mishkis.orbital_railgun.client.rendering;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.mishkis.orbital_railgun.client.mixin.PostChainAccessor;
import io.github.mishkis.orbital_railgun.client.mixin.PostPassAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Re-implementation of the Satin-based shader handling on top of the vanilla
 * post-effect pipeline (1.21.6+ format). Custom uniforms live in the std140
 * "RailgunConfig" uniform block declared in the post_effect JSON; its GPU
 * buffer is rebuilt each frame before the chain is processed.
 */
public abstract class AbstractOrbitalRailgunShader {
    private static final Set<Identifier> EXTERNAL_TARGETS = Set.of(PostChain.MAIN_TARGET_ID);
    private static final String UNIFORM_BLOCK = "RailgunConfig";
    // Field order matters: Std140Builder always pads a vec3 out to 16 bytes,
    // while GLSL packs a following scalar into the vec3's tail. Scalars
    // therefore go before the vectors so both layouts agree.
    private static final int UNIFORM_BLOCK_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putFloat()
            .putFloat()
            .putFloat()
            .putVec3()
            .putVec3()
            .get();
    private static final Vector3f ZERO = new Vector3f();

    // Lazily initialized: mods are constructed before the Minecraft instance exists.
    protected Minecraft client;

    protected int ticks = 0;

    protected abstract Identifier getIdentifier();

    protected abstract boolean shouldRender();

    /** Hook matching the subclass tick overrides of the original mod. */
    protected void tickExtra() {}

    /** Compute per-frame values (e.g. raycasts) before the chain runs. */
    protected void prepareExtraUniforms(float partialTick) {}

    /** The BlockPosition value pushed into the uniform block. */
    protected Vector3f getBlockPositionUniform() {
        return ZERO;
    }

    /** One drawing of the effect: where it is and how far along (in ticks) it is. */
    protected record Instance(Vector3f blockPosition, int ticks) {}

    /**
     * The drawings to make this frame, in order (each one is drawn over the previous). By default a
     * single one with this shader's own position and timer while {@link #shouldRender()} holds;
     * the strike shader overrides it to stack several strikes at once.
     */
    protected List<Instance> instances() {
        return shouldRender() ? List.of(new Instance(getBlockPositionUniform(), ticks)) : List.of();
    }

    /** The IsBlockHit value pushed into the uniform block. */
    protected float getIsBlockHitUniform() {
        return 0f;
    }

    /**
     * Builds the matrix that takes (screen uv, raw depth sample) to camera-relative world space.
     * Since 26.2 the level uses a reversed-Z projection (near maps to depth 1, far to 0) and, when
     * GL_ARB_clip_control is present, clip-space depth in [0, 1] instead of [-1, 1]. The original
     * shaders assumed ndc = depth * 2 - 1 with near = 0, which put every reconstructed position
     * behind the camera: no rings, no beams, and a ray-march that never hit anything.
     */
    private static Matrix4f computeInverseTransformMatrix(Matrix4fc modelViewMatrix) {
        boolean zZeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        Matrix4f uvDepthToNdc = new Matrix4f()
                .translation(-1f, -1f, zZeroToOne ? 0f : -1f)
                .scale(2f, 2f, zZeroToOne ? 1f : 2f);
        return new Matrix4f(OrbitalRailgunMatrices.PROJECTION).mul(modelViewMatrix).invert().mul(uvDepthToNdc);
    }

    /** Raw depth-buffer value of the near plane: 1 with the reversed-Z projection, 0 with a classic one. */
    private static float computeNearDepth() {
        Matrix4f projection = OrbitalRailgunMatrices.PROJECTION;
        Vector4f close = projection.transform(new Vector4f(0f, 0f, -1f, 1f));
        Vector4f far = projection.transform(new Vector4f(0f, 0f, -100f, 1f));
        return close.z / close.w > far.z / far.w ? 1f : 0f;
    }

    public final void onClientTick(ClientTickEvent.Post event) {
        if (client == null) {
            client = Minecraft.getInstance();
        }

        tickExtra();

        if (shouldRender()) {
            ticks++;
        } else {
            ticks = 0;
        }
    }

    public final void onRenderLevelStage(RenderLevelStageEvent.AfterLevel event) {
        if (client == null) {
            client = Minecraft.getInstance();
        }

        List<Instance> instances = instances();
        if (instances.isEmpty()) {
            return;
        }

        PostChain chain = client.getShaderManager().getPostChain(getIdentifier(), EXTERNAL_TARGETS);
        if (chain == null) {
            return;
        }

        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        prepareExtraUniforms(partialTick);

        Matrix4f inverseTransformMatrix = computeInverseTransformMatrix(event.getModelViewMatrix());
        float nearDepth = computeNearDepth();
        Vector3f cameraPosition = event.getLevelRenderState().cameraRenderState.pos.toVector3f();
        float isBlockHit = getIsBlockHitUniform();

        for (Instance instance : instances) {
        Vector3f blockPosition = instance.blockPosition();
        float time = (instance.ticks() + partialTick) / 20f;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (PostPass pass : ((PostChainAccessor) chain).orbital_railgun$getPasses()) {
            Map<String, GpuBuffer> customUniforms = ((PostPassAccessor) pass).orbital_railgun$getCustomUniforms();
            GpuBuffer current = customUniforms.get(UNIFORM_BLOCK);
            if (current == null) {
                // e.g. the final blit pass, which has no RailgunConfig block
                continue;
            }

            try (MemoryStack memoryStack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(memoryStack, UNIFORM_BLOCK_SIZE);
                builder.putMat4f(inverseTransformMatrix);
                builder.putFloat(time);
                builder.putFloat(isBlockHit);
                builder.putFloat(nearDepth);
                builder.putVec3(cameraPosition);
                builder.putVec3(blockPosition);
                ByteBuffer data = builder.get();

                if ((current.usage() & GpuBuffer.USAGE_COPY_DST) != 0 && current.size() >= data.remaining()) {
                    // Our own buffer from a previous frame: update it in place.
                    encoder.writeToBuffer(current.slice(0, data.remaining()), data);
                } else {
                    // The immutable buffer PostPass built from the JSON defaults: swap in a writable one once.
                    customUniforms.put(UNIFORM_BLOCK, RenderSystem.getDevice().createBuffer(() -> "orbital_railgun " + UNIFORM_BLOCK,
                            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data));
                    current.close();
                }
            }
        }

        chain.process(client.gameRenderer.mainRenderTarget(), GraphicsResourceAllocator.UNPOOLED);
        }
    }
}
