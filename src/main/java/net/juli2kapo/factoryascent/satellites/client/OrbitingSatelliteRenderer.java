package net.juli2kapo.factoryascent.satellites.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.juli2kapo.factoryascent.orbital.SatelliteSky;
import net.juli2kapo.factoryascent.orbital.SatelliteType;
import net.juli2kapo.factoryascent.satellites.OrbitingSatellite;
import net.juli2kapo.factoryascent.space.client.SatelliteSkyClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a satellite flying in Earth orbit with the same 3D model as in the sky
 * ({@link SatelliteSkyClient}), {@link #SCALE} times its item size (about seven blocks across the
 * solar wings), sunlit, facing along its track and rocking a little. Far ones are drawn nearer and
 * smaller (same size on screen) so the black fog of orbit never swallows them. Its name and
 * distance show within {@link #LABEL_RANGE} blocks (or through a spyglass).
 */
public class OrbitingSatelliteRenderer extends EntityRenderer<OrbitingSatellite, OrbitingSatelliteRenderer.State> {
    /** How many blocks one item-model block becomes. */
    public static final float SCALE = 3f;
    private static final double DRAW_DISTANCE = 64, LABEL_RANGE = 160;

    public static final class State extends EntityRenderState {
        SatelliteType type = SatelliteType.SURVEY;
        float yaw, wobble, roll;
        @Nullable Component label;
    }

    public OrbitingSatelliteRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(OrbitingSatellite entity) {
        return entity.getBoundingBox().inflate(3);
    }

    @Override
    public boolean shouldRender(OrbitingSatellite entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z); // drawn pulled in when far: the frustum test of the true box would be wrong
    }

    @Override
    public void extractRenderState(OrbitingSatellite e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.type = e.satelliteType();
        s.yaw = e.getYRot(partial);
        float t = e.tickCount + partial + (e.satelliteId().hashCode() & 0xFF);
        s.wobble = Mth.sin(t * 0.021f) * 6f;
        s.roll = Mth.sin(t * 0.013f + 1f) * 8f;
        s.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        s.label = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double d = Math.sqrt(s.distanceToCameraSq);
            if (d < LABEL_RANGE || mc.player.isScoping()) {
                SatelliteSky.Entry entry = SatelliteSkyClient.entry(e.satelliteId());
                Component name = entry != null ? SatelliteSkyClient.label(entry)
                        : Component.translatable("sky.factoryascent.unidentified", s.type.shortName()).withStyle(ChatFormatting.RED);
                s.label = name.copy().append(Component.literal("  " + Math.round(d) + " m").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        var part = Minecraft.getInstance().getModelManager().getStandaloneModel(SatelliteSkyClient.model(s.type));
        Vec3 off = new Vec3(s.x - camera.pos.x, s.y + 1.5 - camera.pos.y, s.z - camera.pos.z);
        double dist = off.length();
        double k = dist > DRAW_DISTANCE ? DRAW_DISTANCE / dist : 1.0;
        pose.pushPose();
        if (k < 1) pose.translate(-off.x * (1 - k), -off.y * (1 - k), -off.z * (1 - k));
        pose.translate(0, 1.5, 0);
        if (s.label != null) {
            c.submitNameTag(pose, new Vec3(0, 2.6 * k, 0), 0, s.label, true, LightCoordsUtil.FULL_BRIGHT, camera);
        }
        if (part != null) {
            float sc = (float) (SCALE * k);
            pose.mulPose(Axis.YP.rotationDegrees(-s.yaw + 90f)); // the wings lie along the model's x: span across the track
            pose.mulPose(Axis.XP.rotationDegrees(15f + s.wobble));
            pose.mulPose(Axis.ZP.rotationDegrees(s.roll));
            pose.scale(sc, sc, sc);
            pose.translate(-0.5f, -0.5f, -0.5f);
            c.submitBlockModel(pose, Sheets.translucentBlockItemSheet(), List.of(part), new int[0], LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 0);
        }
        pose.popPose();
        super.submit(s, pose, c, camera);
    }
}
