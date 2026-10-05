package net.juli2kapo.factoryascent.space.gravity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.juli2kapo.factoryascent.space.Orbit;
import net.juli2kapo.factoryascent.space.SpaceConfig;
import net.juli2kapo.factoryascent.space.station.MagneticBoots;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Per-player gravity direction: what Magnetic Boots really do in low gravity (config
 * {@code magneticBootsRotateGravity}). Walk into a wall and your "down" becomes that wall; walk up
 * it into the ceiling and the ceiling becomes your floor; walk down a wall onto the floor and you
 * are back to normal. Sneak, take the boots off, leave low gravity, fly, ride, swim, glide, sleep,
 * or drift off the surface for a moment, and gravity is plain DOWN again.
 *
 * <p>Design (after the ideas of the open-source "Gravity Changer" mod, our own code): the player
 * keeps living by the vanilla rules in a turned <i>local frame</i> ({@link GravityFrame}). The
 * mixins turn the box ({@code makeBoundingBox}), collisions, step-up and on-ground test
 * ({@code move}), movement and jumping (velocity is turned into the local frame for
 * {@code travel}), the eyes and the look vector (block interaction, projectiles), the camera and the
 * model. The client that moves the player decides when its gravity changes (it feels the surfaces
 * first, with no lag) and asks the server, which checks the request (boots, low gravity, room for
 * the turned body) and tells the other players; the server also forces DOWN when a hard rule breaks.
 */
public final class Gravity {
    /** Persistent-data key of the saved direction. */
    public static final String NBT = "factoryascent_gravity";
    /** Ticks without a surface under the feet (along the turned gravity) before the client lets go. */
    public static final int LOST_TICKS = 16;
    /** The server's own, laxer limits (the client normally resolves these first). */
    public static final int SERVER_LOST_TICKS = 100, SERVER_RULE_GRACE = 10;
    /** How far a requested position may be from where the server has the player. */
    public static final double MAX_REQUEST_SHIFT = 4.0;

    private Gravity() {}

    // ---------------------------------------------------------------- state

    public static Direction of(Entity entity) {
        Direction d = ((GravityHolder) entity).factoryascent$gravity();
        return d == null ? Direction.DOWN : d;
    }

    public static boolean isTurned(Entity entity) {
        return of(entity) != Direction.DOWN;
    }

    /** Config: real gravity turning (true) or the old upright wall climbing (false). */
    public static boolean enabled() {
        return SpaceConfig.get(SpaceConfig.BOOTS_ROTATE_GRAVITY);
    }

    // ---------------------------------------------------------------- the rules (pure)

    /** Hard reasons gravity must be plain DOWN, whatever surface the boots touch. */
    public static boolean mustReset(boolean enabled, boolean wearing, double baseGravity, boolean flying, boolean riding,
                                    boolean swimming, boolean fallFlying, boolean sleeping, boolean inWater, boolean spectator,
                                    boolean dead) {
        return !enabled || !wearing || baseGravity >= 1.0 || flying || riding || swimming || fallFlying || sleeping
                || inWater || spectator || dead;
    }

    /** Letting go of a wall or ceiling: sneaking, or no surface under the feet for {@code limit} ticks. */
    public static boolean detach(Direction current, boolean sneaking, int ticksWithoutSurface, int limit) {
        return current != Direction.DOWN && (sneaking || ticksWithoutSurface > limit);
    }

