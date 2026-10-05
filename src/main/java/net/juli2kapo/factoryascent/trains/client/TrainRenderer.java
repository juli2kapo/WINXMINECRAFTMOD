package net.juli2kapo.factoryascent.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.juli2kapo.factoryascent.trains.DieselLocomotive;
import net.juli2kapo.factoryascent.trains.HopperWagon;
import net.juli2kapo.factoryascent.trains.Locomotive;
import net.juli2kapo.factoryascent.trains.RollingStock;
import net.juli2kapo.factoryascent.trains.SteamLocomotive;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws every locomotive and wagon from its layout ({@link TrainModels}): body and glass, bogies,
 * wheelsets turning with the distance rolled, the couplings that are in use, and per vehicle:
 * <ul>
 * <li>steam: coupling rods going round with the crank pins (the two sides a quarter turn apart),
 * connecting rods swinging between crank pin and crosshead, the crossheads and piston rods sliding
 * in and out of the cylinders, the lamp;</li>
 * <li>diesel: radiator fans spinning while the engine works, headlight and its beam;</li>
 * <li>hopper: the load heap rising with the fill and the bottom doors opening while it dumps.</li>
 * </ul>
 * The body follows the track: yaw from the rails' direction (smoothed), pitch on slopes.
 */
public class TrainRenderer extends EntityRenderer<RollingStock, TrainRenderer.State> {
    public static final class State extends EntityRenderState {
        String kind = "cargo_wagon";
        float yaw, pitch, wheel, hurt, hurtDir, damage, fan, fill;
        int links;
        boolean lights, dumping, working;
    }

    public TrainRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.9f;
        this.shadowStrength = 0.6f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(RollingStock entity) {
        return entity.getBoundingBox().inflate(entity.length() / 2 + 0.5, 1.2, entity.length() / 2 + 0.5);
    }

    @Override
    public void extractRenderState(RollingStock stock, State s, float partial) {
        super.extractRenderState(stock, s, partial);
        s.kind = BuiltInRegistries.ENTITY_TYPE.getKey(stock.getType()).getPath();
        s.yaw = stock.renderHeading(partial);
        s.pitch = stock.renderPitch(partial);
        s.wheel = Mth.lerp(partial, stock.wheelO, stock.wheel);
        s.hurt = stock.getHurtTime() - partial;
        s.hurtDir = stock.getHurtDir();
        s.damage = Math.max(stock.getDamage() - partial, 0);
        s.links = stock.linkBits();
        s.lights = stock instanceof Locomotive l && l.lightsOn();
        s.working = stock instanceof Locomotive l && (Math.abs(l.throttle()) > 0.01 || l.gaugeC() > 0);
        s.fan = (stock.tickCount + partial) * (s.working ? 31f : 0f);
        int fill = stock.fill();
        s.fill = (fill & 0xFFFF) / 1000f;
        s.dumping = (fill & (1 << 16)) != 0;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        TrainModels.Layout lay = TrainModels.layout(s.kind);
        if (lay == null) return;
        int light = s.lightCoords;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-s.yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-s.pitch));
        if (s.hurt > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(s.hurt) * s.hurt * s.damage / 20f * s.hurtDir));
        for (String part : lay.body()) TrainModels.draw(pose, c, part, light);
        for (TrainModels.Placed b : lay.bogies()) {
            pose.pushPose();
            pose.translate(0, b.y() / 16f, b.z() / 16f);
            TrainModels.draw(pose, c, b.part(), light);
            pose.popPose();
        }
        for (TrainModels.Placed w : lay.wheels()) {
            pose.pushPose();
            pose.translate(0, w.y() / 16f, w.z() / 16f);
            pose.mulPose(Axis.XP.rotation(s.wheel));
            TrainModels.draw(pose, c, w.part(), light);
            pose.popPose();
        }
        switch (s.kind) {
            case "steam_locomotive" -> steam(s, lay, pose, c, light);
            case "diesel_locomotive" -> diesel(s, pose, c, light);
            case "hopper_wagon" -> hopper(s, pose, c, light);
            default -> {}
        }
        // couplings in use: bit 0 = front, bit 1 = rear
        for (int end = 0; end < 2; end++) {
            if ((s.links & (1 << end)) == 0) continue;
            pose.pushPose();
            if (end == 1) pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(0, 0, (lay.half() - 1) / 16f);
            TrainModels.draw(pose, c, "coupler", light);
            pose.popPose();
        }
        for (String part : lay.glass()) TrainModels.draw(pose, c, part, light);
        pose.popPose();
        super.submit(s, pose, c, camera);
    }

    /** Valve gear: the crank pins go round with the wheels; rods follow (left side leads by a quarter turn). */
    private static void steam(State s, TrainModels.Layout lay, PoseStack pose, SubmitNodeCollector c, int light) {
        TrainModels.draw(pose, c, s.lights ? "steam_lamp_on" : "steam_lamp_off", s.lights ? 0xF000F0 : light);
        if (lay.wheels().isEmpty()) return;
        TrainModels.Placed front = lay.wheels().getLast();
        float axleY = front.y();
        float rc = TrainModels.crank, len = TrainModels.mainrod, cy = TrainModels.cylinderY;
        for (int side = 0; side < 2; side++) {
            float a = s.wheel + (side == 0 ? 0 : Mth.HALF_PI);
            float py = -rc * Mth.sin(a), pz = rc * Mth.cos(a);
            String suffix = side == 0 ? "_l" : "_r";
            pose.pushPose();
            pose.translate(0, (axleY + py) / 16f, pz / 16f);
            TrainModels.draw(pose, c, "steam_rod" + suffix, light);
            pose.popPose();
            float pinY = axleY + py, pinZ = front.z() + pz;
            float dy = cy - pinY;
            float cz = pinZ + Mth.sqrt(Math.max(0, len * len - dy * dy));
            float angle = (float) Mth.atan2(-dy, cz - pinZ);
            pose.pushPose();
            pose.translate(0, pinY / 16f, pinZ / 16f);
            pose.mulPose(Axis.XP.rotation(angle));
            pose.scale(1, 1, len / 16f);
            TrainModels.draw(pose, c, "steam_mainrod" + suffix, light);
            pose.popPose();
            pose.pushPose();
            pose.translate(0, cy / 16f, cz / 16f);
            TrainModels.draw(pose, c, "steam_piston" + suffix, light);
            pose.popPose();
        }
    }

    private static void diesel(State s, PoseStack pose, SubmitNodeCollector c, int light) {
        TrainModels.draw(pose, c, s.lights ? "diesel_lamp_on" : "diesel_lamp_off", s.lights ? 0xF000F0 : light);
        for (float[] f : TrainModels.FANS) {
            pose.pushPose();
            pose.translate(0, f[0] / 16f, f[1] / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(s.fan + f[1] * 7));
            TrainModels.draw(pose, c, "diesel_fan", light);
            pose.popPose();
        }
        if (s.lights) TrainModels.draw(pose, c, "diesel_beam", 0xF000F0);
    }

    private static void hopper(State s, PoseStack pose, SubmitNodeCollector c, int light) {
        TrainModels.draw(pose, c, s.dumping ? "hopper_doors_open" : "hopper_doors_closed", light);
        if (s.fill > 0.002f) {
            float y = TrainModels.hopperFloor + (TrainModels.hopperTop - TrainModels.hopperFloor) * Math.min(1, s.fill * 1.6f);
            pose.pushPose();
            pose.translate(0, y / 16f, 0);
            TrainModels.draw(pose, c, "hopper_load", light);
            pose.popPose();
        }
    }
}
