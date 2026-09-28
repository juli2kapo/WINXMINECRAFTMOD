package net.juli2kapo.factoryascent.orbital;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.RadarAction;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.RadarView;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.Row;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * The Orbital Radar: sees every satellite over its dimension, its owner team's and everyone
 * else's. Foreign satellites show up as unidentified contacts; tracking one for
 * {@link Config#RADAR_LOCK_SECONDS} seconds of powered operation ({@link #TRACK_DRAIN} FE/t)
 * locks it: its type, name and team are revealed, its owners are warned, and it can be picked as
 * the target of an {@link AsatMissileItem}.
 *
 * <p>The team's own satellites need no lock: they are listed by name and can be picked as the
 * target straight away, so a team can shoot down its own satellites (its own Guardians don't
 * intercept its own missiles).
 *
 * <p>The radar belongs to the team of whoever placed it ({@link #owner}): only that team's members
 * can use it. Locks last until the satellite leaves orbit or the radar is broken.
 */
public class OrbitalRadarBlockEntity extends BlockEntity {
    public static final int CAPACITY = 40_000;
    public static final int MAX_INSERT = 512;
    /** FE per tick while tracking a contact. */
    public static final int TRACK_DRAIN = 48;
    /** How far (blocks) a player may be from the radar to use its screen. */
    private static final double USE_RANGE = 8;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, MAX_INSERT, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    };
    private @Nullable UUID owner;
    /** The contact being tracked, or null. */
    private @Nullable UUID tracking;
    private int trackTicks;
    private final Set<UUID> locked = new LinkedHashSet<>();
    /** The locked contact picked as the missile target, or null. */
    private @Nullable UUID designated;

    public OrbitalRadarBlockEntity(BlockPos pos, BlockState state) {
        super(OrbitalContent.ORBITAL_RADAR_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- state

    public EnergyHandler energyHandler() {
        return energy;
    }

    public int energyStored() {
        return energy.getAmountAsInt();
    }

    /** Fills the buffer directly (creative/testing helper). */
    public void fill() {
        energy.set(CAPACITY);
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public @Nullable UUID tracking() {
        return tracking;
    }

    public boolean isLocked(UUID satellite) {
        return locked.contains(satellite);
    }

    public @Nullable UUID designated() {
        return designated;
    }

    /** Ticks of powered tracking a lock takes. */
    public static int lockTicks() {
        return Config.RADAR_LOCK_SECONDS.get() * 20;
    }

    /** The owner's team key, or null for an unowned radar. */
    private @Nullable String ownerTeam(MinecraftServer server) {
        return owner == null ? null : FactoryTeams.get(server).teamOf(owner);
    }

    /** Null if the player may use this radar, else why not. */
    public @Nullable Component accessProblem(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (owner == null) return Component.translatable("message.factoryascent.radar_unowned").withStyle(ChatFormatting.RED);
        FactoryTeams teams = FactoryTeams.get(server);
        if (!teams.sameTeam(owner, player.getUUID())) {
            return Component.translatable("message.factoryascent.radar_foreign", teams.displayName(teams.teamOf(owner)))
                    .withStyle(ChatFormatting.RED);
        }
        return null;
    }

    // ---------------------------------------------------------------- actions

    /** Starts tracking a foreign contact over this dimension; null on success, else why not. */
    public @Nullable Component startTracking(UUID satellite) {
        if (!(level instanceof ServerLevel server)) return null;
        String team = ownerTeam(server.getServer());
        if (team == null) return Component.translatable("message.factoryascent.radar_unowned").withStyle(ChatFormatting.RED);
        var found = OrbitRegistry.get(server.getServer()).find(satellite);
        if (found.isEmpty() || !found.get().satellite().dimension().equals(server.dimension())) {
            return Component.translatable("message.factoryascent.radar_contact_gone").withStyle(ChatFormatting.RED);
        }
        if (found.get().team().equals(team)) return Component.translatable("message.factoryascent.radar_own").withStyle(ChatFormatting.RED);
        if (locked.contains(satellite)) return Component.translatable("message.factoryascent.radar_already_locked");
        if (!satellite.equals(tracking)) {
            tracking = satellite;
            trackTicks = 0;
            changed();
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        return null;
    }

    public void stopTracking() {
        if (tracking == null) return;
        tracking = null;
        trackTicks = 0;
        changed();
    }

    /**
     * Picks the target missiles are programmed with: a locked foreign contact, or one of the owner
     * team's own satellites over this dimension (no lock needed). Null on success, else why not.
     */
    public @Nullable Component designate(UUID satellite) {
        if (!locked.contains(satellite) && !isOwn(satellite)) {
            return Component.translatable("message.factoryascent.radar_not_locked").withStyle(ChatFormatting.RED);
        }
        designated = satellite;
        changed();
        return null;
    }

    /** True if the satellite belongs to the radar owner's team and is over this radar's dimension. */
    public boolean isOwn(UUID satellite) {
        if (!(level instanceof ServerLevel server)) return false;
        String team = ownerTeam(server.getServer());
        var found = OrbitRegistry.get(server.getServer()).find(satellite);
        return team != null && found.isPresent() && found.get().team().equals(team)
                && found.get().satellite().dimension().equals(server.dimension());
    }

    /** Locks a contact at once, skipping the tracking time (creative/testing helper). */
    public void forceLock(UUID satellite) {
        if (level instanceof ServerLevel server) lock(server.getServer(), server, satellite);
    }

    /** Short description of a locked contact for missile tooltips: "Uplink-2 (team Foo)". */
    public @Nullable String label(MinecraftServer server, UUID satellite) {
        var found = OrbitRegistry.get(server).find(satellite);
        if (found.isEmpty()) return null;
        return Component.translatable("message.factoryascent.radar_label", found.get().satellite().name(),
                FactoryTeams.get(server).displayName(found.get().team())).getString();
    }

    // ---------------------------------------------------------------- tick

    public void serverTick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (level.getGameTime() % 20 == 0) prune(server, level);
        if (tracking == null) return;
        int stored = energy.getAmountAsInt();
        if (stored < TRACK_DRAIN) return; // no power: tracking pauses
        energy.set(stored - TRACK_DRAIN);
        if (++trackTicks % 40 == 0) level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.4f, 1.8f);
        if (trackTicks >= lockTicks()) lock(server, level, tracking);
    }

    /** Forgets contacts that left orbit (or became friendly when teams changed). */
    private void prune(MinecraftServer server, ServerLevel level) {
        String team = ownerTeam(server);
        OrbitRegistry orbit = OrbitRegistry.get(server);
        boolean changed = locked.removeIf(id -> !isForeignContact(orbit, team, level, id));
        if (designated != null && !locked.contains(designated) && !isOwn(designated)) {
            designated = null;
            changed = true;
        }
        if (tracking != null && !isForeignContact(orbit, team, level, tracking)) {
            tracking = null;
            trackTicks = 0;
            changed = true;
        }
        if (changed) changed();
    }

    private static boolean isForeignContact(OrbitRegistry orbit, @Nullable String team, ServerLevel level, UUID id) {
        var found = orbit.find(id);
        return team != null && found.isPresent() && !found.get().team().equals(team)
                && found.get().satellite().dimension().equals(level.dimension());
    }

    private void lock(MinecraftServer server, ServerLevel level, UUID id) {
        tracking = null;
        trackTicks = 0;
        var found = OrbitRegistry.get(server).find(id);
        String team = ownerTeam(server);
        if (found.isEmpty() || team == null) {
            changed();
            return;
        }
        locked.add(id);
        if (designated == null) designated = id;
        changed();
        FactoryTeams teams = FactoryTeams.get(server);
        Satellite s = found.get().satellite();
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 1.4f);
        OrbitalText.tellTeam(server, team, Component.translatable("message.factoryascent.radar_locked", s.type().displayName(),
                s.name(), teams.displayName(found.get().team())).withStyle(ChatFormatting.GOLD));
        OrbitalText.tellTeam(server, found.get().team(), Component.translatable("message.factoryascent.radar_tracked_warning",
                s.type().displayName(), s.name(), teams.displayName(team), OrbitalText.dimensionName(s.dimension()))
                .withStyle(ChatFormatting.RED));
        OrbitalContent.award(server, teams.members(team), "orbital_radar_lock");
    }

    // ---------------------------------------------------------------- screen

    /** What the radar's screen shows. */
    RadarView view(ServerLevel level, boolean open, Component message) {
        MinecraftServer server = level.getServer();
        FactoryTeams teams = FactoryTeams.get(server);
        String team = ownerTeam(server);
        List<Row> rows = new ArrayList<>();
        int unknown = 0;
        for (OrbitRegistry.Owned o : OrbitRegistry.get(server).everyOver(level.dimension())) {
            Satellite s = o.satellite();
            long days = OrbitalText.daysInOrbit(server, s);
            Component age = Component.translatable("message.factoryascent.station_age", days).withStyle(ChatFormatting.DARK_GRAY);
            if (o.team().equals(team)) {
                int state = s.id().equals(designated) ? OrbitalPayloads.CONTACT_TARGET : OrbitalPayloads.CONTACT_OWN;
                rows.add(new Row(s.id(), Component.translatable("message.factoryascent.radar_row_own", s.type().shortName(), s.name())
                        .withStyle(ChatFormatting.DARK_GREEN).append(age), state, 0));
            } else if (locked.contains(s.id())) {
                int state = s.id().equals(designated) ? OrbitalPayloads.CONTACT_TARGET : OrbitalPayloads.CONTACT_LOCKED;
                rows.add(new Row(s.id(), Component.translatable("message.factoryascent.radar_row_locked", s.type().shortName(), s.name(),
                        teams.displayName(o.team())).withStyle(s.type().color()).append(age), state, 100));
            } else {
                unknown++;
                boolean tracked = s.id().equals(tracking);
                rows.add(new Row(s.id(), Component.translatable("message.factoryascent.radar_row_unknown", unknown)
                        .withStyle(ChatFormatting.GRAY).append(age),
                        tracked ? OrbitalPayloads.CONTACT_TRACKING : OrbitalPayloads.CONTACT_UNKNOWN,
                        tracked ? Math.min(99, trackTicks * 100 / lockTicks()) : 0));
            }
        }
        Component header = Component.translatable("message.factoryascent.radar_header", OrbitalText.dimensionName(level.dimension()),
                team == null ? "?" : teams.displayName(team), EnergyUtil.format(energyStored()), EnergyUtil.format(CAPACITY));
        return new RadarView(open, worldPosition, energyStored(), CAPACITY, header, rows, message);
    }

    /** Opens the radar's screen for a player of the owner's team (else tells them why not). */
    public void open(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return;
        Component problem = accessProblem(player);
        if (problem != null) {
            player.sendOverlayMessage(problem);
            return;
        }
        PacketDistributor.sendToPlayer(player, view(server, true, Component.empty()));
    }

    /** A radar screen button. */
    static void handle(ServerPlayer player, RadarAction action) {
        ServerLevel level = player.level();
        BlockPos pos = action.pos();
        if (!level.isLoaded(pos) || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > USE_RANGE * USE_RANGE) return;
        if (!(level.getBlockEntity(pos) instanceof OrbitalRadarBlockEntity radar)) return;
        Component problem = radar.accessProblem(player);
        if (problem == null) {
            problem = switch (action.action()) {
                case RadarAction.TRACK -> radar.startTracking(action.id());
                case RadarAction.DESIGNATE -> radar.designate(action.id());
                case RadarAction.STOP -> {
                    radar.stopTracking();
                    yield null;
                }
                default -> null;
            };
        }
        PacketDistributor.sendToPlayer(player, radar.view(level, false, problem == null ? Component.empty() : problem));
    }

    // ---------------------------------------------------------------- save / sync

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client only needs to know whether the dish is tracking (it spins faster). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", tracking != null);
        return tag;
    }

    /** Client side: set from the update tag. */
    private boolean clientActive;

    public boolean isActive() {
        return level != null && level.isClientSide() ? clientActive : tracking != null;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        clientActive = input.getBooleanOr("active", false);
        energy.deserialize(input.childOrEmpty("energy"));
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        tracking = input.read("tracking", UUIDUtil.CODEC).orElse(null);
        trackTicks = input.getIntOr("track_ticks", 0);
        locked.clear();
        input.read("locked", UUIDUtil.CODEC.listOf()).ifPresent(locked::addAll);
        designated = input.read("designated", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
        if (tracking != null) output.store("tracking", UUIDUtil.CODEC, tracking);
        output.putInt("track_ticks", trackTicks);
        output.store("locked", UUIDUtil.CODEC.listOf(), List.copyOf(locked));
        if (designated != null) output.store("designated", UUIDUtil.CODEC, designated);
    }
}
