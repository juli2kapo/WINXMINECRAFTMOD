package net.juli2kapo.factoryascent.orbital;

import java.util.UUID;
import net.juli2kapo.factoryascent.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The Launch Pad's controller: the mounted payload (a satellite or an Anti-Satellite missile) and
 * who mounted it, the fuel tank, and the launch sequence.
 *
 * <p>Hoppers and pipes can feed it through {@link #itemHandler()}: payloads and fuel go in under
 * the same rules as by hand, and an automated payload counts as mounted by the controller's
 * {@link #owner} (whoever placed it). Nothing can be pulled out.
 *
 * <p>Launch sequence ({@link #SEQUENCE} ticks): a countdown with smoke and flames at the base,
 * liftoff at {@link #LIFTOFF}, then the rocket climbs ever faster (drawn by the client renderer
 * from the synced start time; the server puts the exhaust trail at the same height). At the end
 * the satellite is added to the launcher's team in {@link OrbitRegistry} and the team is told; a
 * missile instead strikes its target (see {@link #strike}).
 */
public class LaunchControllerBlockEntity extends BlockEntity {
    public static final int FUEL_PER_LAUNCH = 4;
    public static final int FUEL_MAX = 8;
    public static final int LIFTOFF = 60;
    public static final int SEQUENCE = 100;
    /** Rocket height above the pad after liftoff: {@code ACCEL * t²} blocks, t in ticks since liftoff. */
    public static final float ACCEL = 0.075f;

    /** The mounted payload: a {@link SatelliteItem} or an {@link AsatMissileItem}. */
    private ItemStack satellite = ItemStack.EMPTY;
    private @Nullable UUID launcher;
    /** Who placed the controller: the launcher of payloads that arrive by hopper or pipe. */
    private @Nullable UUID owner;
    private int fuel;
    /** Ticks into the launch sequence, or -1 when idle. */
    private int launchTick = -1;
    /** Game time the sequence started (synced for the renderer), or -1. */
    private long launchStart = -1;
    /** The pad is on the survey map's list of sites (not saved: re-added once per load). */
    private boolean siteKnown;

    public LaunchControllerBlockEntity(BlockPos pos, BlockState state) {
        super(OrbitalContent.LAUNCH_CONTROLLER_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- state

    public ItemStack satellite() {
        return satellite;
    }

    public @Nullable SatelliteType satelliteType() {
        return satellite.getItem() instanceof SatelliteItem item ? item.type() : null;
    }

    /** An Anti-Satellite missile is mounted. */
    public boolean hasMissile() {
        return satellite.getItem() instanceof AsatMissileItem;
    }

    /** Can this item ride the rocket? */
    public static boolean isPayload(ItemStack stack) {
        return stack.getItem() instanceof SatelliteItem || stack.getItem() instanceof AsatMissileItem
                || net.juli2kapo.factoryascent.space.CrewLaunch.isCapsule(stack) // [space hook] crew capsules ride too
                || net.juli2kapo.factoryascent.dyson.DysonLaunch.isCollector(stack) // [dyson hook] a Solar Collector for the swarm
                || net.juli2kapo.factoryascent.stationkit.StationKits.isPayload(stack); // [station kit hook] Station Kit, Cargo Pod
    }

    /** [space hook] Fuel units the mounted payload needs (a crew capsule may need more). */
    public int fuelCost() {
        return net.juli2kapo.factoryascent.space.CrewLaunch.fuelCost(satellite, FUEL_PER_LAUNCH);
    }

    /** [space hook] Length of this launch's sequence (crewed launches climb longer). */
    public int sequence() {
        return net.juli2kapo.factoryascent.space.CrewLaunch.sequence(satellite, SEQUENCE);
    }

    /**
     * [space hook] Scrubs a launch still in its countdown (the crew climbed out): the rocket stays
     * on the pad with its payload and the fuel goes back into the tank.
     */
    public void abortLaunch() {
        if (!launching() || launchTick >= LIFTOFF) return;
        fuel = Math.min(FUEL_MAX, fuel + fuelCost());
        launchTick = -1;
        launchStart = -1;
        changed();
    }

    public @Nullable UUID owner() {
        return owner;
    }

    /** Who mounted the payload (null: nobody known). */
    public @Nullable UUID launcher() {
        return launcher;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        siteKnown = false;
        setChanged();
    }

    public int fuel() {
        return fuel;
    }

    public boolean launching() {
        return launchTick >= 0;
    }

    public long launchStart() {
        return launchStart;
    }

    /** Fuel units an item is worth: Blaze Powder 1, Rocket Fuel 4. */
    public static int fuelValue(ItemStack stack) {
        if (stack.is(Items.BLAZE_POWDER)) return 1;
        if (stack.is(OrbitalContent.ROCKET_FUEL.get())) return FUEL_PER_LAUNCH;
        return 0;
    }

    /** All eight plates around the controller are Launch Pad blocks. */
    public boolean isFormed() {
        if (level == null) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !(level.getBlockState(worldPosition.offset(dx, 0, dz)).getBlock() instanceof LaunchPadBlock)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- actions

    /** Null if a payload could be mounted now, else why not (the rules for hands and hoppers alike). */
    public @Nullable Component mountProblem() {
        if (launching()) return Component.translatable("message.factoryascent.pad_busy").withStyle(ChatFormatting.RED);
        if (!satellite.isEmpty()) return Component.translatable("message.factoryascent.pad_occupied").withStyle(ChatFormatting.RED);
        if (!isFormed()) return Component.translatable("message.factoryascent.pad_incomplete").withStyle(ChatFormatting.RED);
        return null;
    }

    /** Mounts one satellite or missile from the stack; null on success, else why not. The caller shrinks the stack. */
    public @Nullable Component mount(ItemStack stack, UUID player) {
        if (!isPayload(stack)) return Component.translatable("message.factoryascent.pad_empty").withStyle(ChatFormatting.RED);
        Component problem = mountProblem();
        if (problem != null) return problem;
        satellite = stack.copyWithCount(1);
        launcher = player;
        if (owner == null) owner = player;
        changed();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.7f, 0.8f);
        }
        return null;
    }

    /**
     * Mounts a payload put in the controller screen's payload slot (same rules as by hand; the
     * slot only accepts it when {@link #mountProblem()} is null).
     */
    void mountFromMenu(ItemStack stack, UUID player) {
        if (stack.isEmpty()) {
            dismount();
        } else if (isPayload(stack) && (satellite.isEmpty() || !launching())) {
            satellite = stack.copyWithCount(1);
            launcher = player;
            if (owner == null) owner = player;
            changed();
        }
    }

    /** Readiness of the pad, as shown on the controller screen (one of the {@code STATUS_*} constants). */
    public int status() {
        if (launching()) return STATUS_LAUNCHING;
        if (!isFormed()) return STATUS_INCOMPLETE;
        if (satellite.isEmpty()) return STATUS_NO_PAYLOAD;
        if (fuel < fuelCost()) return STATUS_NO_FUEL;
        if (pathBlocked()) return STATUS_BLOCKED;
        if (hasMissile() && level instanceof ServerLevel server && missileProblem(server) != null) return STATUS_MISSILE;
        return STATUS_READY;
    }

    public static final int STATUS_READY = 0, STATUS_LAUNCHING = 1, STATUS_INCOMPLETE = 2, STATUS_NO_PAYLOAD = 3,
            STATUS_NO_FUEL = 4, STATUS_BLOCKED = 5, STATUS_MISSILE = 6;

    /** Ticks into the launch sequence, or -1 when idle. */
    public int launchTick() {
        return launchTick;
    }

    /** Blocks above the controller that must be clear: the launch vehicle stands about 8 blocks tall. */
    private static final int CLEARANCE = 9;

    private boolean pathBlocked() {
        if (level == null) return false;
        for (int y = 1; y <= CLEARANCE; y++) {
            if (!level.getBlockState(worldPosition.above(y)).getCollisionShape(level, worldPosition.above(y)).isEmpty()) return true;
        }
        return false;
    }

    /** Takes the satellite back off the pad (not during a launch). */
    public ItemStack dismount() {
        if (launching() || satellite.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = satellite;
        satellite = ItemStack.EMPTY;
        launcher = null;
        changed();
        return out;
    }

    /** Adds fuel units if the whole amount fits. */
    public boolean addFuel(int units) {
        if (fuel + units > FUEL_MAX) return false;
        fuel += units;
        changed();
        return true;
    }

    /** Starts the launch sequence; null on success, else why not. */
    public @Nullable Component tryLaunch() {
        if (level == null || level.isClientSide()) return null;
        if (launching()) return Component.translatable("message.factoryascent.pad_busy").withStyle(ChatFormatting.RED);
        if (!isFormed()) return Component.translatable("message.factoryascent.pad_incomplete").withStyle(ChatFormatting.RED);
        if (satellite.isEmpty()) return Component.translatable("message.factoryascent.pad_empty").withStyle(ChatFormatting.RED);
        if (fuel < fuelCost()) {
            return Component.translatable("message.factoryascent.pad_no_fuel", fuel, fuelCost()).withStyle(ChatFormatting.RED);
        }
        if (hasMissile()) {
            Component problem = missileProblem((ServerLevel) level);
            if (problem != null) return problem;
        }
        if (pathBlocked()) return Component.translatable("message.factoryascent.pad_blocked").withStyle(ChatFormatting.RED);
        Component crew = net.juli2kapo.factoryascent.space.CrewLaunch.launchProblem(this); // [space hook] capsules need a crew
        if (crew != null) return crew;
        Component kit = net.juli2kapo.factoryascent.stationkit.StationKits.launchProblem(this); // [station kit hook] room in orbit, a station for cargo
        if (kit != null) return kit;
        fuel -= fuelCost();
        launchTick = 0;
        launchStart = level.getGameTime();
        changed();
        level.playSound(null, worldPosition, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1f, 0.8f);
        return null;
    }

    /** Tells the player who mounted the satellite (if online and near): used for redstone launches. */
    void tellLauncher(Component message) {
        if (launcher != null && level instanceof ServerLevel server) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(launcher);
            if (p != null && p.level() == level && p.blockPosition().closerThan(worldPosition, 32)) p.sendOverlayMessage(message);
        }
    }

    public Component statusLine() {
        Component payload = satellite.isEmpty() ? Component.translatable("message.factoryascent.pad_status_none")
                : satellite.getHoverName();
        String key = isFormed() ? "message.factoryascent.pad_status" : "message.factoryascent.pad_incomplete";
        return Component.translatable(key, payload, fuel, FUEL_MAX, fuelCost()); // [space hook] per-payload cost
    }

    // ---------------------------------------------------------------- the launch

    public void serverTick(ServerLevel level) {
        if (!siteKnown && owner != null) {
            SurveySites.get(level.getServer()).put(level.dimension(), worldPosition, SurveySites.PAD, owner);
            siteKnown = true;
        }
        if (launchTick < 0) return;
        if (satellite.isEmpty()) { // e.g. the data was edited; abort quietly
            launchTick = -1;
            launchStart = -1;
            changed();
            return;
        }
        int t = launchTick++;
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.25, z = worldPosition.getZ() + 0.5;
        if (t < LIFTOFF) {
            if (t % 20 == 0) {
                int seconds = (LIFTOFF - t) / 20;
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
                for (ServerPlayer p : level.players()) {
                    if (p.blockPosition().closerThan(worldPosition, 32)) {
                        p.sendOverlayMessage(Component.translatable("message.factoryascent.countdown", seconds).withStyle(ChatFormatting.GOLD));
                    }
                }
            }
            // Venting smoke, thicker as liftoff nears, and a flicker of flame under the engine.
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, x, y + 0.1, z, 1 + t / 20, 0.8, 0.05, 0.8, 0.01);
            if (t > LIFTOFF / 2) level.sendParticles(ParticleTypes.FLAME, x, y + 0.05, z, 3, 0.12, 0.02, 0.12, 0.02);
            if (t % 10 == 5) level.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1f, 0.6f);
        } else if (t == LIFTOFF) {
            level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.5f, 0.5f);
            level.playSound(null, worldPosition, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 3f, 0.5f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, false, x, y + 0.3, z, 60, 1.5, 0.2, 1.5, 0.06);
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, false, x, y + 0.3, z, 20, 1.2, 0.2, 1.2, 0.02);
            level.sendParticles(ParticleTypes.FLAME, true, false, x, y, z, 40, 0.6, 0.1, 0.6, 0.12);
        } else if (t < sequence()) {
            // Exhaust trail at the rocket's current height (same curve the renderer draws).
            float dt = t - LIFTOFF;
            double h = y + ACCEL * dt * dt;
            level.sendParticles(ParticleTypes.FLAME, true, true, x, h, z, 6, 0.1, 0.4, 0.1, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, x, h - 0.5, z, 4, 0.2, 0.6, 0.2, 0.01);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, x, y + 0.2, z, 2, 1.2, 0.05, 1.2, 0.01);
            if (t % 8 == 0) level.playSound(null, x, h, z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 2f, 0.4f);
        } else {
            reachOrbit(level);
        }
    }

    /**
     * Null if the mounted missile may fly now, else why not: missiles must be allowed on the
     * server, programmed by a radar of the launcher's team in this dimension, and aimed at a
     * satellite still over this dimension: another team's (the radar must still hold its lock) or
     * the launcher's own team's (no lock needed: teams may shoot down their own satellites).
     */
    public @Nullable Component missileProblem(ServerLevel level) {
        if (!Config.ASAT_ENABLED.get()) return Component.translatable("message.factoryascent.asat_disabled").withStyle(ChatFormatting.RED);
        AsatMissileItem.Target target = AsatMissileItem.target(satellite);
        if (target == null) return Component.translatable("message.factoryascent.asat_unprogrammed").withStyle(ChatFormatting.RED);
        MinecraftServer server = level.getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        String team = teams.teamOf(launcher != null ? launcher : new UUID(0, 0));
        var found = OrbitRegistry.get(server).find(target.satellite());
        if (found.isEmpty() || !found.get().satellite().dimension().equals(level.dimension())) {
            return Component.translatable("message.factoryascent.asat_target_gone").withStyle(ChatFormatting.RED);
        }
        boolean own = found.get().team().equals(team);
        BlockPos radarPos = target.radar().pos();
        if (!target.radar().dimension().equals(level.dimension()) || !level.isLoaded(radarPos)
                || !(level.getBlockEntity(radarPos) instanceof OrbitalRadarBlockEntity radar)
                || !(own || radar.isLocked(target.satellite()))) {
            return Component.translatable("message.factoryascent.asat_no_lock").withStyle(ChatFormatting.RED);
        }
        if (radar.owner() == null || !teams.teamOf(radar.owner()).equals(team)) {
            return Component.translatable("message.factoryascent.asat_foreign_radar").withStyle(ChatFormatting.RED);
        }
        return null;
    }

    /** The payload arrives: a satellite joins the launcher's team's orbit over this dimension, a missile strikes. */
    private void reachOrbit(ServerLevel level) {
        MinecraftServer server = level.getServer();
        SatelliteType type = satelliteType();
        UUID who = launcher != null ? launcher : new UUID(0, 0);
        if (hasMissile()) strike(level, who);
        if (net.juli2kapo.factoryascent.space.CrewLaunch.isCapsule(satellite)) net.juli2kapo.factoryascent.space.CrewLaunch.arrive(level, this); // [space hook]
        if (net.juli2kapo.factoryascent.dyson.DysonLaunch.isCollector(satellite)) net.juli2kapo.factoryascent.dyson.DysonLaunch.arrive(level, who); // [dyson hook]
        if (net.juli2kapo.factoryascent.stationkit.StationKits.isPayload(satellite)) net.juli2kapo.factoryascent.stationkit.StationKits.arrive(level, this, who); // [station kit hook]
        if (type != null) {
            FactoryTeams teams = FactoryTeams.get(server);
            String team = teams.teamOf(who);
            OrbitRegistry orbit = OrbitRegistry.get(server);
            Component custom = satellite.get(DataComponents.CUSTOM_NAME);
            String name = custom != null ? custom.getString()
                    : type.shortName().getString() + "-" + (orbit.count(team, type) + 1);
            Satellite sat = new Satellite(type, level.dimension(), server.overworld().getGameTime(), name, who);
            orbit.add(team, sat);
            OrbitalText.tellTeam(server, team, Component.translatable("message.factoryascent.reached_orbit",
                    type.displayName(), name, OrbitalText.dimensionName(level.dimension())).withStyle(ChatFormatting.AQUA));
            OrbitalContent.award(server, teams.members(team), "orbital_launch");
            if (type == SatelliteType.UPLINK) OrbitalContent.award(server, teams.members(team), "orbital_uplink");
        }
        satellite = ItemStack.EMPTY;
        launcher = null;
        launchTick = -1;
        launchStart = -1;
        changed();
    }

    /**
     * The missile reaches its target. A Guardian Satellite of the target's team over this
     * dimension intercepts it and is used up; otherwise the target is destroyed. Both teams are told.
     * A missile aimed at the launcher's own team's satellite is never intercepted by that team's
     * Guardians: it destroys the target and the team is told it shot down its own satellite.
     */
    private void strike(ServerLevel level, UUID who) {
        MinecraftServer server = level.getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        OrbitRegistry orbit = OrbitRegistry.get(server);
        String team = teams.teamOf(who);
        AsatMissileItem.Target target = AsatMissileItem.target(satellite);
        var found = target == null ? java.util.Optional.<OrbitRegistry.Owned>empty() : orbit.find(target.satellite());
        if (found.isEmpty() || !found.get().satellite().dimension().equals(level.dimension())) {
            OrbitalText.tellTeam(server, team, Component.translatable("message.factoryascent.asat_missed").withStyle(ChatFormatting.GRAY));
            return;
        }
        String victims = found.get().team();
        Satellite victim = found.get().satellite();
        if (victims.equals(team)) {
            orbit.remove(victim.id());
            OrbitalText.tellTeam(server, team, Component.translatable("message.factoryascent.asat_destroyed_own",
                    teams.playerName(who), victim.type().displayName(), victim.name()).withStyle(ChatFormatting.YELLOW));
            return;
        }
        String shooter = teams.displayName(team), shooterPlayer = teams.playerName(who);
        var guardian = orbit.first(victims, level.dimension(), SatelliteType.DEFENSE);
        if (guardian.isPresent()) {
            orbit.remove(guardian.get().id());
            OrbitalText.tellTeam(server, victims, Component.translatable("message.factoryascent.asat_intercepted_owner",
                    guardian.get().name(), shooter, victim.name()).withStyle(ChatFormatting.GOLD));
            OrbitalText.tellTeam(server, team, Component.translatable("message.factoryascent.asat_intercepted_shooter",
                    victim.name(), teams.displayName(victims)).withStyle(ChatFormatting.YELLOW));
            return;
        }
        orbit.remove(victim.id());
        OrbitalText.tellTeam(server, victims, Component.translatable("message.factoryascent.asat_destroyed_owner",
                victim.type().displayName(), victim.name(), shooter, shooterPlayer).withStyle(ChatFormatting.RED));
        OrbitalText.tellTeam(server, team, Component.translatable("message.factoryascent.asat_destroyed_shooter",
                victim.type().displayName(), victim.name(), teams.displayName(victims)).withStyle(ChatFormatting.GREEN));
        OrbitalContent.award(server, teams.members(team), "orbital_shootdown");
    }

    // ---------------------------------------------------------------- automation

    private final Journal journal = new Journal();
    private final PadItems items = new PadItems();

    /** What hoppers and pipes see (any side). */
    public ResourceHandler<ItemResource> itemHandler() {
        return items;
    }

    private record Snapshot(ItemStack satellite, @Nullable UUID launcher, int fuel) {}

    private final class Journal extends SnapshotJournal<Snapshot> {
        @Override
        protected Snapshot createSnapshot() {
            return new Snapshot(satellite, launcher, fuel);
        }

        @Override
        protected void revertToSnapshot(Snapshot snapshot) {
            satellite = snapshot.satellite();
            launcher = snapshot.launcher();
            fuel = snapshot.fuel();
        }

        @Override
        protected void onRootCommit(Snapshot originalState) {
            changed();
        }
    }

    /**
     * Insert-only view for automation. Slot 0 is the payload (one satellite or missile, only while
     * the pad could take one by hand, and only once the controller has an owner); slot 1 is the fuel
     * tank, which takes Blaze Powder or Rocket Fuel while whole items fit; slot 2 loads anything
     * else into a mounted Cargo Pod (until it launches).
     */
    private final class PadItems implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return 3; // [station kit hook] slot 2: cargo for a mounted Cargo Pod
        }

        @Override
        public ItemResource getResource(int index) {
            return index == 0 ? ItemResource.of(satellite) : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return index == 0 ? satellite.getCount() : 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return index == 0 ? 1 : index == 1 ? FUEL_MAX : 64;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            ItemStack stack = resource.toStack(1);
            if (index == 2) return net.juli2kapo.factoryascent.stationkit.StationKits.isPod(satellite) && !launching()
                    && net.juli2kapo.factoryascent.stationkit.CargoPodItem.accepts(stack);
            return index == 0 ? isPayload(stack) : fuelValue(stack) > 0;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || !isValid(index, resource)) return 0;
            if (index == 2) { // [station kit hook] load the mounted Cargo Pod
                var contents = net.juli2kapo.factoryascent.stationkit.CargoPodItem.contents(satellite);
                int fits = Math.min(amount, net.juli2kapo.factoryascent.stationkit.CargoPodItem.room(contents, resource.toStack(1)));
                if (fits <= 0) return 0;
                net.juli2kapo.factoryascent.stationkit.CargoPodItem.add(contents, resource.toStack(1), fits);
                journal.updateSnapshots(tx);
                ItemStack loaded = satellite.copy();
                net.juli2kapo.factoryascent.stationkit.CargoPodItem.setContents(loaded, contents);
                satellite = loaded;
                return fits;
            }
            if (index == 0) {
                if (owner == null || mountProblem() != null) return 0;
                journal.updateSnapshots(tx);
                satellite = resource.toStack(1);
                launcher = owner;
                return 1;
            }
            int units = fuelValue(resource.toStack(1));
            int fits = Math.min(amount, (FUEL_MAX - fuel) / units);
            if (fits <= 0) return 0;
            journal.updateSnapshots(tx);
            fuel += fits * units;
            return fits;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- save / sync

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Drops the mounted payload (and nothing else: fuel burns away) when the controller is broken.
     * Breaking it during the countdown or the climb aborts the launch and the payload still drops.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide() && net.juli2kapo.factoryascent.space.CrewLaunch.isCapsule(satellite)) {
            net.juli2kapo.factoryascent.space.CrewLaunch.abort(level, pos); // [space hook] crew out, gently
        }
        if (level != null && !satellite.isEmpty()) Block.popResource(level, pos, satellite.copy());
        satellite = ItemStack.EMPTY;
        if (level instanceof ServerLevel server) SurveySites.get(server.getServer()).remove(server.dimension(), pos);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        satellite = input.read("satellite", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        launcher = input.read("launcher", UUIDUtil.CODEC).orElse(null);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        fuel = input.getIntOr("fuel", 0);
        launchTick = input.getIntOr("launch_tick", -1);
        launchStart = input.getLongOr("launch_start", -1L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!satellite.isEmpty()) output.store("satellite", ItemStack.OPTIONAL_CODEC, satellite);
        if (launcher != null) output.store("launcher", UUIDUtil.CODEC, launcher);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
        output.putInt("fuel", fuel);
        output.putInt("launch_tick", launchTick);
        output.putLong("launch_start", launchStart);
    }
}
