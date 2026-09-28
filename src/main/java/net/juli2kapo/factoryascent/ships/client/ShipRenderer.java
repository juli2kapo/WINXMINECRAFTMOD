package net.juli2kapo.factoryascent.ships.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.juli2kapo.factoryascent.ships.AbstractShip;
import net.juli2kapo.factoryascent.ships.BronzeCog;
import net.juli2kapo.factoryascent.ships.MotorShip;
import net.juli2kapo.factoryascent.ships.SeaShip;
import net.juli2kapo.factoryascent.ships.ShipMath;
import net.juli2kapo.factoryascent.ships.Shuttle;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws the three ships from their part models ({@link ShipModels}), turning the animated parts
 * about the pivots declared in tools/features/ships.py (PIVOTS):
 * <ul>
 * <li>sea ships bob, pitch and roll on the waves (more in rain and storms) and heel into turns;
 * the rudder follows the helm;</li>
 * <li>cog: the yard and sail brace round to the wind and fill (or luff) about the mast, the
 * pennant streams downwind and flutters, the stern lantern lights with J;</li>
 * <li>motor ship: the propeller spins with the throttle, the radar turns, the searchlight and its
 * beam light with J;</li>
 * <li>shuttle: main-engine and VTOL flames scale with thrust and flicker, the landing legs stow in
 * flight, the tail strobe blinks, landing lights with J, and re-entry shakes it.</li>
 * </ul>
 */
public class ShipRenderer extends EntityRenderer<AbstractShip, ShipRenderer.State> {
    // Pivots in ship pixels (mirrors PIVOTS in tools/features/ships.py).
    private static final float[] COG_MAST = {0, 0, 4}, COG_RUDDER = {0, 0, -44}, COG_FLAG = {0, 100, 4};
    private static final float[] MOTOR_PROP = {0, -1.5f, -61}, MOTOR_RUDDER = {0, 0, -62}, MOTOR_RADAR = {0, 66, -26};
    private static final float[] SH_MAIN = {0, 16, -44}, SH_VTOL = {0, 2.5f, 0};

    public enum Kind { COG, MOTOR, SHUTTLE }

    public static final class State extends EntityRenderState {
        Kind kind = Kind.COG;
        float yaw, bob, pitch, roll, hurt, hurtDir, damage;
        boolean lights, afloat, sailing;
        float yard, flag, rudder, spin, radar, thrust, lift, legs, shakeX, shakeZ;
        int strobe;
    }

    public ShipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.6f;
        this.shadowStrength = 0.6f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(AbstractShip entity) {
        return entity.getBoundingBox().inflate(4.5, 7, 4.5);
    }

    @Override
    public void extractRenderState(AbstractShip ship, State s, float partial) {
        super.extractRenderState(ship, s, partial);
        s.kind = ship instanceof Shuttle ? Kind.SHUTTLE : ship instanceof MotorShip ? Kind.MOTOR : Kind.COG;
        s.yaw = ship.getYRot(partial);
        s.hurt = ship.getHurtTime() - partial;
        s.hurtDir = ship.getHurtDir();
        s.damage = Math.max(ship.getDamage() - partial, 0);
        s.lights = ship.lightsOn();
        float t = ship.tickCount + partial + ship.getId() * 37;
        s.bob = s.pitch = s.roll = 0;
        s.shakeX = s.shakeZ = 0;
        int in = ship.syncedInput();
        if (ship instanceof SeaShip sea) {
            s.afloat = sea.afloat();
            if (s.afloat) {
                float rain = ship.level().getRainLevel(partial), thunder = ship.level().getThunderLevel(partial);
                float sea_ = 1f + rain * 0.8f + thunder * 1.2f;
                float big = ship instanceof MotorShip ? 0.6f : 1f; // the bigger hull rides the swell better
                s.bob = (Mth.sin(t * 0.075f) * 1.1f + Mth.sin(t * 0.19f) * 0.35f) * sea_ * big / 16f;
                s.pitch = (Mth.sin(t * 0.061f + 1.1f) * 1.4f) * sea_ * big;
                s.roll = (Mth.sin(t * 0.049f + 0.4f) * 2.0f + Mth.sin(t * 0.13f) * 0.5f) * sea_ * big;
                // heel into turns, squat a little under power
                s.roll += Mth.clamp(ship.turnThisTick(), -3, 3) * (ship instanceof BronzeCog ? 2.2f : 1.4f);
                s.pitch -= (float) Mth.clamp(ship.horizontalSpeed() * 3, 0, 1.2);
            }
            s.rudder = Mth.clamp(-ship.turnThisTick() * 9f, -30, 30);
        }
        switch (s.kind) {
            case COG -> {
                s.sailing = (in & AbstractShip.IN_FORWARD) != 0;
                s.yard = ship.aux();
                float wind = ShipMath.windYaw(ship.level().getGameTime());
                s.flag = Mth.wrapDegrees(wind - s.yaw) + Mth.sin(t * 0.6f) * 6f + Mth.sin(t * 1.7f) * 3f;
            }
            case MOTOR -> {
                s.spin = Mth.lerp(partial, ship.spinO, ship.spin);
                s.radar = t * 5f;
            }
            case SHUTTLE -> {
                float flicker = 0.9f + 0.1f * Mth.sin(t * 2.3f) * Mth.cos(t * 3.1f);
                s.thrust = Mth.lerp(partial, ship.thrustO, ship.thrust) * flicker;
                s.lift = Mth.lerp(partial, ship.liftO, ship.lift) * flicker;
                s.legs = Mth.lerp(partial, ship.legsO, ship.legs);
                s.strobe = (int) t % 24;
                if (ship.state() == Shuttle.STATE_REENTRY) {
                    s.shakeX = Mth.sin(t * 7.3f) * 0.06f;
                    s.shakeZ = Mth.cos(t * 9.1f) * 0.06f;
                    s.lightCoords = 0xF000F0;
                } else if (s.thrust > 0.05f || s.lift > 0.05f) {
                    s.shakeX = Mth.sin(t * 5.3f) * 0.008f * (s.thrust + s.lift);
                }
            }
        }
    }

