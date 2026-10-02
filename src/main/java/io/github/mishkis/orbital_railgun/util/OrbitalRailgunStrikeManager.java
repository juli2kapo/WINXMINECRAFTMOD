package io.github.mishkis.orbital_railgun.util;

import com.mojang.logging.LogUtils;
import io.github.mishkis.orbital_railgun.OrbitalRailgun;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2i;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OrbitalRailgunStrikeManager {
    public record Strike(BlockPos pos, List<Entity> entities, int startTick, ResourceKey<Level> dimension) {}

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Queue<Strike> activeStrikes = new ConcurrentLinkedQueue<>();
    private static final List<CraterJob> activeCraters = new ArrayList<>();
    /** Crater columns that keep being swept for loose items for a while after carving (falling mobs drop loot late). */
    private record Sweep(ServerLevel level, BlockPos origin, int untilTick) {}
    private static final List<Sweep> activeSweeps = new ArrayList<>();
    private static final int SWEEP_TICKS = 200;
    private static final ResourceKey<DamageType> STRIKE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(OrbitalRailgun.MOD_ID, "strike"));
    private static final int RADIUS = 24;
    private static final int RADIUS_SQUARED = RADIUS * RADIUS;
    private static final boolean[][] mask = new boolean[RADIUS * 2 + 1][RADIUS * 2 + 1];

    /** Server time spent carving per tick before the rest of the crater is left for the next tick. */
    private static final long CARVE_BUDGET_NANOS = 15_000_000L;
    /** Blocks with a block entity go through Level#setBlock, without neighbour updates, drops or container spills. */
    private static final int BLOCK_ENTITY_FLAGS = Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    private static final Heightmap.Types[] HEIGHTMAPS = {
            Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE
    };
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    public static void tick(MinecraftServer server) {
        Iterator<Strike> iterator = activeStrikes.iterator();
        while (iterator.hasNext()) {
            Strike strike = iterator.next();
            float age = server.getTickCount() - strike.startTick();
            BlockPos blockPos = strike.pos();
            ResourceKey<Level> dimension = strike.dimension();

            if (age >= 700) {
                iterator.remove();

                ServerLevel level = server.getLevel(dimension);
                if (level == null) {
                    continue;
                }

                DamageSource damageSource = level.damageSources().source(STRIKE_DAMAGE);
                strike.entities().forEach(entity -> {
                    if (entity.level().dimension() == dimension && entity.position().subtract(Vec3.atCenterOf(blockPos)).lengthSqr() <= RADIUS_SQUARED) {
                        entity.hurtServer(level, damageSource, 100000f);
                    }
                });
                // Mobs that wandered into the column after the strike was aimed are hit as well.
                for (net.minecraft.world.entity.LivingEntity living : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, craterColumn(level, blockPos),
                        e -> e.isAlive() && inCrater(e.getBlockX() - blockPos.getX(), e.getBlockZ() - blockPos.getZ()))) {
                    if (!(living instanceof Player player && (player.isSpectator() || player.isCreative()))) {
                        living.hurtServer(level, damageSource, 100000f);
                    }
                }

                vaporizeLooseItems(level, blockPos);
                activeCraters.add(new CraterJob(level, blockPos));
            } else if (age >= 400) {
                strike.entities().forEach(entity -> {
                    if (entity instanceof Player player && player.isSpectator()) {
                        return;
                    }
                    if (entity.level().dimension() == dimension) {
                        Vec3 dir = Vec3.atCenterOf(blockPos).subtract(entity.position());
                        double mag = Math.min(1. / Math.abs(dir.length() - 20.) * 4. * (age - 400.) / 300., 5.);
                        dir = dir.normalize();

                        entity.push(dir.x * mag, dir.y * mag, dir.z * mag);
                        entity.hurtMarked = true;
                    }
                });
            }
        }

        if (!activeSweeps.isEmpty()) {
            int now = server.getTickCount();
            activeSweeps.removeIf(sweep -> {
                if (now % 5 == 0) {
                    vaporizeLooseItems(sweep.level(), sweep.origin());
                }
                return now >= sweep.untilTick();
            });
        }

        if (!activeCraters.isEmpty()) {
            long deadline = System.nanoTime() + CARVE_BUDGET_NANOS;
            Iterator<CraterJob> jobs = activeCraters.iterator();
            while (jobs.hasNext()) {
                CraterJob job = jobs.next();
                if (job.tick(server, deadline)) {
                    jobs.remove();
                }
            }
        }
    }

    /** Nothing survives the strike: dropped items and XP orbs inside the crater column are removed. */
    static void vaporizeLooseItems(ServerLevel level, BlockPos origin) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, craterColumn(level, origin),
                e -> e instanceof net.minecraft.world.entity.item.ItemEntity || e instanceof net.minecraft.world.entity.ExperienceOrb)) {
            if (inCrater(entity.getBlockX() - origin.getX(), entity.getBlockZ() - origin.getZ())) {
                entity.discard();
            }
        }
    }

    private static net.minecraft.world.phys.AABB craterColumn(ServerLevel level, BlockPos origin) {
        return new net.minecraft.world.phys.AABB(
                origin.getX() - RADIUS, level.getMinY(), origin.getZ() - RADIUS,
                origin.getX() + RADIUS + 1, level.getMaxY() + 1, origin.getZ() + RADIUS + 1);
    }

    public static void clear() {
        activeStrikes.clear();
        activeCraters.clear();
        activeSweeps.clear();
    }

    public static void initialize() {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                mask[x + RADIUS][z + RADIUS] = Vector2i.lengthSquared(x, z) <= RADIUS_SQUARED;
            }
        }
    }

    private static boolean inCrater(int dx, int dz) {
        return dx >= -RADIUS && dx <= RADIUS && dz >= -RADIUS && dz <= RADIUS && mask[dx + RADIUS][dz + RADIUS];
    }

    /**
     * Clears the crater (a radius-24 cylinder through the whole world height) over several ticks, within a
     * fixed time budget per tick; the unit of work is one 16x16x16 chunk section. Blocks are written
     * straight into the sections: no neighbour or shape updates except across the crater wall, no drops,
     * no per-block packets and only the light checks that can actually change something. Once every
     * chunk is done and the light engine has caught up, each carved chunk is resent to its watchers in
     * one packet (blocks and finished light together), so clients rebuild each section exactly once
     * instead of relighting ~700k block changes themselves.
     */
    private static final class CraterJob {
        private final ServerLevel level;
        private final BlockPos origin;
        private final List<ChunkPos> pending = new ArrayList<>();
        private final List<ChunkPos> carved = new ArrayList<>();
        private int nextChunk = 0;
        private ChunkCarver current;
        private int ticks = 0;
        private long carveNanos = 0;
        private long maxTickNanos = 0;
        private int removedBlocks = 0;
        private int lightChecks = 0;
        private int wallUpdates = 0;

        CraterJob(ServerLevel level, BlockPos origin) {
            this.level = level;
            this.origin = origin;
            int minChunkX = SectionPos.blockToSectionCoord(origin.getX() - RADIUS);
            int maxChunkX = SectionPos.blockToSectionCoord(origin.getX() + RADIUS);
            int minChunkZ = SectionPos.blockToSectionCoord(origin.getZ() - RADIUS);
            int maxChunkZ = SectionPos.blockToSectionCoord(origin.getZ() + RADIUS);
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    pending.add(new ChunkPos(cx, cz));
                }
            }
            // Centre first, so the part under the beam goes before the fringes.
            pending.sort(Comparator.comparingDouble(pos -> {
                double dx = pos.getMiddleBlockX() - origin.getX();
                double dz = pos.getMiddleBlockZ() - origin.getZ();
                return dx * dx + dz * dz;
            }));
        }

        /** @return true once the job is finished and can be dropped. */
        boolean tick(MinecraftServer server, long deadline) {
            long start = System.nanoTime();
            // Always at least one section per tick, so a slow server still makes progress.
            do {
                if (current == null) {
                    if (nextChunk >= pending.size()) {
                        break;
                    }
                    ChunkPos pos = pending.get(nextChunk++);
                    current = ChunkCarver.create(this, level.getChunk(pos.x(), pos.z()));
                    if (current == null) {
                        continue;
                    }
                }
                if (current.carveNextSection()) {
                    if (current.finish()) {
                        carved.add(current.chunk.getPos());
                    }
                    current = null;
                }
            } while (System.nanoTime() < deadline);
            long spent = System.nanoTime() - start;
            carveNanos += spent;
            maxTickNanos = Math.max(maxTickNanos, spent);
            ticks++;

            if (current != null || nextChunk < pending.size()) {
                return false;
            }

            LOGGER.info("Orbital railgun crater at {}: removed {} blocks in {} chunks over {} ticks ({} ms in total, worst tick {} ms; {} light checks, {} wall updates)",
                    origin.toShortString(), removedBlocks, carved.size(), ticks, String.format("%.1f", carveNanos / 1e6), String.format("%.1f", maxTickNanos / 1e6), lightChecks, wallUpdates);
            vaporizeLooseItems(level, origin);
            activeSweeps.add(new Sweep(level, origin, server.getTickCount() + SWEEP_TICKS));
            scheduleResend(server);
            return true;
        }

        /**
         * Waits until the light engine has processed every check queued for the crater, then sends each
         * carved chunk (blocks and light together) to the players watching it.
         */
        private void scheduleResend(MinecraftServer server) {
            if (carved.isEmpty()) {
                return;
            }
            ThreadedLevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
            CompletableFuture<?>[] lightDone = carved.stream()
                    .map(pos -> lightEngine.waitForPendingTasks(pos.x(), pos.z()))
                    .toArray(CompletableFuture[]::new);
            long queued = System.nanoTime();
            CompletableFuture.allOf(lightDone).thenRunAsync(() -> {
                ServerChunkCache chunkSource = level.getChunkSource();
                int packets = 0;
                for (ChunkPos pos : carved) {
                    LevelChunk chunk = chunkSource.getChunkNow(pos.x(), pos.z());
                    if (chunk == null) {
                        continue;
                    }
                    List<ServerPlayer> players = chunkSource.chunkMap.getPlayers(pos, false);
                    if (players.isEmpty()) {
                        continue;
                    }
                    ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, chunkSource.getLightEngine(), null, null);
                    for (ServerPlayer player : players) {
                        player.connection.send(packet);
                        packets++;
                    }
                }
                LOGGER.info("Orbital railgun crater at {}: light settled after {} ms, resent {} chunk packets",
                        origin.toShortString(), String.format("%.1f", (System.nanoTime() - queued) / 1e6), packets);
            }, server).exceptionally(throwable -> {
                LOGGER.error("Failed to resend orbital railgun crater chunks", throwable);
                return null;
            });
        }
    }

    /** Carves the crater's part of one chunk, one section per call, top to bottom. */
    private static final class ChunkCarver {
        private final CraterJob job;
        private final ServerLevel level;
        private final LevelChunk chunk;
        private final ChunkPos chunkPos;
        private final int baseX;
        private final int baseZ;
        /** Which of the chunk's 16x16 columns lie inside the crater, and which of those touch its wall. */
        private final boolean[] column;
        private final boolean[] wall;
        private final int[] lowestChanged = new int[256];
        private final LongArrayList wallPositions = new LongArrayList();
        private final LongArrayList lightChecks = new LongArrayList();
        private final Heightmap[] heightmaps = new Heightmap[HEIGHTMAPS.length];
        private final ServerChunkCache chunkSource;
        private final ThreadedLevelLightEngine lightEngine;
        private final LayerLightEventListener blockLight;
        /** Block light of the current section and its six neighbours; index 13 is the section itself. */
        private final DataLayer[] lightAround = new DataLayer[27];
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private int sectionIndex;
        private boolean changed = false;

        private ChunkCarver(CraterJob job, LevelChunk chunk, boolean[] column, boolean[] wall) {
            this.job = job;
            this.level = job.level;
            this.chunk = chunk;
            this.chunkPos = chunk.getPos();
            this.baseX = chunkPos.getMinBlockX();
            this.baseZ = chunkPos.getMinBlockZ();
            this.column = column;
            this.wall = wall;
            this.chunkSource = level.getChunkSource();
            this.lightEngine = chunkSource.getLightEngine();
            this.blockLight = lightEngine.getLayerListener(LightLayer.BLOCK);
            this.sectionIndex = chunk.getSectionsCount() - 1;
            Arrays.fill(lowestChanged, Integer.MAX_VALUE);
            for (int i = 0; i < HEIGHTMAPS.length; i++) {
                heightmaps[i] = chunk.getOrCreateHeightmapUnprimed(HEIGHTMAPS[i]);
            }
        }

        static ChunkCarver create(CraterJob job, LevelChunk chunk) {
            int baseX = chunk.getPos().getMinBlockX();
            int baseZ = chunk.getPos().getMinBlockZ();
            boolean[] column = new boolean[256];
            boolean[] wall = new boolean[256];
            boolean any = false;
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int dx = baseX + lx - job.origin.getX();
                    int dz = baseZ + lz - job.origin.getZ();
                    if (inCrater(dx, dz)) {
                        int index = lz << 4 | lx;
                        column[index] = true;
                        wall[index] = !inCrater(dx - 1, dz) || !inCrater(dx + 1, dz) || !inCrater(dx, dz - 1) || !inCrater(dx, dz + 1);
                        any = true;
                    }
                }
            }
            return any ? new ChunkCarver(job, chunk, column, wall) : null;
        }

        /** @return true when every section has been carved. */
        boolean carveNextSection() {
            while (sectionIndex >= 0 && chunk.getSection(sectionIndex).hasOnlyAir()) {
                sectionIndex--;
            }
            if (sectionIndex < 0) {
                return true;
            }

            LevelChunkSection section = chunk.getSection(sectionIndex);
            int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
            int baseY = SectionPos.sectionToBlockCoord(sectionY);
            sectionIndex--;
            BlockState air = Blocks.AIR.defaultBlockState();
            loadLightAround(sectionY);
            lightChecks.clear();

            for (int ly = 15; ly >= 0; ly--) {
                int y = baseY + ly;
                for (int index = 0; index < 256; index++) {
                    if (!column[index]) {
                        continue;
                    }
                    int lx = index & 15;
                    int lz = index >> 4;
                    BlockState state = section.getBlockState(lx, ly, lz);
                    // Leave air alone and never break unbreakable blocks (bedrock, barriers, command blocks...),
                    // except the End portal: its frames and the portal itself do get blasted away.
                    if (state.isAir()) {
                        continue;
                    }
                    pos.set(baseX + lx, y, baseZ + lz);
                    boolean endPortal = state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.END_PORTAL);
                    if (!endPortal && state.getDestroySpeed(level, pos) < 0) {
                        continue;
                    }

                    if (state.hasBlockEntity()) {
                        // Rare; the regular path removes the block entity and takes care of its light and POI.
                        level.setBlock(pos, air, BLOCK_ENTITY_FLAGS);
                    } else {
                        section.setBlockState(lx, ly, lz, air, false);
                        for (Heightmap heightmap : heightmaps) {
                            heightmap.update(lx, y, lz, air);
                        }
                        if (PoiTypes.hasPoi(state)) {
                            level.updatePOIOnBlockStateChange(pos, state, air);
                        }
                        // Block light only needs a check where an emitter went away, or where light next to
                        // the new opening now has to flow in; sky light is handled per column in finish().
                        if (state.getLightEmission(level, pos) > 0 || hasLitNeighbour(lx, ly, lz)) {
                            lightChecks.add(pos.asLong());
                        }
                    }

                    if (wall[index]) {
                        wallPositions.add(pos.asLong());
                    }
                    lowestChanged[index] = y;
                    job.removedBlocks++;
                    changed = true;
                }
            }

            if (section.hasOnlyAir()) {
                lightEngine.updateSectionStatus(SectionPos.of(chunkPos, sectionY), true);
                chunkSource.onSectionEmptinessChanged(chunkPos.x(), sectionY, chunkPos.z(), true);
            }
            for (int i = 0; i < lightChecks.size(); i++) {
                lightEngine.checkBlock(BlockPos.of(lightChecks.getLong(i)));
            }
            job.lightChecks += lightChecks.size();
            return sectionIndex < 0;
        }

        private void loadLightAround(int sectionY) {
            Arrays.fill(lightAround, null);
            lightAround[13] = blockLight.getDataLayerData(SectionPos.of(chunkPos, sectionY));
            lightAround[4] = blockLight.getDataLayerData(SectionPos.of(chunkPos.x(), sectionY - 1, chunkPos.z()));
            lightAround[22] = blockLight.getDataLayerData(SectionPos.of(chunkPos.x(), sectionY + 1, chunkPos.z()));
            lightAround[12] = blockLight.getDataLayerData(SectionPos.of(chunkPos.x() - 1, sectionY, chunkPos.z()));
            lightAround[14] = blockLight.getDataLayerData(SectionPos.of(chunkPos.x() + 1, sectionY, chunkPos.z()));
            lightAround[10] = blockLight.getDataLayerData(SectionPos.of(chunkPos.x(), sectionY, chunkPos.z() - 1));
            lightAround[16] = blockLight.getDataLayerData(SectionPos.of(chunkPos.x(), sectionY, chunkPos.z() + 1));
        }

        private boolean hasLitNeighbour(int lx, int ly, int lz) {
            return blockLightAt(lx - 1, ly, lz) > 1 || blockLightAt(lx + 1, ly, lz) > 1
                    || blockLightAt(lx, ly - 1, lz) > 1 || blockLightAt(lx, ly + 1, lz) > 1
                    || blockLightAt(lx, ly, lz - 1) > 1 || blockLightAt(lx, ly, lz + 1) > 1;
        }

        /** Stored block light at section-local coordinates that may step one block outside the section. */
        private int blockLightAt(int lx, int ly, int lz) {
            int sx = lx >> 4;
            int sy = ly >> 4;
            int sz = lz >> 4;
            DataLayer layer = lightAround[(sx + 1) + (sy + 1) * 9 + (sz + 1) * 3];
            return layer == null ? 0 : layer.get(lx & 15, ly & 15, lz & 15);
        }

        /** @return whether anything in the chunk changed. */
        boolean finish() {
            if (!changed) {
                return false;
            }

            // Sky light: refresh the per-column sky source heights, then one check per column is enough -
            // SkyLightEngine#checkNode re-seeds the whole column from its new lowest source and spreads it
            // sideways into the crater wall. (Queued after all section status changes, like vanilla.)
            chunk.getSkyLightSources().fillFrom(chunk);
            for (int index = 0; index < 256; index++) {
                if (lowestChanged[index] != Integer.MAX_VALUE) {
                    lightEngine.checkBlock(new BlockPos(baseX + (index & 15), lowestChanged[index], baseZ + (index >> 4)));
                    job.lightChecks++;
                }
            }
            chunk.markUnsaved();

            // Neighbour and shape updates only across the crater wall, so water, lava, sand and attached
            // blocks outside react to the hole the way they did with setBlockAndUpdate.
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos outside = new BlockPos.MutableBlockPos();
            for (int i = 0; i < wallPositions.size(); i++) {
                pos.set(wallPositions.getLong(i));
                for (Direction direction : HORIZONTAL) {
                    outside.setWithOffset(pos, direction);
                    if (inCrater(outside.getX() - job.origin.getX(), outside.getZ() - job.origin.getZ()) || !level.isLoaded(outside)) {
                        continue;
                    }
                    if (level.getBlockState(outside).isAir()) {
                        continue;
                    }
                    BlockPos outsidePos = outside.immutable();
                    level.neighborShapeChanged(direction.getOpposite(), outsidePos, pos.immutable(), air, Block.UPDATE_CLIENTS, Block.UPDATE_LIMIT - 1);
                    level.neighborChanged(outsidePos, Blocks.AIR, null);
                    job.wallUpdates++;
                }
            }
            return true;
        }
    }
}