    /**
     * Picks the new gravity: a wall (world direction) the feet touch and the player walks into
     * becomes the floor; bumping the head into a surface while rising (a jump) flips onto it.
     * Walls must lie across the current gravity; {@code wish} is the world direction the keys push.
     */
    public static Direction pick(Direction current, Set<Direction> walls, boolean headBump, boolean onGround, boolean rising,
                                 Vec3 wish, boolean sneaking) {
        if (sneaking) return current;
        Direction best = current;
        double bestDot = 0.5;
        for (Direction wall : walls) {
            if (wall.getAxis() == current.getAxis()) continue;
            double dot = wish.x * wall.getStepX() + wish.y * wall.getStepY() + wish.z * wall.getStepZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = wall;
            }
        }
        if (best != current) return best;
        if (headBump && !onGround && rising) return current.getOpposite();
        return current;
    }

    // ---------------------------------------------------------------- surfaces (world queries)

    /** A box given in the local frame relative to the feet, in the world. */
    public static AABB localRegion(Direction g, Vec3 feet, double x0, double y0, double z0, double x1, double y1, double z1) {
        Vec3 a = GravityFrame.toWorld(g, x0, y0, z0), b = GravityFrame.toWorld(g, x1, y1, z1);
        return new AABB(feet.x + a.x, feet.y + a.y, feet.z + a.z, feet.x + b.x, feet.y + b.y, feet.z + b.z);
    }

    /**
     * A wall on one side of the body (world direction {@code side}, across gravity) touching it on
     * its centre line, at the feet and still at the waist: a real wall, not a step or a door frame.
     */
    public static boolean wallAt(Level level, Entity entity, Direction side) {
        Direction g = of(entity);
        if (side.getAxis() == g.getAxis()) return false;
        Direction local = GravityFrame.toLocal(g, side);
        double w = entity.getBbWidth() / 2.0;
        double sx = local.getStepX(), sz = local.getStepZ();
        for (double[] band : new double[][] {{0.1, 0.45}, {0.85, 1.2}}) {
            // depth 0.06 beyond the body face; 0.2 wide around the centre line
            double ax = sx != 0 ? sx * w : -0.1, bx = sx != 0 ? sx * (w + 0.06) : 0.1;
            double az = sz != 0 ? sz * w : -0.1, bz = sz != 0 ? sz * (w + 0.06) : 0.1;
            AABB region = localRegion(g, entity.position(), ax, band[0], az, bx, band[1], bz);
            if (level.noCollision(entity, region)) return false;
        }
        return true;
    }

    /** Every wall the feet touch, as world directions. */
    public static Set<Direction> walls(Level level, Entity entity) {
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        Direction g = of(entity);
        for (Direction d : Direction.values()) {
            if (d.getAxis() != g.getAxis() && wallAt(level, entity, d)) out.add(d);
        }
        return out;
    }

    /** Something solid just above the head (local up). */
    public static boolean headBump(Level level, Entity entity) {
        Direction g = of(entity);
        double w = entity.getBbWidth() / 2.0 - 0.05, h = entity.getBbHeight();
        return !level.noCollision(entity, localRegion(g, entity.position(), -w, h, -w, w, h + 0.1, w));
    }

    /** A surface within {@code reach} below the feet along the entity's gravity. */
    public static boolean surfaceNear(Level level, Entity entity, double reach) {
        if (entity.onGround()) return true;
        Direction g = of(entity);
        double w = entity.getBbWidth() / 2.0 - 0.01;
        return !level.noCollision(entity, localRegion(g, entity.position(), -w, -reach, -w, w, -0.001, w));
    }

    // ---------------------------------------------------------------- changing

    /** What a change did (for the client's turn animation). */
    public record Change(Direction from, Direction to, Quaternionf turn, Vec3 oldEye, Vec3 newEye) {}

    /**
     * The feet position to turn into: on a surface ({@code onto}) the feet go onto the plane of the
     * face the body touches, at the height of the old eyes; letting go, the eyes stay put. Then the
     * nearest spot where the turned body fits. Null if there is no room.
     */
    @Nullable
    public static Vec3 spotFor(Player player, Direction to, boolean onto) {
        Direction from = of(player);
        Vec3 eye = player.getEyePosition();
        Vec3 base;
        if (onto) {
            double plane = GravityFrame.face(player.getBoundingBox(), to);
            base = switch (to.getAxis()) {
                case X -> new Vec3(plane, eye.y, eye.z);
                case Y -> new Vec3(eye.x, plane, eye.z);
                case Z -> new Vec3(eye.x, eye.y, plane);
            };
        } else {
            base = eye.subtract(GravityFrame.toWorld(to, 0, player.getEyeHeight(), 0));
        }
        EntityDimensions dims = player.getDimensions(player.getPose());
        Vec3 upNew = GravityFrame.up(to), upOld = GravityFrame.up(from);
        Vec3[] dirs = {upOld.scale(-1), upNew, upOld, upNew.scale(-1)};
        for (int k = 0; k <= 36; k++) {
            double dist = k * 0.05;
            for (Vec3 dir : dirs) {
                Vec3 p = base.add(dir.scale(dist));
                if (fits(player, to, p, dims)) return p;
                if (k == 0) break;
            }
        }
        return null;
    }

    public static boolean fits(Entity entity, Direction g, Vec3 feet, EntityDimensions dims) {
        AABB box = GravityFrame.box(g, feet, dims.width(), dims.height()).deflate(1.0E-7);
        return entity.level().noCollision(entity, box);
    }

    /**
     * Turns the player to gravity {@code to} with its feet at {@code feet}: the velocity and the
     * facing are carried along by the same turn (so you keep walking the way you walked and look the
     * way you looked), falls are forgiven, and the server saves it.
     */
    public static Change apply(Player player, Direction to, Vec3 feet) {
        Direction from = of(player);
        Vec3 oldEye = player.getEyePosition();
        float yaw = player.getYRot();
        Quaternionf turn = GravityFrame.turn(from, to, GravityFrame.toWorld(from, GravityFrame.forward(yaw)));
        float shift = GravityFrame.yawShift(from, to, yaw);
        Vec3 v = GravityFrame.rotate(turn, player.getDeltaMovement());
        Vec3 local = GravityFrame.toLocal(to, v);
        ((GravityHolder) player).factoryascent$setGravityRaw(to);
        player.setPos(feet);
        player.setYRot(yaw + shift);
        player.setYHeadRot(player.getYHeadRot() + shift);
        player.yBodyRot += shift;
        player.setOldPosAndRot();
        player.yHeadRotO = player.yHeadRot;
        player.yBodyRotO = player.yBodyRot;
        // keep moving along the new floor, but don't push off it
        player.setDeltaMovement(GravityFrame.toWorld(to, local.x, Math.min(local.y, 0.0), local.z));
        player.resetFallDistance();
        if (!player.level().isClientSide()) player.getPersistentData().putString(NBT, to.getSerializedName());
        return new Change(from, to, turn, oldEye, player.getEyePosition());
    }

    /** Finds room and turns; null if the turned body doesn't fit anywhere near. */
    @Nullable
    public static Change change(Player player, Direction to, boolean onto) {
        if (of(player) == to) return null;
        Vec3 spot = spotFor(player, to, onto);
        if (spot == null) {
            if (to != Direction.DOWN) return null;
            // letting go must always work: fall back to the old feet
            spot = player.position();
        }
        return apply(player, to, spot);
    }

    // ---------------------------------------------------------------- server side

    private static final Map<UUID, int[]> SERVER_COUNTERS = new ConcurrentHashMap<>();

    /** Whether the server lets this player keep (or get) a turned gravity right now. */
    public static boolean allowedOnServer(Player p) {
        return !mustReset(enabled(), MagneticBoots.wearing(p), Orbit.gravityFor(p.level().dimension()), p.getAbilities().flying,
                p.isPassenger(), p.isSwimming(), p.isFallFlying(), p.isSleeping(), p.isInWater(), p.isSpectator(), !p.isAlive());
    }

    static void onServerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!isTurned(sp)) {
            SERVER_COUNTERS.remove(sp.getUUID());
            return;
        }
        sp.resetFallDistance();
        int[] c = SERVER_COUNTERS.computeIfAbsent(sp.getUUID(), u -> new int[2]);
        c[0] = allowedOnServer(sp) ? 0 : c[0] + 1;
        c[1] = surfaceNear(sp.level(), sp, MagneticBoots.REACH + 0.5) ? 0 : c[1] + 1;
        if (!enabled() || c[0] > SERVER_RULE_GRACE || c[1] > SERVER_LOST_TICKS) forceDown(sp);
    }

    /** The server lets go for the player (and tells everyone, the player included). */
    public static void forceDown(ServerPlayer sp) {
        SERVER_COUNTERS.remove(sp.getUUID());
        if (change(sp, Direction.DOWN, false) == null) return;
        sp.connection.resetPosition();
        sync(sp, true);
    }

    /** Sends the player's gravity to the players tracking it, and to itself too when {@code self}. */
    public static void sync(ServerPlayer sp, boolean self) {
        GravityPayloads.Sync msg = new GravityPayloads.Sync(sp.getId(), of(sp), self, sp.getX(), sp.getY(), sp.getZ());
        if (self) PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, msg);
        else PacketDistributor.sendToPlayersTrackingEntity(sp, msg);
    }

    /** A client asks to turn: checked against the rules and the world, then applied or refused (the client is corrected). */
    static void onRequest(ServerPlayer sp, Direction to, Vec3 feet) {
        boolean ok = to == Direction.DOWN || allowedOnServer(sp);
        ok &= feet.distanceTo(sp.position()) <= MAX_REQUEST_SHIFT;
        EntityDimensions dims = sp.getDimensions(sp.getPose());
        if (ok && !fits(sp, to, feet, dims)) {
            // the client's world may be a hair off: accept a spot of the server's own nearby
            Vec3 spot = to == of(sp) ? null : spotNear(sp, to, feet, dims);
            if (spot == null) ok = to == Direction.DOWN;
            else feet = spot;
        }
        if (ok && to != Direction.DOWN) {
            // a surface must be under the new feet
            double w = dims.width() / 2.0 - 0.01;
            ok = !sp.level().noCollision(sp, localRegion(to, feet, -w, -0.6, -w, w, 0.0, w));
        }
        if (!ok) {
            sync(sp, true);
            return;
        }
        if (to == of(sp)) return;
        apply(sp, to, feet);
        sp.connection.resetPosition();
        SERVER_COUNTERS.remove(sp.getUUID());
        sync(sp, false);
    }

    @Nullable
    private static Vec3 spotNear(Player p, Direction to, Vec3 feet, EntityDimensions dims) {
        for (int k = 1; k <= 6; k++) {
            for (Direction d : Direction.values()) {
                Vec3 q = feet.add(d.getStepX() * k * 0.05, d.getStepY() * k * 0.05, d.getStepZ() * k * 0.05);
                if (fits(p, to, q, dims)) return q;
            }
        }
        return null;
    }

    /** Joining: the saved gravity comes back (if the boots still allow it). */
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Direction saved = Direction.byName(sp.getPersistentData().getStringOr(NBT, "down"));
        if (saved != null && saved != Direction.DOWN && allowedOnServer(sp)) {
            ((GravityHolder) sp).factoryascent$setGravityRaw(saved);
            sp.setPos(sp.position());
            sync(sp, true);
        } else if (saved != null && saved != Direction.DOWN) {
            sp.getPersistentData().putString(NBT, "down");
        }
    }

    /** Another dimension, another floor: always arrive the normal way up. */
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && isTurned(sp)) {
            ((GravityHolder) sp).factoryascent$setGravityRaw(Direction.DOWN);
            sp.setPos(sp.position());
            sp.getPersistentData().putString(NBT, "down");
            sync(sp, true);
        }
    }

    /** Teleported (commands, pearls, chorus fruit): arrive the normal way up, wherever that is. */
    static void onTeleport(net.neoforged.neoforge.event.entity.EntityTeleportEvent event) {
        if (event instanceof net.neoforged.bus.api.ICancellableEvent c && c.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp) || !isTurned(sp)) return;
        ((GravityHolder) sp).factoryascent$setGravityRaw(Direction.DOWN);
        sp.setPos(sp.position());
        sp.getPersistentData().putString(NBT, "down");
        SERVER_COUNTERS.remove(sp.getUUID());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new GravityPayloads.Sync(sp.getId(), Direction.DOWN, true, event.getTargetX(), event.getTargetY(), event.getTargetZ()));
    }

    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && isTurned(target) && event.getEntity() instanceof ServerPlayer viewer) {
            PacketDistributor.sendToPlayer(viewer, new GravityPayloads.Sync(target.getId(), of(target), false,
                    target.getX(), target.getY(), target.getZ()));
        }
    }
}
