package net.juli2kapo.factoryascent.ships;

import net.juli2kapo.factoryascent.orbital.LaunchControllerBlock;
import net.juli2kapo.factoryascent.orbital.LaunchPadBlock;
import net.juli2kapo.factoryascent.orbital.OrbitalContent;
import net.juli2kapo.factoryascent.space.SealedCabin;
import net.juli2kapo.factoryascent.space.planet.IonDriveItem;
import net.juli2kapo.factoryascent.space.planet.Navigation;
import net.juli2kapo.factoryascent.space.planet.Planet;
import net.juli2kapo.factoryascent.space.planet.PlanetContent;
import net.juli2kapo.factoryascent.space.station.DockingPortBlock;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
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
 * rule. The cabin is sealed ({@link SealedCabin}: no suit needed aboard) and heat-shielded.
 *
 * <p>Leaving: Shift only works once landed; in flight the hatch key (K) opens the hatch on the
 * ground or, in orbit, when nearly stopped (an EVA: the space rules apply outside).
 *
 * <p>Navigation: in Earth orbit the cockpit's Navigation panel picks a destination (Earth orbit,
 * the Moon, Mars, Io, see {@link Navigation}) and Engage burns the trip's fuel and starts a cruise
 * (a short warp through the stars, everyone aboard). The shuttle then appears high over the target
 * planet ({@link Planet#ARRIVAL_Y}) and falls under the planet's gravity for the pilot to land.
 * Climbing back through a planet's orbit line ({@link Planet#ORBIT_LINE}) cruises on to the selected
 * destination (another planet), or home to Earth orbit. An Ion Drive in the hold halves fuel and
 * time. On a Launch Pad or a Docking Port (in orbit too: ease down onto it) it docks and refuels
 * from containers touching the pad or port.
 */
public class Shuttle extends AbstractShip implements SealedCabin {
    public static final int STATE_LANDED = 0, STATE_DOCKED = 1, STATE_FLYING = 2, STATE_ORBIT = 3, STATE_REENTRY = 4, STATE_CRUISE = 5;
    /** Navigation, synced: selected destination (bits 0-3), cruise target (4-7), cruise progress in percent (8-15). */
    private static final EntityDataAccessor<Integer> DATA_NAV = SynchedEntityData.defineId(Shuttle.class, EntityDataSerializers.INT);
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
    /** Navigation: the selected destination, and the cruise under way (ticks left of total, towards the target). */
    private int selected;
    private int cruiseLeft, cruiseTotal, cruiseTarget = -1;
    private boolean arriveNow;

    public Shuttle(EntityType<? extends Shuttle> type, Level level) {
        super(type, level);
    }

    /**
     * Whether an entity sits in a shuttle's sealed cabin (heat-shielded; for air the space rules
     * see the cabin through {@link SealedCabin}).
     */
    public static boolean carries(Entity entity) {
        return entity.getVehicle() instanceof Shuttle;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NAV, 0);
    }

    // ---------------------------------------------------------------- navigation

    /** The destination picked in the Navigation panel. */
    public Navigation.Destination selected() {
        return Navigation.Destination.byIndex(level().isClientSide() ? entityData.get(DATA_NAV) & 0xF : selected);
    }

    /** Where the shuttle is in space terms (Earth orbit or a planet), or null (the Overworld...). */
    public Navigation.@Nullable Destination here() {
        return Navigation.Destination.at(level().dimension());
    }

    public boolean cruising() {
        return level().isClientSide() ? state() == STATE_CRUISE : cruiseLeft > 0;
    }

    /** Where the cruise under way goes (client and server). */
    public Navigation.@Nullable Destination cruiseTarget() {
        int t = level().isClientSide() ? (entityData.get(DATA_NAV) >> 4) & 0xF : cruiseTarget;
        return cruising() && t < Navigation.Destination.values().length ? Navigation.Destination.byIndex(t) : null;
    }

    /** Progress 0..1 of the cruise under way. */
    public float cruiseProgress() {
        if (level().isClientSide()) return ((entityData.get(DATA_NAV) >> 8) & 0xFF) / 100f;
        return Navigation.progress(cruiseLeft, cruiseTotal);
    }

    /** An Ion Drive rides in the hold. */
    public boolean ionDrive() {
        for (int i = 0; i < cargoSize(); i++) {
            if (items.get(i).getItem() instanceof IonDriveItem) return true;
        }
        return false;
    }

    public void select(int index) {
        selected = Navigation.Destination.byIndex(index).ordinal();
        syncNav();
    }

    private void syncNav() {
        int percent = cruiseLeft > 0 ? Math.round(Navigation.progress(cruiseLeft, cruiseTotal) * 100) : 0;
        int target = cruiseTarget < 0 ? 0xF : cruiseTarget;
        entityData.set(DATA_NAV, (selected & 0xF) | (target & 0xF) << 4 | (percent & 0xFF) << 8);
    }

    /**
     * The Engage button: starts the cruise to the selected destination if the shuttle is in Earth
     * orbit with the fuel for it. Returns null when it started, otherwise what to tell the player.
     */
    public net.minecraft.network.chat.@Nullable Component engage(Player player) {
        if (player.getVehicle() != this) return Component.translatable("message.factoryascent.nav.board_first");
        Navigation.Destination to = selected();
        Navigation.Destination here = here();
        var costs = ShipConfig.navCosts();
        if (realm() == ShipMath.Realm.PLANET && here != null) {
            if (to == here) return Component.translatable(Navigation.Check.SAME_PLACE.key());
            return Component.translatable("message.factoryascent.nav.climb", Planet.ORBIT_LINE, to.displayName());
        }
        Navigation.Check check = Navigation.check(realm() == ShipMath.Realm.ORBIT ? here : null, to, fuel, cruising(), costs, ionDrive());
        if (check != Navigation.Check.OK) {
            return Component.translatable(check.key(), here == null ? 0 : Navigation.fuelCost(here, to, costs, ionDrive()));
        }
        startCruise(to);
        return null;
    }

    private void startCruise(Navigation.Destination to) {
        Navigation.Destination from = here();
        if (from == null) return;
        var costs = ShipConfig.navCosts();
        boolean ion = ionDrive();
        fuel = Math.max(0, fuel - Navigation.fuelCost(from, to, costs, ion));
        cruiseTotal = Navigation.travelTicks(from, to, costs, ion);
        cruiseLeft = cruiseTotal;
        cruiseTarget = to.ordinal();
        setDeltaMovement(Vec3.ZERO);
        setState(STATE_CRUISE);
        syncNav();
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 2f, 0.5f);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.NEUTRAL, 0.6f, 1.6f);
        for (Entity p : getPassengers()) {
            if (p instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.factoryascent.nav.cruise", to.displayName(), (cruiseTotal + 19) / 20)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    private void cruiseTick() {
        cruiseLeft--;
        setDeltaMovement(Vec3.ZERO);
        setState(STATE_CRUISE);
        setAux(cruiseProgress());
        if (cruiseLeft <= 0) {
            cruiseLeft = 0;
            arriveNow = true;
        }
        syncNav();
    }

    /** End of the cruise: over the target planet (falling towards it) or back in Earth orbit. */
    private void arrive(ServerLevel from) {
        Navigation.Destination to = Navigation.Destination.byIndex(Math.max(0, cruiseTarget));
        cruiseTarget = -1;
        cruiseTotal = 0;
        syncNav();
        ServerLevel target = from.getServer().getLevel(to.dimension());
        if (target == null) {
            for (Entity p : getPassengers()) {
                if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("message.factoryascent.nav.unavailable", to.displayName())
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        boolean planet = to.planet != null;
        double y = planet ? Planet.ARRIVAL_Y : ShipConfig.thresholds().arrivalInOrbit();
        Vec3 arrive = planet ? new Vec3(0, -0.35, 0) : Vec3.ZERO;
        OrbitTransfer.travel(this, target, new Vec3(getX(), y, getZ()), arrive, "message.factoryascent.nav.arrived", to.displayName());
    }

    /** Climbing out of a planet's sky: on to the selected destination, home to Earth orbit, or (no fuel) nowhere. */
    private void leavePlanet() {
        Navigation.Destination here = here();
        if (here == null) return;
        Navigation.Destination to = Navigation.leavingPlanet(here, selected(), fuel, ShipConfig.navCosts(), ionDrive());
        if (to == null) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Math.min(0, v.y), v.z);
            if (pilot() != null && tickCount % 40 == 0) {
                pilot().sendOverlayMessage(Component.translatable("message.factoryascent.nav.stranded",
                        Navigation.fuelCost(here, Navigation.Destination.EARTH_ORBIT, ShipConfig.navCosts(), ionDrive()))
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        startCruise(to);
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
        return PlanetContent.shuttleFuelValue(stack, 1) > 0;
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
        if (cruiseLeft > 0) {
            cruiseTick();
            return;
        }
        boolean orbit = realm() == ShipMath.Realm.ORBIT;
        Planet planet = Planet.of(level());
        double gravity = planet == null ? GRAVITY : GRAVITY * planet.gravity();
        boolean airborne = !grounded();
        boolean up = pressed(IN_UP);
        // nobody at the controls in the air: the autopilot sets it down gently
        boolean down = pressed(IN_DOWN) || (input == 0 && pilot() == null && airborne && !orbit);
        boolean ahead = pressed(IN_FORWARD), back = pressed(IN_BACK);
        boolean horizontal = ahead || back;
        int cost = planet != null ? ShipMath.planetFuelPerTick(airborne, up, down, horizontal, ShipConfig.fuelUse(), planet.gravity())
                : ShipMath.shuttleFuelPerTick(orbit, airborne, up, down, horizontal, ShipConfig.fuelUse());
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
            // drifting momentum; with the pilot off the thrusters the RCS slowly holds station
            boolean coasting = !(ahead || back || up || down);
            v = v.add(ax, ay, az).scale(coasting && powered && pilot() != null ? 0.988 : 0.996);
            if (v.length() > 1.6) v = v.normalize().scale(1.6);
            reentryTicks = 0;
            if (dockedAt != null && !up && !horizontal) {
                v = new Vec3(0, -0.02, 0); // held down on the port by its clamps
                setState(STATE_DOCKED);
            } else {
                setState(STATE_ORBIT);
            }
        } else {
            double vy = v.y;
            if (powered && up) {
                vy += 0.085;
            } else if (powered && down && airborne) {
                vy += gravity * 0.55;
                vy = Math.max(vy, -0.6);
            } else if (powered && airborne) {
                vy += gravity; // hover
                vy *= 0.85;
            }
            vy = (vy - gravity) * 0.98;
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
        if (arriveNow) {
            arriveNow = false;
            arrive(server);
            return;
        }
        if (cruiseLeft > 0) return;
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
        if (realm() == ShipMath.Realm.PLANET) {
            if (ShipMath.leavesPlanet(getY(), getDeltaMovement().y, Planet.ORBIT_LINE)) leavePlanet();
            return;
        }
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
        int per = PlanetContent.shuttleFuelValue(stack, ShipConfig.fuelPerItem());
        while (!stack.isEmpty() && per > 0 && fuel + per <= fuelCapacity()) {
            stack.shrink(1);
            fuel += per;
        }
    }

    /**
     * On a Launch Pad or a Docking Port: centre on it and refuel from containers touching the pad
     * (or the port).
     */
    private void dock(ServerLevel level) {
        dockedAt = null;
        if (!grounded()) return;
        BlockPos below = BlockPos.containing(getX(), getY() - 0.15, getZ());
        Block block = level.getBlockState(below).getBlock();
        BlockPos controller = null;
        boolean pad = block instanceof LaunchPadBlock || block instanceof LaunchControllerBlock;
        for (BlockPos p : BlockPos.betweenClosed(below.offset(-1, 0, -1), below.offset(1, 0, 1))) {
            Block b = level.getBlockState(p).getBlock();
            if (pad && b instanceof LaunchControllerBlock) {
                controller = p.immutable();
                break;
            }
            if (!pad && b instanceof DockingPortBlock && (controller == null || p.equals(below))) controller = p.immutable();
        }
        if (controller == null) return;
        dockedAt = controller;
        double cx = controller.getX() + 0.5, cz = controller.getZ() + 0.5;
        double dx = cx - getX(), dz = cz - getZ();
        if (dx * dx + dz * dz > 0.0004) setPos(getX() + dx * 0.25, getY(), getZ() + dz * 0.25);
        float snapped = Math.round(getYRot() / 90f) * 90f;
        if (Math.abs(Mth.wrapDegrees(snapped - getYRot())) > 0.5f && input == 0) setYRot(getYRot() + Mth.wrapDegrees(snapped - getYRot()) * 0.2f);
        if (tickCount % 10 != 0 || fuel + ShipConfig.fuelPerItem() > fuelCapacity()) return;
        // pull one Rocket Fuel from any container touching the 3×3 pad (or the port)
        ItemResource rocketFuel = ItemResource.of(OrbitalContent.ROCKET_FUEL.get());
        int reach = pad ? 1 : 0;
        for (BlockPos source : BlockPos.betweenClosed(controller.offset(-reach, 0, -reach), controller.offset(reach, 0, reach))) {
            for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
                BlockPos at = source.relative(dir);
                if (Math.abs(at.getX() - controller.getX()) <= reach && Math.abs(at.getZ() - controller.getZ()) <= reach
                        && at.getY() == controller.getY()) continue;
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
        float stowed = (s == STATE_ORBIT || s == STATE_CRUISE || ((s == STATE_FLYING || s == STATE_REENTRY) && !nearGround)) ? 1f : 0f;
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
        output.putInt("nav_selected", selected);
        output.putInt("cruise_left", cruiseLeft);
        output.putInt("cruise_total", cruiseTotal);
        output.putInt("cruise_target", cruiseTarget);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        reentryTicks = input.getIntOr("reentry", 0);
        selected = input.getIntOr("nav_selected", 0);
        cruiseLeft = input.getIntOr("cruise_left", 0);
        cruiseTotal = input.getIntOr("cruise_total", 0);
        cruiseTarget = input.getIntOr("cruise_target", -1);
        syncNav();
    }
}
