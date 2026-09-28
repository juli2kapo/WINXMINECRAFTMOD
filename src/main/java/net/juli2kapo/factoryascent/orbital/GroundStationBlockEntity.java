package net.juli2kapo.factoryascent.orbital;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Ground Station's survey state (the client also uses it to draw the sweeping dish). The
 * station belongs to the team of whoever placed it ({@link #owner}; an unowned station is claimed by
 * the first player who opens it). While that team has a Survey Satellite over this dimension and
 * the station is loaded, it images the terrain around itself chunk by chunk along an outward spiral
 * into the team's {@link SurveyData} (see {@link SurveyScanner}).
 */
public class GroundStationBlockEntity extends BlockEntity {
    private @Nullable UUID owner;
    /** Next spiral index to image in the current sweep. */
    int index;
    /** Completed sweeps: the first one fills in, later ones refresh loaded chunks. */
    int sweeps;
    /** Game time the next sweep may start after a finished one. */
    private long pauseUntil;
    /** Disk reads in flight (not saved). */
    int reads;
    /** Chunks whose saved data couldn't be imaged (not generated yet): retried only once loaded (not saved). */
    final LongOpenHashSet missing = new LongOpenHashSet();
    private boolean siteKnown;
    private boolean surveying;
    private int checkIn;

    public GroundStationBlockEntity(BlockPos pos, BlockState state) {
        super(OrbitalContent.GROUND_STATION_BE.get(), pos, state);
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        siteKnown = false;
        setChanged();
    }

    /** The owner's team key, or null for an unowned station. */
    public @Nullable String team(MinecraftServer server) {
        return owner == null ? null : FactoryTeams.get(server).teamOf(owner);
    }

    /** True while the owner's team has a Survey Satellite over this dimension (checked once a second). */
    public boolean surveying() {
        return surveying;
    }

    /** Progress of the current sweep, 0..1 (1 once the whole radius has been covered and it is pausing). */
    public float sweepProgress() {
        int total = SurveyScanner.total(net.juli2kapo.factoryascent.Config.SURVEY_RADIUS_CHUNKS.get());
        return Math.min(1f, index / (float) total);
    }

    /** Ring (in chunks from the station) the current sweep has reached. */
    public int sweepRing() {
        return SurveyScanner.ring(index);
    }

    public int sweeps() {
        return sweeps;
    }

    void finishSweep(long now) {
        index = 0;
        sweeps++;
        pauseUntil = now + SurveyScanner.SWEEP_PAUSE;
        setChanged();
    }

    /** True while waiting between two sweeps. */
    public boolean pausing() {
        return level != null && level.getGameTime() < pauseUntil;
    }

    public void serverTick(ServerLevel level) {
        if (owner == null) return;
        MinecraftServer server = level.getServer();
        String team = team(server);
        if (!siteKnown) {
            SurveySites.get(server).put(level.dimension(), worldPosition, SurveySites.STATION, owner);
            siteKnown = true;
        }
        if (--checkIn <= 0) {
            checkIn = 20;
            surveying = OrbitRegistry.get(server).has(team, level.dimension(), SatelliteType.SURVEY);
        }
        if (!surveying || level.getGameTime() < pauseUntil) return;
        SurveyScanner.step(level, this, team, SurveyData.get(server, team, level.dimension()));
    }

    /** Forgets the station's map marker when it is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) SurveySites.get(server.getServer()).remove(server.dimension(), pos);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        index = input.getIntOr("survey_index", 0);
        sweeps = input.getIntOr("survey_sweeps", 0);
        pauseUntil = input.getLongOr("survey_pause", 0L);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) output.store("owner", UUIDUtil.CODEC, owner);
        output.putInt("survey_index", index);
        output.putInt("survey_sweeps", sweeps);
        output.putLong("survey_pause", pauseUntil);
    }
}
