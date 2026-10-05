package net.juli2kapo.factoryascent.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.orbital.SatelliteSky;
import net.juli2kapo.factoryascent.orbital.SatelliteTrack;
import net.juli2kapo.factoryascent.orbital.SatelliteType;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.juli2kapo.factoryascent.space.planet.Planet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draws the satellites the server says are over this sky ({@link SatelliteSky}), each on its own
 * pass overhead ({@link SatelliteTrack}):
 *
 * <ul>
 *   <li>In Earth orbit and on the planets: the real 3D satellite (the deployed item models of the
 *       survey, uplink and guardian satellites), sunlit, tens to hundreds of blocks away. It is only
 *       drawn, never an entity, so it can't be reached. Far ones are drawn nearer and smaller
 *       (same size on screen) so fog and the far plane never hide them.</li>
 *   <li>Looked at through a spyglass: a name tag, green for your team's, gold for other teams'
 *       satellites your radars have locked, red "unidentified" for the rest.</li>
 *   <li>In the Overworld at night: a small point of light crossing the stars.</li>
 * </ul>
 */
public final class SatelliteSkyClient {
    public static final StandaloneModelKey<BlockStateModelPart> SURVEY = key("survey_satellite_3d");
    public static final StandaloneModelKey<BlockStateModelPart> UPLINK = key("uplink_satellite_3d");
    public static final StandaloneModelKey<BlockStateModelPart> GUARDIAN = key("guardian_satellite_3d");
    private static final Identifier DOT = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "textures/environment/satellite_dot.png");
    /** How big a satellite looks: as if it were this many times its item model, at its true distance. */
    private static final float SCALE = 9f;
    /** Nearest a satellite is drawn (farther ones are pulled in and shrunk to match). */
    private static final double DRAW_DISTANCE = 64;

    private static Identifier sky = Identifier.withDefaultNamespace("overworld");
    private static List<SatelliteSky.Entry> satellites = List.of();
    /** Height of the satellites' real orbits in Earth orbit, or 0 when they don't fly there (config off). */
    private static int orbitAltitude;

    private SatelliteSkyClient() {}

    private static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> FactoryAscent.MOD_ID + ":" + name);
    }

    static Identifier modelId(StandaloneModelKey<BlockStateModelPart> key) {
        String name = key == SURVEY ? "survey_satellite_3d" : key == UPLINK ? "uplink_satellite_3d" : "guardian_satellite_3d";
        return Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "item/" + name);
    }

    static void receive(SatelliteSky.Payload payload) {
        sky = payload.sky();
        satellites = List.copyOf(payload.satellites());
        orbitAltitude = payload.altitude();
    }

    /** Height of the real orbits in Earth orbit, 0 if the satellites only appear in the sky there. */
    public static int orbitAltitude() {
        return orbitAltitude;
    }

    /** Where a satellite really flies in Earth orbit at {@code time} (only meaningful with {@link #orbitAltitude} &gt; 0). */
    public static Vec3 orbitPosition(SatelliteSky.Entry e, double time) {
        return net.juli2kapo.factoryascent.satellites.SatelliteOrbit.position(e.id(), e.launchTime(), e.centerX() + 0.5,
                e.centerZ() + 0.5, orbitAltitude, time);
    }

    /** The known entry of a satellite, if the sky list has it. */
    public static SatelliteSky.@org.jspecify.annotations.Nullable Entry entry(java.util.UUID id) {
        for (SatelliteSky.Entry e : current()) {
            if (e.id().equals(id)) return e;
        }
        return null;
    }

    /** The satellites known for the sky the player is under now (empty if the list is for another sky). */
    public static List<SatelliteSky.Entry> current() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().identifier().equals(sky)) return List.of();
        return satellites;
    }

    /** Heights of the passes: low over orbit (you're up there with them), higher over planets, highest seen from the ground. */
    private static double[] heights(net.minecraft.resources.ResourceKey<Level> dim) {
        if (dim == SpaceRules.ORBIT) return new double[] {40, 60};
        if (Planet.of(dim) != null) return new double[] {90, 80};
        return new double[] {170, 90};
    }

    public static StandaloneModelKey<BlockStateModelPart> model(SatelliteType type) {
        return switch (type) {
            case SURVEY -> SURVEY;
            case UPLINK -> UPLINK;
            case DEFENSE -> GUARDIAN;
        };
    }

    /** 3D satellites in orbit and over the planets. */
    static void submit(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        var dim = level.dimension();
        if (dim != SpaceRules.ORBIT && Planet.of(dim) == null) return;
        List<SatelliteSky.Entry> list = current();
        if (list.isEmpty()) return;
        double time = level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double[] h = heights(dim);
        PoseStack pose = event.getPoseStack();
        var collector = event.getSubmitNodeCollector();
        Vec3 look = mc.player.getViewVector(1f);
        boolean scoping = mc.player.isScoping();
        // Earth orbit with flying satellites: each one where it really is (its body draws it when near)
        boolean real = dim == SpaceRules.ORBIT && orbitAltitude > 0;
        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        for (SatelliteSky.Entry e : list) {
            double[] off;
            if (real) {
                if (net.juli2kapo.factoryascent.satellites.SatelliteBodies.presentOnClient(e.id(), level.getGameTime())) continue;
                Vec3 at = orbitPosition(e, time).subtract(cam);
                off = new double[] {at.x, at.y, at.z};
            } else {
                off = SatelliteTrack.offset(e.id(), time, h[0], h[1]);
            }
            double dist = Math.sqrt(off[0] * off[0] + off[1] * off[1] + off[2] * off[2]);
            if (dist < 1) continue;
            double k = Math.min(1.0, DRAW_DISTANCE / dist);
            BlockStateModelPart part = mc.getModelManager().getStandaloneModel(model(e.satelliteType()));
            pose.pushPose();
            pose.translate(off[0] * k, off[1] * k, off[2] * k);
            if (part != null) {
                pose.pushPose();
                float s = (float) ((real ? net.juli2kapo.factoryascent.satellites.client.OrbitingSatelliteRenderer.SCALE : SCALE) * k);
                pose.mulPose(Axis.YP.rotation((float) SatelliteTrack.spin(e.id(), time)));
                pose.mulPose(Axis.XP.rotationDegrees(15f));
                pose.scale(s, s, s);
                pose.translate(-0.5f, -0.5f, -0.5f);
                collector.submitBlockModel(pose, Sheets.translucentBlockItemSheet(), List.of(part), new int[0],
                        LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
            if (scoping) {
                Vec3 to = new Vec3(off[0], off[1], off[2]).normalize();
                if (to.dot(look) > Math.cos(Math.toRadians(6))) {
                    collector.submitNameTag(pose, new Vec3(0, SCALE * k * 0.9, 0), 0, label(e), true,
                            LightCoordsUtil.FULL_BRIGHT, event.getLevelRenderState().cameraRenderState);
                }
            }
            pose.popPose();
        }
    }

    public static Component label(SatelliteSky.Entry e) {
        if (!e.identified()) {
            return Component.translatable("sky.factoryascent.unidentified", e.satelliteType().shortName()).withStyle(ChatFormatting.RED);
        }
        return Component.translatable("sky.factoryascent.satellite", e.name(), e.owner())
                .withStyle(e.own() ? ChatFormatting.GREEN : ChatFormatting.GOLD);
    }

    /** Points of light crossing the Overworld's night sky (called from {@link SpaceSkies#afterSky}). */
    static void afterSky(Matrix4f mv, ClientLevel level, float partial) {
        if (level.dimension() != Level.OVERWORLD) return;
        List<SatelliteSky.Entry> list = current();
        if (list.isEmpty()) return;
        float stars = SpaceSkies.starBrightness(partial) * (1f - level.getRainLevel(partial));
        if (stars <= 0.02f) return;
        double time = level.getGameTime() + partial;
        double[] h = heights(level.dimension());
        for (SatelliteSky.Entry e : list) {
            double[] off = SatelliteTrack.offset(e.id(), time, h[0], h[1]);
            Vector3f dir = new Vector3f((float) off[0], (float) off[1], (float) off[2]).normalize();
            if (dir.y < 0.05f) continue;
            float fade = Math.min(1f, (dir.y - 0.05f) * 6f) * Math.min(1f, stars * 2.5f);
            float r = e.own() ? 0.85f : 1f, g = 1f, b = e.own() ? 0.9f : 0.95f;
            SpaceSkies.disc(mv, dir, 0.7f, 0f, DOT, new Vector4f(r, g, b, fade));
        }
    }
}