    private static void about(PoseStack pose, float[] p, Runnable turn) {
        pose.translate(p[0] / 16f, p[1] / 16f, p[2] / 16f);
        turn.run();
        pose.translate(-p[0] / 16f, -p[1] / 16f, -p[2] / 16f);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        int light = s.lightCoords;
        pose.pushPose();
        pose.translate(s.shakeX, s.bob, s.shakeZ);
        pose.mulPose(Axis.YP.rotationDegrees(-s.yaw));
        if (s.hurt > 0) pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(s.hurt) * s.hurt * s.damage / 20f * s.hurtDir));
        pose.mulPose(Axis.XP.rotationDegrees(s.pitch));
        pose.mulPose(Axis.ZP.rotationDegrees(s.roll));
        switch (s.kind) {
            case COG -> cog(s, pose, c, light);
            case MOTOR -> motor(s, pose, c, light);
            case SHUTTLE -> shuttle(s, pose, c, light);
        }
        pose.popPose();
        super.submit(s, pose, c, camera);
    }

    private static void cog(State s, PoseStack pose, SubmitNodeCollector c, int light) {
        ShipModels.draw(pose, c, "cog_hull", light);
        ShipModels.draw(pose, c, s.lights ? "cog_lantern_on" : "cog_lantern_off", light);
        pose.pushPose();
        about(pose, COG_MAST, () -> {
            pose.mulPose(Axis.YP.rotationDegrees(-s.yard));
            // a full sail bellies forward; a luffing one hangs slack and shivers
            float fill = s.sailing ? 1f : 0.45f + 0.08f * Mth.sin(s.ageInTicks * 0.9f);
            pose.scale(1f, 1f, fill);
        });
        ShipModels.draw(pose, c, "cog_sail", light);
        pose.popPose();
        pose.pushPose();
        about(pose, COG_FLAG, () -> pose.mulPose(Axis.YP.rotationDegrees(-(s.flag - 180f))));
        ShipModels.draw(pose, c, "cog_flag", light);
        pose.popPose();
        pose.pushPose();
        about(pose, COG_RUDDER, () -> pose.mulPose(Axis.YP.rotationDegrees(s.rudder)));
        ShipModels.draw(pose, c, "cog_rudder", light);
        pose.popPose();
    }

    private static void motor(State s, PoseStack pose, SubmitNodeCollector c, int light) {
        ShipModels.draw(pose, c, "motor_hull", light);
        ShipModels.draw(pose, c, s.lights ? "motor_lamp_on" : "motor_lamp_off", light);
        pose.pushPose();
        about(pose, MOTOR_PROP, () -> pose.mulPose(Axis.ZP.rotationDegrees(s.spin)));
        ShipModels.draw(pose, c, "motor_propeller", light);
        pose.popPose();
        pose.pushPose();
        about(pose, MOTOR_RUDDER, () -> pose.mulPose(Axis.YP.rotationDegrees(s.rudder)));
        ShipModels.draw(pose, c, "motor_rudder", light);
        pose.popPose();
        pose.pushPose();
        about(pose, MOTOR_RADAR, () -> pose.mulPose(Axis.YP.rotationDegrees(s.radar)));
        ShipModels.draw(pose, c, "motor_radar", light);
        pose.popPose();
        ShipModels.draw(pose, c, "motor_glass", light);
        if (s.lights) ShipModels.draw(pose, c, "motor_beam", 0xF000F0);
    }

    private static void shuttle(State s, PoseStack pose, SubmitNodeCollector c, int light) {
        ShipModels.draw(pose, c, "shuttle_body", light);
        ShipModels.draw(pose, c, "shuttle_frame", light);
        ShipModels.draw(pose, c, s.lights ? "shuttle_landing_on" : "shuttle_landing_off", light);
        if (s.strobe < 2 || (s.strobe >= 5 && s.strobe < 7)) ShipModels.draw(pose, c, "shuttle_strobe", 0xF000F0);
        pose.pushPose();
        pose.translate(0, s.legs * 5.5f / 16f, 0);
        pose.scale(1f, 1f - s.legs * 0.35f, 1f);
        ShipModels.draw(pose, c, "shuttle_legs", light);
        pose.popPose();
        if (s.thrust > 0.03f) {
            pose.pushPose();
            about(pose, SH_MAIN, () -> pose.scale(0.8f + 0.2f * s.thrust, 0.8f + 0.2f * s.thrust, Math.max(0.05f, s.thrust)));
            ShipModels.draw(pose, c, "shuttle_flame_main", 0xF000F0);
            pose.popPose();
        }
        if (s.lift > 0.03f) {
            pose.pushPose();
            about(pose, SH_VTOL, () -> pose.scale(1f, Math.max(0.05f, s.lift), 1f));
            ShipModels.draw(pose, c, "shuttle_flame_vtol", 0xF000F0);
            pose.popPose();
        }
        ShipModels.draw(pose, c, "shuttle_canopy", light);
    }
}
