package net.juli2kapo.factoryascent.ships;

import net.juli2kapo.factoryascent.orbital.LaunchControllerBlock;
import net.juli2kapo.factoryascent.orbital.LaunchPadBlock;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Orbital age spacecraft for two (pilot in front, one passenger behind) with an 18-slot cargo bay
 * and a tank of Rocket Fuel (poured in through the cockpit's fuel slot, or pulled from containers
 * next to a Launch Pad while docked on it).
 *
 * <p>Flight: W/S main engines along the heading, A/D yaw, Space the downward VTOL thrusters (climb),
 * Shift a controlled descent; with no vertical key it hovers (which still burns fuel). In an
 * atmosphere gravity pulls; in orbit it drifts on its momentum. Climbing above the Overworld's
 * build limit + {@link ShipConfig#ORBIT_MARGIN} carries it, crew and all, to
 * {@code factoryascent:orbit} above the same x/z; descending below {@link ShipConfig#ORBIT_REENTRY_Y}
 * there re-enters the Overworld (heat, shake) for the pilot to land. {@link ShipMath#decide} holds the
 * rule. The cabin is sealed: {@link #isSealed(Entity)}.
 *
 * <p>Leaving: Shift only works once landed; in flight the hatch key (K) opens the hatch on the
 * ground or, in orbit, when nearly stopped (an EVA: the space rules apply outside).
 */
public class Shuttle extends AbstractShip {
    public static final int STATE_LANDED = 0, STATE_DOCKED = 1, STATE_FLYING = 2, STATE_ORBIT = 3, STATE_REENTRY = 4;
    public static final int TANK_ITEMS = 16;
    /** Fuel needed to lift off at all (a few seconds of climb). */
    public static final int LIFTOFF_FUEL = 60;
    private static final int FUEL_SLOT = 18;
    private static final double GRAVITY = 0.04;
    private static final Vec3[] SEATS = {new Vec3(0, 14 / 16.0, 15 / 16.0), new Vec3(0, 14 / 16.0, 0)};

    private int fuel;
    private int reentryTicks;
    private double lastVy;
    private boolean warnedEmpty;
    private @Nullable BlockPos dockedAt;

    public Shuttle(EntityType<? extends Shuttle> type, Level level) {
        super(type, level);
    }

    /** Whether an entity sits in a shuttle's sealed cabin (no oxygen needed, protected from vacuum and re-entry heat). */
    public static boolean isSealed(Entity entity) {
        return entity.getVehicle() instanceof Shuttle;
    }

    @Override
    public int cargoSize() {
        return 18;
    }

    @Override
    public int fuelSlot() {
        return FUEL_SLOT;
    }

    @Override
    protected Vec3[] seats() {
        return SEATS;
    }

    @Override
    public boolean isSpacecraft() {
        return true;
    }

    @Override
    public int fuel() {
        return fuel;
    }

    @Override
    public int fuelCapacity() {
        return TANK_ITEMS * ShipConfig.fuelPerItem();
    }

    @Override
    protected void setFuel(int value) {
        fuel = Math.max(0, Math.min(fuelCapacity(), value));
    }

    @Override
    public boolean acceptsFuel(ItemStack stack) {
        return stack.is(OrbitalContent.ROCKET_FUEL.get());
    }

    public void startReentry() {
        reentryTicks = 120;
        setState(STATE_REENTRY);
    }

    public int reentryTicks() {
        return reentryTicks;
    }

    public ShipMath.Realm realm() {
        return OrbitTransfer.realm(level());
    }

    private boolean grounded() {
        return onGround() || verticalCollisionBelow;
    }

    @Override
    protected void physics() {
        boolean orbit = realm() == ShipMath.Realm.ORBIT;
        boolean airborne = !grounded();
        boolean up = pressed(IN_UP);
        // nobody at the controls in the air: the autopilot sets it down gently
        boolean down = pressed(IN_DOWN) || (input == 0 && pilot() == null && airborne && !orbit);
        boolean ahead = pressed(IN_FORWARD), back = pressed(IN_BACK);
        boolean horizontal = ahead || back;
        int cost = ShipMath.shuttleFuelPerTick(orbit, airborne, up, down, horizontal, ShipConfig.fuelUse());
        boolean powered;
        if (cost == 0) {
            powered = fuel > 0 || ShipConfig.fuelUse() == 0;
        } else if (!airborne && up && fuel < LIFTOFF_FUEL && ShipConfig.fuelUse() > 0) {
            powered = false; // not enough to lift off
        } else {
            powered = fuel >= cost;
            if (powered) fuel -= cost;
        }
        if (!powered && (up || horizontal) && pilot() != null && !warnedEmpty) {
            pilot().sendOverlayMessage(Component.translatable("message.factoryascent.ship.no_fuel").withStyle(ChatFormatting.RED));
            warnedEmpty = true;
        } else if (powered) {
            warnedEmpty = false;
        }
        // yaw: RCS in orbit, rudder-less but agile in the air, slow taxi on the ground
        int steer = (pressed(IN_RIGHT) ? 1 : 0) - (pressed(IN_LEFT) ? 1 : 0);
        if (powered || !airborne) setYRot(getYRot() + steer * (orbit ? 2.2f : airborne ? 3f : 1.5f));
        Vec3 fwd = forward();
        Vec3 v = getDeltaMovement();
        if (orbit) {
            double ax = 0, ay = 0, az = 0;
            if (powered) {
                double f = ahead ? 0.022 : back ? -0.022 : 0;
                ax = fwd.x * f;
                az = fwd.z * f;
                ay = up ? 0.022 : down ? -0.022 : 0;
            }
            v = v.add(ax, ay, az).scale(0.996); // drifting momentum, a whisper of drag
            if (v.length() > 1.6) v = v.normalize().scale(1.6);
            reentryTicks = 0;
            setState(STATE_ORBIT);
        } else {
            double vy = v.y;
            if (powered && up) {
                vy += 0.085;
            } else if (powered && down && airborne) {
                vy += GRAVITY * 0.55;
                vy = Math.max(vy, -0.6);
            } else if (powered && airborne) {
                vy += GRAVITY; // hover
                vy *= 0.85;
            }
            vy = (vy - GRAVITY) * 0.98;
            vy = Mth.clamp(vy, -1.6, 1.3);
            double hx = v.x, hz = v.z;
            if (powered && horizontal && airborne) {
                double f = ahead ? 0.03 : -0.02;
                hx += fwd.x * f;
                hz += fwd.z * f;
            }
            double keep = airborne ? 0.975 : 0.5;
            hx *= keep;
            hz *= keep;
            double h = Math.sqrt(hx * hx + hz * hz);
            if (h > 1.1) {
                hx *= 1.1 / h;
                hz *= 1.1 / h;
            }
            if (reentryTicks > 0) {
                reentryTicks--;
                hx *= 0.97;
                hz *= 0.97;
                vy = Math.max(vy, -0.9);
            }
            v = new Vec3(hx, vy, hz);
            if (reentryTicks > 0) setState(STATE_REENTRY);
            else if (airborne) setState(STATE_FLYING);
            else setState(dockedAt != null ? STATE_DOCKED : STATE_LANDED);
        }
        lastVy = v.y;
        setDeltaMovement(v);
        setAux((float) v.y);
    }

    @Override
    protected void afterMove() {
        if (!(level() instanceof ServerLevel server)) return;
        // hard landing without fuel
        if (grounded() && lastVy < -1.0) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.8f, 1.3f);
            setDamage(getDamage() + 25);
            setHurtTime(10);
            if (getDamage() > 40) destroy(server, damageSources().fall());
            return;
        }
        pourFuelSlot();
        dock(server);
        ShipMath.Transfer t = ShipMath.decide(realm(), getY(), getDeltaMovement().y,
                OrbitTransfer.overworldTop(server.getServer()), ShipConfig.thresholds());
        if (t != ShipMath.Transfer.NONE) {
            Entity moved = OrbitTransfer.transfer(this, t);
            if (moved == null && t == ShipMath.Transfer.TO_ORBIT) {
                // no orbit here: the sky has a ceiling
                Vec3 v = getDeltaMovement();
                setDeltaMovement(v.x, Math.min(0, v.y), v.z);
            }
        }
    }

    /** Rocket Fuel in the cockpit's fuel slot goes straight into the tank. */
    private void pourFuelSlot() {
        ItemStack stack = items.get(FUEL_SLOT);
        int per = ShipConfig.fuelPerItem();
        while (!stack.isEmpty() && acceptsFuel(stack) && fuel + per <= fuelCapacity()) {
            stack.shrink(1);
            fuel += per;
        }
    }

    /** On a Launch Pad: centre on the pad and refuel from containers touching it. */
    private void dock(ServerLevel level) {
        dockedAt = null;
        if (!grounded()) return;
        BlockPos below = BlockPos.containing(getX(), getY() - 0.15, getZ());
        Block block = level.getBlockState(below).getBlock();
        if (!(block instanceof LaunchPadBlock) && !(block instanceof LaunchControllerBlock)) return;
        BlockPos controller = null;
        for (BlockPos p : BlockPos.betweenClosed(below.offset(-1, 0, -1), below.offset(1, 0, 1))) {
            if (level.getBlockState(p).getBlock() instanceof LaunchControllerBlock) {
                controller = p.immutable();
                break;
            }
        }
        if (controller == null) return;
        dockedAt = controller;
        double cx = controller.getX() + 0.5, cz = controller.getZ() + 0.5;
        double dx = cx - getX(), dz = cz - getZ();
        if (dx * dx + dz * dz > 0.0004) setPos(getX() + dx * 0.25, getY(), getZ() + dz * 0.25);
        float snapped = Math.round(getYRot() / 90f) * 90f;
        if (Math.abs(Mth.wrapDegrees(snapped - getYRot())) > 0.5f && input == 0) setYRot(getYRot() + Mth.wrapDegrees(snapped - getYRot()) * 0.2f);
        if (tickCount % 10 != 0 || fuel + ShipConfig.fuelPerItem() > fuelCapacity()) return;
        // pull one Rocket Fuel from any container touching the 3×3 pad
        ItemResource rocketFuel = ItemResource.of(OrbitalContent.ROCKET_FUEL.get());
        for (BlockPos pad : BlockPos.betweenClosed(controller.offset(-1, 0, -1), controller.offset(1, 0, 1))) {
            for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
                BlockPos at = pad.relative(dir);
                if (Math.abs(at.getX() - controller.getX()) <= 1 && Math.abs(at.getZ() - controller.getZ()) <= 1 && at.getY() == controller.getY()) continue;
                var handler = level.getCapability(Capabilities.Item.BLOCK, at, dir.getOpposite());
                if (handler == null) continue;
                try (Transaction tx = Transaction.openRoot()) {
                    if (handler.extract(rocketFuel, 1, tx) == 1) {
                        tx.commit();
                        fuel += ShipConfig.fuelPerItem();
                        return;
                    }
                }
            }
        }
    }

    public boolean docked() {
        return state() == STATE_DOCKED;
    }

    // ---------------------------------------------------------------- leaving

    @Override
    public boolean canDismountBySneaking() {
        int s = state();
        return s == STATE_LANDED || s == STATE_DOCKED;
    }

    @Override
    protected void hatch(Player player) {
        int s = state();
        boolean stopped = getDeltaMovement().length() < 0.35;
        if (s == STATE_LANDED || s == STATE_DOCKED || (s == STATE_ORBIT && stopped)) {
            releasePassenger(player);
        } else {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.shuttle.hatch_locked").withStyle(ChatFormatting.YELLOW));
        }
    }

    // ---------------------------------------------------------------- effects

    @Override
    protected void animate() {
        int in = syncedInput();
        int s = state();
        boolean fuelled = syncedFuel() > 0;
        float main = fuelled && (in & IN_FORWARD) != 0 ? 1f : 0f;
        float up = !fuelled ? 0f : (in & IN_UP) != 0 ? 1f : s == STATE_FLYING || s == STATE_REENTRY ? 0.5f
                : s == STATE_ORBIT && (in & IN_DOWN) != 0 ? 0.35f : 0f;
        thrust += (main - thrust) * 0.25f;
        lift += (up - lift) * 0.25f;
        boolean nearGround = false;
        for (int dy = 0; dy < 4 && !nearGround; dy++) {
            nearGround = !level().getBlockState(BlockPos.containing(getX(), getY() - 0.5 - dy, getZ())).isAir();
        }
        float stowed = (s == STATE_ORBIT || ((s == STATE_FLYING || s == STATE_REENTRY) && !nearGround)) ? 1f : 0f;
        legs += Math.signum(stowed - legs) * Math.min(Math.abs(stowed - legs), 0.05f);
    }

    @Override
    protected void clientEffects() {
        int in = syncedInput();
        int s = state();
        boolean fuelled = syncedFuel() > 0;
        Vec3 fwd = forward();
        Vec3 side = new Vec3(fwd.z, 0, -fwd.x);
        if (fuelled && (in & (IN_FORWARD)) != 0) {
            for (int sx : new int[] {1, -1}) {
                Vec3 p = position().add(fwd.scale(-47 / 16.0)).add(side.scale(sx * 6.5 / 16)).add(0, 1, 0);
                level().addParticle(ParticleTypes.FLAME, p.x, p.y, p.z, -fwd.x * 0.3, 0, -fwd.z * 0.3);
                if (random.nextBoolean()) level().addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, -fwd.x * 0.2, 0.02, -fwd.z * 0.2);
            }
        }
        boolean vtol = fuelled && s != STATE_ORBIT && ((in & IN_UP) != 0 || s == STATE_FLYING || s == STATE_REENTRY);
        if (vtol || (fuelled && s == STATE_ORBIT && (in & (IN_UP | IN_DOWN)) != 0)) {
            for (double[] pod : new double[][] {{-24, -14}, {24, -14}, {-9, 30}, {9, 30}}) {
                Vec3 p = position().add(side.scale(pod[0] / 16)).add(fwd.scale(pod[1] / 16));
                if (random.nextFloat() < 0.5f) level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.x, getY() - 0.2, p.z, 0, -0.25, 0);
            }
            // dust kicked up from the ground below
            if (s != STATE_ORBIT) {
                for (int dy = 0; dy < 8; dy++) {
                    BlockPos b = BlockPos.containing(getX(), getY() - dy - 0.5, getZ());
                    if (!level().getBlockState(b).isAir()) {
                        for (int i = 0; i < 3; i++) {
                            double a = random.nextDouble() * Math.PI * 2;
                            level().addParticle(ParticleTypes.CLOUD, getX() + Math.cos(a) * 1.5, b.getY() + 1.1, getZ() + Math.sin(a) * 1.5,
                                    Math.cos(a) * 0.3, 0.02, Math.sin(a) * 0.3);
                        }
                        break;
                    }
                }
            }
        }
        if (s == STATE_REENTRY) {
            for (int i = 0; i < 6; i++) {
                Vec3 p = position().add(fwd.scale((random.nextDouble() - 0.3) * 5)).add(side.scale((random.nextDouble() - 0.5) * 4));
                level().addParticle(random.nextInt(3) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, p.x, getY() + random.nextDouble() * 0.6,
                        p.z, (random.nextDouble() - 0.5) * 0.2, 0.3 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.2);
            }
            level().addParticle(ParticleTypes.LARGE_SMOKE, getX(), getY() + 2.5, getZ(), 0, 0.5, 0);
        }
    }

    // ---------------------------------------------------------------- save

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("reentry", reentryTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        reentryTicks = input.getIntOr("reentry", 0);
    }
}
