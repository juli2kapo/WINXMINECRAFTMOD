package net.juli2kapo.factoryascent.trains;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Moves a whole train one tick (called from its master's tick):
 * <ol>
 * <li>forces: locomotive traction toward the throttle's target speed (slower to pick up the more
 * the train weighs), driver's brake, gravity on slopes, rolling friction, pushes from players;</li>
 * <li>rails: powered rails boost (up to a minecart's 0.4 b/t) and unpowered ones brake to a halt,
 * exactly as for minecarts; a Train Station holding any vehicle brakes the whole train;</li>
 * <li>the master walks its distance along the track; every other vehicle is placed at its
 * coupling distance from its neighbour, outward from the master, along the rails;</li>
 * <li>if the leading end runs out of track (a buffer, a junction set against it) or into another
 * train the train stops instead; if the track behind a vehicle is gone the coupling there breaks;</li>
 * <li>each vehicle gets its new position, velocity, heading and speed, and the blocks under it
 * react (detector rails, activator rails).</li>
 * </ol>
 */
public final class TrainPhysics {
    /** Slope acceleration (vanilla minecarts' slide speed), blocks per tick². */
    public static final double SLOPE = 0.0078125;
    /** Top speed powered rails boost a train to (a minecart's). */
    public static final double POWERED_RAIL_MAX = 0.4;
    /** Station brake, blocks per tick². */
    public static final double STATION_BRAKE = 0.08;
    /** Driver's brake (Space), blocks per tick². */
    public static final double BRAKE = 0.025;

    private TrainPhysics() {}

    /** Coupling distance (centre to centre, along the track) between two coupled vehicles. */
    public static double spacing(RollingStock a, RollingStock b) {
        return (a.length() + b.length()) / 2 + RollingStock.GAP;
    }

    public static void step(ServerLevel level, Consist consist) {
        List<RollingStock> m = consist.members();
        RollingStock master = consist.master();
        if (master == null) return;
        int n = m.size(), k = m.indexOf(master);
        TrackWalker.Spot start = master.spot();
        if (start == null) return;
        start = TrackWalker.refresh(level, start, master.position(), master);
        if (start == null) return;
        master.spot = start;

        // the direction each vehicle faces relative to the master (+1 same way, -1 reversed)
        int[] sign = new int[n];
        sign[k] = 1;
        for (int i = k + 1; i < n; i++) sign[i] = sign[i - 1] * (sameEnd(m.get(i - 1), m.get(i)) ? -1 : 1);
        for (int i = k - 1; i >= 0; i--) sign[i] = sign[i + 1] * (sameEnd(m.get(i + 1), m.get(i)) ? -1 : 1);

        double mass = 0;
        for (RollingStock r : m) mass += r.mass();
        double v = master.speed;

        // pushes (only felt by trains without a locomotive, see RollingStock#push)
        for (int i = 0; i < n; i++) {
            RollingStock r = m.get(i);
            if (r.pendingImpulse != 0) {
                v += r.pendingImpulse * sign[i] * r.mass() / mass;
                r.pendingImpulse = 0;
            }
        }
        // gravity on slopes: each vehicle pulls downhill in proportion to its mass
        double g = 0;
        for (int i = 0; i < n; i++) {
            RollingStock r = m.get(i);
            TrackWalker.Spot s = i == k ? start : r.spot;
            if (s == null || !s.shape().isSlope()) continue;
            Vec3 t = s.tangent();
            boolean frontUp = (r.frontTowardB ? t.y : -t.y) > 0;
            g += (frontUp ? -1 : 1) * sign[i] * SLOPE * r.mass();
        }
        v += g / mass;
        v *= 0.997; // rolling friction
        // locomotives
        Locomotive driver = null;
        for (RollingStock r : m) if (r instanceof Locomotive l && l.driver() != null) { driver = l; break; }
        if (driver == null) for (RollingStock r : m) if (r instanceof Locomotive l) { driver = l; break; }
        double cap = 1.0;
        double[] efforts = new double[n];
        boolean held = false;
        for (RollingStock r : m) held |= r.stationHold > 0;
        if (driver != null) {
            int di = m.indexOf(driver);
            double throttle = held ? 0 : driver.throttle() * sign[di]; // in the master's frame
            double top = 0, power = 0;
            for (int i = 0; i < n; i++) {
                if (!(m.get(i) instanceof Locomotive l)) continue;
                top = Math.max(top, l.topSpeed());
                double traction = l.traction();
                power += l.power() * traction;
                efforts[i] = driver.braking() ? 0 : Math.abs(throttle) * traction;
                l.setStatus(held ? Locomotive.STATUS_STATION : !l.fuelled() && Math.abs(l.throttle()) > 0.001
                        ? Locomotive.STATUS_NO_FUEL : Locomotive.STATUS_OK);
            }
            double accel = power / mass * TrainConfig.acceleration();
            double target = throttle * top * Math.min(1, power / Math.max(1e-9, driverPower(driver)));
            if (Math.abs(throttle) > 0.001 && power > 0) {
                if (Math.signum(target) == Math.signum(v) || Math.abs(v) < 1e-4) {
                    if (Math.abs(v) < Math.abs(target)) v += Math.signum(target) * Math.min(accel, Math.abs(target) - Math.abs(v));
                } else {
                    v -= Math.signum(v) * Math.min(accel * 1.5, Math.abs(v)); // reverser against the motion: dynamic brake
                }
            }
            if (driver.braking()) v -= Math.signum(v) * Math.min(BRAKE, Math.abs(v));
            cap = top * 1.15;
        }
        // powered rails: boost or halt, like minecarts
        int halts = 0;
        double boost = 0;
        Vec3 kick = null;
        for (int i = 0; i < n; i++) {
            RollingStock r = m.get(i);
            TrackWalker.Spot s = i == k ? start : r.spot;
            if (s == null) continue;
            BlockState state = level.getBlockState(s.rail());
            if (!(state.getBlock() instanceof PoweredRailBlock rail) || rail.isActivatorRail()) continue;
            if (!state.getValue(PoweredRailBlock.POWERED)) {
                halts++;
            } else if (Math.abs(v) > 0.01) {
                if (Math.abs(v) < POWERED_RAIL_MAX) boost += 0.06 * r.mass() / mass;
            } else if (kick == null) {
                kick = startKick(level, s.rail(), s);
            }
        }
        if (halts > 0 && (driver == null || Math.abs(driver.throttle()) < 0.001 || held)) {
            v = Math.abs(v) < 0.03 ? 0 : v * 0.5;
        } else if (halts > 0) {
            v = Math.signum(v) * Math.min(Math.abs(v), 0.1); // a locomotive can creep over a halt rail but not race it
        }
        if (boost > 0) v = Math.signum(v) * Math.min(POWERED_RAIL_MAX, Math.abs(v) + boost);
        if (kick != null && Math.abs(v) <= 0.01) {
            v = (TrackWalker.pointsTowardB(start, kick) == master.frontTowardB ? 1 : -1) * 0.02;
        }
        if (held) v -= Math.signum(v) * Math.min(STATION_BRAKE, Math.abs(v));
        v = Mth.clamp(v, -cap, cap);
        if (Math.abs(v) < 5e-4) v = 0;

        // move: the master first, then everyone else outward from it
        Placement plan = place(level, m, k, start, master.frontTowardB, v);
        boolean endOfTrack = plan.stopped;
        if (plan.stopped) {
            v = 0;
            if (plan.spots[k] == null) plan = place(level, m, k, start, master.frontTowardB, 0);
        }
        // collisions at the leading end
        if (v != 0) {
            int lead = leadIndex(m, k, v);
            Vec3 dir = travelDirection(plan, lead, m, k, v);
            Obstacle hit = obstacle(level, consist, m.get(lead), plan.spots[lead].pos(), dir);
            if (hit == Obstacle.TRAIN) {
                if (Math.abs(v) > 0.15) level.playSound(null, m.get(lead).blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.6f, 0.6f);
                v = 0;
                plan = place(level, m, k, start, master.frontTowardB, 0);
                if (driver != null) driver.setStatus(Locomotive.STATUS_BLOCKED);
            } else if (hit == Obstacle.CART) {
                v = Math.signum(v) * Math.min(Math.abs(v), 0.35);
            }
        }
        if (endOfTrack && driver != null && driver.status() == Locomotive.STATUS_OK && Math.abs(driver.throttle()) > 0.001) {
            driver.setStatus(Locomotive.STATUS_END);
        }
        // apply
        long now = level.getGameTime();
        int placed = plan.breakAt < 0 ? n : plan.breakAt;
        int from = plan.breakFrom < 0 ? 0 : plan.breakFrom;
        for (int i = from; i < placed; i++) {
            RollingStock r = m.get(i);
            TrackWalker.Spot s = plan.spots[i];
            if (s == null) continue;
            Vec3 old = r.position();
            Vec3 now3 = s.pos();
            r.spot = s;
            r.frontTowardB = plan.frontB[i];
            r.setPos(now3.x, now3.y, now3.z);
            r.setDeltaMovement(now3.subtract(old));
            r.speed = v * sign[i];
            r.drivenAt = now;
            r.updateHeadingFromSpot();
            r.resetFallDistance();
            r.applyEffectsFromBlocks(old, now3);
            BlockState under = level.getBlockState(s.rail());
            if (under.getBlock() instanceof PoweredRailBlock rail && rail.isActivatorRail()) {
                r.activateMinecart(level, s.rail().getX(), s.rail().getY(), s.rail().getZ(), under.getValue(PoweredRailBlock.POWERED));
            }
        }
        if (plan.breakAt >= 0) m.get(plan.breakAt - 1).uncouple(m.get(plan.breakAt));
        if (plan.breakFrom > 0) m.get(plan.breakFrom).uncouple(m.get(plan.breakFrom - 1));
        if (v != 0) shove(level, consist, m, plan);
        // fuel is only used while the locomotives actually work against the train (not against a buffer stop)
        boolean stuck = endOfTrack || driver != null && driver.status() == Locomotive.STATUS_BLOCKED;
        for (int i = 0; i < n; i++) {
            if (efforts[i] > 0.001 && m.get(i) instanceof Locomotive l) l.burn(stuck ? efforts[i] * 0.1 : efforts[i]);
        }
        if (driver != null) driver.setCars(n);
    }

    /** The power the driving locomotive alone has at full traction (for scaling the target speed). */
    private static double driverPower(Locomotive driver) {
        return Math.max(1e-9, driver.power());
    }

    /** Whether two coupled neighbours are coupled front-to-front or rear-to-rear (they face opposite ways). */
    private static boolean sameEnd(RollingStock a, RollingStock b) {
        Boolean ea = a.endLinkedTo(b), eb = b.endLinkedTo(a);
        return ea != null && ea.equals(eb);
    }

    /** Index of the vehicle at the end the train is moving toward. */
    private static int leadIndex(List<RollingStock> m, int k, double v) {
        if (m.size() == 1) return k;
        boolean towardMasterFront = v > 0;
        RollingStock master = m.get(k);
        // which side of the list lies in front of the master?
        boolean afterIsFront = k + 1 < m.size() ? Boolean.TRUE.equals(master.endLinkedTo(m.get(k + 1)))
                : !Boolean.TRUE.equals(master.endLinkedTo(m.get(k - 1)));
        boolean leadAfter = afterIsFront == towardMasterFront;
        return leadAfter ? m.size() - 1 : 0;
    }

    private static Vec3 travelDirection(Placement plan, int lead, List<RollingStock> m, int k, double v) {
        Vec3 t = plan.spots[lead].tangent();
        // the master moves along its front when v > 0; propagate through the facing signs
        boolean front = plan.frontB[lead];
        Vec3 f = front ? t : t.scale(-1);
        int s = leadSign(m, k, lead);
        return f.scale(Math.signum(v) * s);
    }

    private static int leadSign(List<RollingStock> m, int k, int lead) {
        int s = 1;
        if (lead > k) for (int i = k + 1; i <= lead; i++) s *= sameEnd(m.get(i - 1), m.get(i)) ? -1 : 1;
        else for (int i = k - 1; i >= lead; i--) s *= sameEnd(m.get(i + 1), m.get(i)) ? -1 : 1;
        return s;
    }

    /** Which way a stopped cart on a powered rail next to a solid block gets kicked (like vanilla). */
    private static Vec3 startKick(ServerLevel level, BlockPos pos, TrackWalker.Spot s) {
        Vec3 t = s.tangent();
        Direction.Axis axis = Math.abs(t.x) > Math.abs(t.z) ? Direction.Axis.X : Direction.Axis.Z;
        if (axis == Direction.Axis.X) {
            if (level.getBlockState(pos.west()).isRedstoneConductor(level, pos.west())) return new Vec3(1, 0, 0);
            if (level.getBlockState(pos.east()).isRedstoneConductor(level, pos.east())) return new Vec3(-1, 0, 0);
        } else {
            if (level.getBlockState(pos.north()).isRedstoneConductor(level, pos.north())) return new Vec3(0, 0, 1);
            if (level.getBlockState(pos.south()).isRedstoneConductor(level, pos.south())) return new Vec3(0, 0, -1);
        }
        return null;
    }

    // ---------------------------------------------------------------- placement

    /** Where everyone goes this tick; stopped = the leading end ran out of track; breakAt/breakFrom = couplings to cut. */
    private static final class Placement {
        final TrackWalker.Spot[] spots;
        final boolean[] frontB;
        boolean stopped;
        int breakAt = -1, breakFrom = -1;

        Placement(int n) {
            spots = new TrackWalker.Spot[n];
            frontB = new boolean[n];
        }
    }

    private static Placement place(ServerLevel level, List<RollingStock> m, int k, TrackWalker.Spot start, boolean frontB, double v) {
        int n = m.size();
        Placement p = new Placement(n);
        RollingStock master = m.get(k);
        boolean towardB = frontB == (v >= 0);
        TrackWalker.Result r = TrackWalker.walk(level, start, towardB, Math.abs(v), master);
        if (r.blocked()) {
            // as far as the track goes (up to the buffer), then stop
            p.stopped = true;
            if (r.travelled() < 1e-3) return p;
            Placement shorter = place(level, m, k, start, frontB, Math.signum(v) * (r.travelled() - 1e-4));
            shorter.stopped = true;
            return shorter;
        }
        p.spots[k] = r.spot();
        p.frontB[k] = v >= 0 ? r.towardB() : !r.towardB();
        // which side leads (only matters while moving)
        boolean afterIsFront = k + 1 < n ? Boolean.TRUE.equals(master.endLinkedTo(m.get(k + 1)))
                : k > 0 && !Boolean.TRUE.equals(master.endLinkedTo(m.get(k - 1)));
        boolean afterLeads = v != 0 && afterIsFront == (v > 0);
        boolean beforeLeads = v != 0 && !afterLeads;
        for (int i = k + 1; i < n; i++) {
            if (!placeNext(level, m, p, i - 1, i)) {
                if (afterLeads) {
                    p.stopped = true;
                    p.spots[k] = null; // redo standing still
                    return p;
                }
                p.breakAt = i;
                break;
            }
        }
        for (int i = k - 1; i >= 0; i--) {
            if (!placeNext(level, m, p, i + 1, i)) {
                if (beforeLeads) {
                    p.stopped = true;
                    p.spots[k] = null;
                    return p;
                }
                p.breakFrom = i + 1;
                break;
            }
        }
        return p;
    }

    /** Places vehicle {@code to} at its coupling distance from its already placed neighbour {@code from}. */
    private static boolean placeNext(ServerLevel level, List<RollingStock> m, Placement p, int from, int to) {
        RollingStock a = m.get(from), b = m.get(to);
        Boolean aFront = a.endLinkedTo(b), bFront = b.endLinkedTo(a);
        if (aFront == null || bFront == null) return false;
        TrackWalker.Spot s = p.spots[from];
        // walk out of a's coupled end
        boolean towardB = aFront == p.frontB[from];
        TrackWalker.Result r = TrackWalker.walk(level, s, towardB, spacing(a, b), b);
        if (r.blocked()) return false;
        p.spots[to] = r.spot();
        // b's coupled end faces back toward a, i.e. against the walk
        p.frontB[to] = bFront ? !r.towardB() : r.towardB();
        return true;
    }

    // ---------------------------------------------------------------- obstacles

    enum Obstacle { NONE, CART, TRAIN }

    /** What the leading vehicle's nose runs into: another train stops us, a lone cart gets pushed along. */
    private static Obstacle obstacle(ServerLevel level, Consist consist, RollingStock lead, Vec3 pos, Vec3 dir) {
        AABB nose = noseBox(pos, dir, lead.length());
        Obstacle result = Obstacle.NONE;
        for (Entity e : level.getEntities(lead, nose.inflate(1.6), e -> e instanceof AbstractMinecart && e.isAlive())) {
            if (e instanceof RollingStock r && consist.members().contains(r)) continue;
            AABB box = e instanceof RollingStock r ? bodyBox(r.position(), r.front(), r.length()) : e.getBoundingBox();
            if (!box.intersects(nose)) continue;
            // only what is ahead of us counts
            Vec3 to = e.position().subtract(pos);
            if (to.x * dir.x + to.z * dir.z <= 0) continue;
            if (e instanceof RollingStock r && (r.trainDriven())) {
                return Obstacle.TRAIN;
            }
            Vec3 push = dir.scale(0.4);
            e.setDeltaMovement(push.x, e.getDeltaMovement().y, push.z);
            result = Obstacle.CART;
        }
        return result;
    }

    /** The front half of a vehicle's body (where its buffers would touch something ahead). */
    private static AABB noseBox(Vec3 pos, Vec3 dir, double length) {
        Vec3 tip = pos.add(dir.x * length / 2, 0, dir.z * length / 2);
        Vec3 mid = pos.add(dir.x * length / 4, 0, dir.z * length / 4);
        return new AABB(mid, tip).inflate(0.35, 0, 0.35).expandTowards(0, 1, 0);
    }

    private static AABB bodyBox(Vec3 pos, Vec3 front, double length) {
        Vec3 a = pos.add(front.scale(length / 2)), b = pos.subtract(front.scale(length / 2));
        return new AABB(a, b).inflate(0.35, 0, 0.35).expandTowards(0, 1, 0);
    }

    /** Creatures standing on the line are shoved aside by a moving train (never run over). */
    private static void shove(ServerLevel level, Consist consist, List<RollingStock> m, Placement plan) {
        for (int i = 0; i < m.size(); i++) {
            RollingStock r = m.get(i);
            if (plan.spots[i] == null) continue;
            AABB body = bodyBox(r.position(), r.front(), r.length());
            for (Entity e : level.getEntities(r, body, EntitySelector.pushableBy(r))) {
                if (!(e instanceof LivingEntity) || e.isPassenger()) continue;
                Vec3 f = r.front();
                Vec3 side = new Vec3(f.z, 0, -f.x);
                Vec3 to = e.position().subtract(r.position());
                double s = Math.signum(to.x * side.x + to.z * side.z);
                if (s == 0) s = 1;
                Vec3 v = r.getDeltaMovement();
                e.push(side.x * s * 0.25 + v.x * 0.5, 0.05, side.z * s * 0.25 + v.z * 0.5);
                e.hurtMarked = true;
            }
        }
    }
}
