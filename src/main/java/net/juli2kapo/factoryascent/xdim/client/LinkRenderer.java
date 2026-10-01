package net.juli2kapo.factoryascent.xdim.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.factoryascent.xdim.LinkBlock;
import net.juli2kapo.factoryascent.xdim.LinkBlockEntity;
import net.juli2kapo.factoryascent.xdim.LinkTier;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The endpoint's core: a glowing cube tumbling in the middle of the frame (violet for an Ender
 * Link, cyan for a Quantum Entangler, two counter-rotating shells for the latter), brighter and
 * faster while it transfers, with a beam of light rising from the top.
 */
final class LinkRenderer implements BlockEntityRenderer<LinkBlockEntity, LinkRenderer.State> {
    static final class State extends BlockEntityRenderState {
        boolean quantum;
        boolean active;
        float time;
    }

    LinkRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LinkBlockEntity link, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(link, state, partialTicks, camera, breakProgress);
        state.quantum = link.tier() == LinkTier.QUANTUM;
        state.active = link.getBlockState().hasProperty(LinkBlock.ACTIVE) && link.getBlockState().getValue(LinkBlock.ACTIVE);
        state.time = link.getLevel() == null ? 0 : (link.getLevel().getGameTime() % 24000L) + partialTicks;
    }

    private static int argb(int a, int rgb) {
        return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF);
    }

    private static void quad(VertexConsumer b, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, int c) {
        b.addVertex(m, p0.x, p0.y, p0.z).setColor(c);
        b.addVertex(m, p1.x, p1.y, p1.z).setColor(c);
        b.addVertex(m, p2.x, p2.y, p2.z).setColor(c);
        b.addVertex(m, p3.x, p3.y, p3.z).setColor(c);
        b.addVertex(m, p3.x, p3.y, p3.z).setColor(c);
        b.addVertex(m, p2.x, p2.y, p2.z).setColor(c);
        b.addVertex(m, p1.x, p1.y, p1.z).setColor(c);
        b.addVertex(m, p0.x, p0.y, p0.z).setColor(c);
    }

    /** A cube of half-size h around the block centre, turned by yaw (around Y) and tilt (around X then Z). */
    private static void cube(VertexConsumer b, Matrix4f m, float h, float yaw, float tilt, int color) {
        Vector3f[] v = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            Vector3f p = new Vector3f((i & 1) == 0 ? -h : h, (i & 2) == 0 ? -h : h, (i & 4) == 0 ? -h : h);
            p.rotateX(tilt).rotateZ(tilt * 0.7f).rotateY(yaw);
            v[i] = p.add(0.5f, 0.5f, 0.5f);
        }
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] f : faces) quad(b, m, v[f[0]], v[f[1]], v[f[2]], v[f[3]], color);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float t = state.time;
        float speed = state.active ? 0.09f : 0.025f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * (state.active ? 0.35f : 0.08f));
        int core = state.quantum ? 0x50E8FF : 0xB060FF;
        int halo = state.quantum ? 0x2080FF : 0x6020C0;
        float bob = (float) Math.sin(t * 0.05f) * 0.03f;
        pose.pushPose();
        pose.translate(0, bob, 0);
        collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, b) -> {
            Matrix4f m = p.pose();
            float inner = state.quantum ? 0.11f : 0.12f;
            cube(b, m, inner, t * speed, t * speed * 0.6f, argb(state.active ? 230 : 150, core));
            cube(b, m, inner + 0.05f + pulse * 0.03f, -t * speed * 0.8f, t * speed * 0.4f + 0.6f, argb(state.active ? 110 : 50, halo));
            if (state.quantum) {
                cube(b, m, 0.24f + pulse * 0.02f, t * speed * 0.5f + 0.4f, -t * speed * 0.3f, argb(state.active ? 60 : 22, 0xA0F0FF));
            }
        });
        pose.popPose();
        if (state.active) {
            int beamColor = state.quantum ? 0xFF80F0FF : 0xFFC080FF;
            int height = state.quantum ? 24 : 6;
            BeaconRenderer.submitBeaconBeam(pose, collector, BeaconRenderer.BEAM_LOCATION, 1f, t, 1, height, beamColor, 0.06f, 0.12f);
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
