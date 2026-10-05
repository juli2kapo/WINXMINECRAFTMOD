package net.juli2kapo.factoryascent.space.gravity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.juli2kapo.factoryascent.space.Orbit;
import net.juli2kapo.factoryascent.space.gravity.Gravity;
import net.juli2kapo.factoryascent.space.gravity.GravityFrame;
import net.juli2kapo.factoryascent.space.gravity.GravityHolder;
import net.juli2kapo.factoryascent.space.gravity.GravityPayloads;
import net.juli2kapo.factoryascent.space.station.MagneticBoots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;

/**
 * Turned gravity on the client: the moving player feels for walls and ceilings and turns (asking
 * the server), the turn is animated (camera and model swing over {@link #ANIM_TICKS} ticks while
 * the physics switches at once), and other players' gravity arrives from the server.
 */
@Mod(value = FactoryAscent.MOD_ID, dist = Dist.CLIENT)
public final class GravityClient {
    public static final int ANIM_TICKS = 8;

    /** A running turn animation: the world turn still to undo at the start, the eye jump to smooth, when it began. */
    private record Anim(Quaternionf undo, Vec3 offset, long start) {}

    private static final Map<Integer, Anim> ANIMS = new ConcurrentHashMap<>();
    private static int lostTicks;

    public GravityClient(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(GravityPayloads.Sync.TYPE, GravityClient::onSync));
        NeoForge.EVENT_BUS.addListener(GravityClient::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ANIMS.clear();
            lostTicks = 0;
        });
    }

    // ---------------------------------------------------------------- animation

    private static long now() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    /** 0 → 1 (eased) over the animation, or -1 when none runs. */
    private static float progress(Entity e, float partial) {
        Anim a = ANIMS.get(e.getId());
        if (a == null) return -1;
        float t = (now() - a.start + partial) / ANIM_TICKS;
        if (t >= 1 || t < -1) {
            ANIMS.remove(e.getId());
            return -1;
        }
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Whether the entity is drawn or viewed turned right now (turned gravity, or a turn still animating). */
    public static boolean active(Entity e) {
        return Gravity.isTurned(e) || ANIMS.containsKey(e.getId());
    }

    /** The frame's visual rotation (local → world), swinging from the old frame to the new one. */
    public static Quaternionf visual(Entity e, float partial) {
        Quaternionf base = GravityFrame.quaternion(Gravity.of(e));
        float s = progress(e, partial);
        if (s < 0) return base;
        Anim a = ANIMS.get(e.getId());
        if (a == null) return base;
        return new Quaternionf(a.undo).slerp(new Quaternionf(), s).mul(base);
    }

    /** What still has to be added to the eye position to smooth the jump of the eyes during a turn. */
    public static Vec3 offset(Entity e, float partial) {
        float s = progress(e, partial);
        Anim a = ANIMS.get(e.getId());
        return s < 0 || a == null ? Vec3.ZERO : a.offset.scale(1 - s);
    }

    /** Turns the pose (at the entity's feet) so the model stands on its turned floor, swinging about the eyes. */
    public static void applyModelTurn(Entity e, float partial, PoseStack pose) {
        float eye = e.getEyeHeight();
        Vec3 toEye = GravityFrame.toWorld(Gravity.of(e), 0, eye, 0).add(offset(e, partial));
        pose.translate(toEye.x, toEye.y, toEye.z);
        pose.mulPose(visual(e, partial));
        pose.translate(0, -eye, 0);
    }

    private static void animate(Entity e, Gravity.Change change, boolean smoothEyes) {
        Vec3 offset = smoothEyes ? change.oldEye().subtract(change.newEye()) : Vec3.ZERO;
        ANIMS.put(e.getId(), new Anim(new Quaternionf(change.turn()).conjugate(), offset, now()));
    }

    // ---------------------------------------------------------------- the moving player

    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player p = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (!p.level().isClientSide() || p != mc.player) return;
        Direction g = Gravity.of(p);
        boolean must = Gravity.mustReset(Gravity.enabled(), MagneticBoots.wearing(p), Orbit.gravityFor(p.level().dimension()),
                p.getAbilities().flying, p.isPassenger(), p.isSwimming(), p.isFallFlying(), p.isSleeping(), p.isInWater(),
                p.isSpectator(), !p.isAlive());
        if (must) {
            lostTicks = 0;
            if (g != Direction.DOWN) turn(p, Direction.DOWN, false);
            return;
        }
        if (g != Direction.DOWN) {
            lostTicks = Gravity.surfaceNear(p.level(), p, MagneticBoots.REACH) ? 0 : lostTicks + 1;
            if (Gravity.detach(g, p.isShiftKeyDown(), lostTicks, Gravity.LOST_TICKS)) {
                lostTicks = 0;
                turn(p, Direction.DOWN, false);
                return;
            }
        } else {
            lostTicks = 0;
        }
        if (p.isShiftKeyDown()) return;
        Set<Direction> walls = p.horizontalCollision ? Gravity.walls(p.level(), p) : EnumSet.noneOf(Direction.class);
        boolean bumped = p.verticalCollision && !p.verticalCollisionBelow && !p.onGround() && Gravity.headBump(p.level(), p);
        Direction to = Gravity.pick(g, walls, bumped, p.onGround(), bumped, wish(p, g), false);
        if (to != g) turn(p, to, true);
    }

    /** The world direction the movement keys push the player (zero with no keys). */
    private static Vec3 wish(Player p, Direction g) {
        double x = p.xxa, z = p.zza;
        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0E-4) return Vec3.ZERO;
        x /= len;
        z /= len;
        float sin = Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), cos = Mth.cos(p.getYRot() * Mth.DEG_TO_RAD);
        return GravityFrame.toWorld(g, x * cos - z * sin, 0, z * cos + x * sin);
    }

    private static void turn(Player p, Direction to, boolean onto) {
        Gravity.Change change = Gravity.change(p, to, onto);
        if (change == null) return;
        animate(p, change, true);
        ClientPacketDistributor.sendToServer(new GravityPayloads.Request(to, p.getX(), p.getY(), p.getZ()));
    }

    // ---------------------------------------------------------------- from the server

    private static void onSync(GravityPayloads.Sync msg, IPayloadContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(msg.entityId());
        if (!(e instanceof Player p)) return;
        Direction from = Gravity.of(p), to = msg.gravity();
        Vec3 pos = new Vec3(msg.x(), msg.y(), msg.z());
        if (p == mc.player) {
            // only corrections and let-gos from the server; our own turns we made already
            if (!msg.force()) return;
            if (from != to) animate(p, Gravity.apply(p, to, pos), true);
            else if (p.position().distanceToSqr(pos) > 0.25) p.setPos(pos);
            lostTicks = 0;
            return;
        }
        if (from == to) return;
        Quaternionf turn = GravityFrame.turn(from, to, GravityFrame.toWorld(from, GravityFrame.forward(p.getYRot())));
        ((GravityHolder) p).factoryascent$setGravityRaw(to);
        p.setPos(p.position());
        animate(p, new Gravity.Change(from, to, turn, Vec3.ZERO, Vec3.ZERO), false);
    }
}
