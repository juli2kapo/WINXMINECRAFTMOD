package net.juli2kapo.factoryascent.orbital;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.juli2kapo.factoryascent.Config;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Survey imaging: turns chunks into 16×16 vanilla map pixels for {@link SurveyData}, and drives
 * each Ground Station's outward spiral ({@link GroundStationBlockEntity#serverTick}).
 *
 * <p><b>Which chunks get imaged.</b> Never generates anything. A chunk that is loaded is imaged
 * from the live world. One that isn't loaded is read from the region file asynchronously (the same
 * I/O worker the game uses, so pending saves are seen) and imaged on a background thread from the
 * parsed block sections, without ever becoming a loaded chunk; this only happens for chunks
 * already saved as fully generated in the current data version (anything else is skipped and
 * picked up whenever it is loaded). {@link Config#SURVEY_FROM_DISK} turns disk reads off. Each
 * station images at most {@link Config#SURVEY_CHUNKS_PER_TICK} chunks per tick, with at most
 * {@link #MAX_READS} disk reads in flight.
 *
 * <p><b>Pixels.</b> Like a vanilla map at scale 1: the colour of the top block with a map colour,
 * relief shading from the height difference to the pixel to the north, water shaded by depth, and
 * the vanilla dirt/stone speckle in dimensions with a ceiling.
 */
public final class SurveyScanner {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Disk reads one station may have in flight. */
    static final int MAX_READS = 4;
    /** Already-imaged chunks a station may skip over per tick. */
    private static final int MAX_SKIPS = 2048;
    /** Ticks between two sweeps once a station has covered its whole radius. */
    static final int SWEEP_PAUSE = 1200;

    /** A chunk imaged off-thread, waiting to be stored on the server thread. */
    private record Imaged(GroundStationBlockEntity station, MinecraftServer server, String team, ResourceKey<Level> dimension,
                          long chunk, @Nullable Image image) {}

    /** The pixels of one chunk and the heights of its first and last rows. */
    record Image(byte[] pixels, int[] north, int[] south, @Nullable String biome) {}

    private static final ConcurrentLinkedQueue<Imaged> DONE = new ConcurrentLinkedQueue<>();

    private SurveyScanner() {}

    // ---------------------------------------------------------------- spiral

    /** Chunks a station covers: a (2r+1)² square. */
    public static int total(int radius) {
        return (2 * radius + 1) * (2 * radius + 1);
    }

    /** Offset (packed like a ChunkPos) of the n-th chunk of the outward square spiral: 0 is the centre, then ring 1, ring 2… */
    static long spiral(int n) {
        if (n <= 0) return ChunkPos.pack(0, 0);
        int r = (int) Math.ceil((Math.sqrt(n + 1) - 1) / 2);
        while ((2 * r + 1) * (2 * r + 1) <= n) r++;
        while (r > 1 && (2 * r - 1) * (2 * r - 1) > n) r--;
        int side = 2 * r, k = n - (2 * r - 1) * (2 * r - 1);
        int x, z;
        if (k < side) {
            x = r;
            z = -r + 1 + k;
        } else if (k < 2 * side) {
            x = r - 1 - (k - side);
            z = r;
        } else if (k < 3 * side) {
            x = -r;
            z = r - 1 - (k - 2 * side);
        } else {
            x = -r + 1 + (k - 3 * side);
            z = -r;
        }
        return ChunkPos.pack(x, z);
    }

    /** Ring (Chebyshev distance in chunks) of the n-th spiral chunk. */
    static int ring(int n) {
        long p = spiral(n);
        return Math.max(Math.abs(ChunkPos.getX(p)), Math.abs(ChunkPos.getZ(p)));
    }

    // ---------------------------------------------------------------- stepping a station

    /**
     * One tick of a station's sweep: images up to the per-tick budget of chunks along its spiral.
     * Chunks already imaged are skipped during the first sweep; later sweeps re-image the loaded
     * ones (so changes show up) and retry unloaded ones that couldn't be read before only once they
     * have been loaded.
     */
    static void step(ServerLevel level, GroundStationBlockEntity station, String team, SurveyData data) {
        int radius = Config.SURVEY_RADIUS_CHUNKS.get();
        int total = total(radius);
        int budget = Config.SURVEY_CHUNKS_PER_TICK.get();
        int skips = MAX_SKIPS;
        ChunkPos centre = ChunkPos.containing(station.getBlockPos());
        boolean fromDisk = Config.SURVEY_FROM_DISK.get();
        while (budget > 0 && skips > 0 && station.reads < MAX_READS) {
            if (station.index >= total) {
                station.finishSweep(level.getGameTime());
                return;
            }
            long offset = spiral(station.index);
            int cx = centre.x() + ChunkPos.getX(offset), cz = centre.z() + ChunkPos.getZ(offset);
            long chunk = ChunkPos.pack(cx, cz);
            LevelChunk loaded = level.getChunkSource().getChunkNow(cx, cz);
            if (loaded != null) {
                if (station.sweeps == 0 && data.has(chunk)) {
                    skips--;
                } else {
                    store(level.getServer(), team, level.dimension(), data, chunk, image(level, loaded, data));
                    budget--;
                }
            } else if (data.has(chunk) || !fromDisk || station.missing.contains(chunk)) {
                skips--;
            } else {
                readFromDisk(level, station, team, data, chunk);
                budget--;
            }
            station.index++;
            station.setChanged();
        }
    }

    /** Starts an asynchronous read + imaging of a saved chunk; the result is stored in {@link #drain}. */
    private static void readFromDisk(ServerLevel level, GroundStationBlockEntity station, String team, SurveyData data, long chunk) {
        station.reads++;
        ChunkPos pos = ChunkPos.unpack(chunk);
        int[] northEdge = northEdge(data, chunk);
        MinecraftServer server = level.getServer();
        ResourceKey<Level> dimension = level.dimension();
        level.getChunkSource().chunkMap.read(pos)
                .thenApplyAsync(tag -> imageSaved(level, tag, northEdge), Util.backgroundExecutor())
                .whenComplete((image, error) -> {
                    if (error != null) LOGGER.debug("Survey could not read chunk {}", pos, error);
                    DONE.add(new Imaged(station, server, team, dimension, chunk, error == null ? image : null));
                });
    }

    /** Stores the chunks imaged off-thread (server thread, every tick). */
    static void drain(MinecraftServer server) {
        for (Imaged done; (done = DONE.poll()) != null; ) {
            done.station().reads = Math.max(0, done.station().reads - 1);
            if (done.server() != server) continue;
            if (done.image() == null) {
                done.station().missing.add(done.chunk());
                continue;
            }
            SurveyData data = SurveyData.get(server, done.team(), done.dimension());
            store(server, done.team(), done.dimension(), data, done.chunk(), done.image());
        }
    }

    static void clear() {
        DONE.clear();
    }

    private static void store(MinecraftServer server, String team, ResourceKey<Level> dimension, SurveyData data, long chunk, Image image) {
        for (long changed : data.put(chunk, image.pixels(), image.north(), image.south(), image.biome())) {
            SurveyService.chunkChanged(server, team, dimension, changed);
        }
    }

    /** Heights of the last row of the chunk to the north, if it was imaged (to shade this chunk's first row). */
    private static int @Nullable [] northEdge(SurveyData data, long chunk) {
        long north = ChunkPos.pack(ChunkPos.getX(chunk), ChunkPos.getZ(chunk) - 1);
        if (!data.has(north)) return null;
        int[] out = new int[16];
        for (int x = 0; x < 16; x++) out[x] = data.southHeight(north, x);
        return out;
    }

    // ---------------------------------------------------------------- imaging

    /** Images a loaded chunk (server thread). */
    static Image image(ServerLevel level, LevelChunk chunk, SurveyData data) {
        int midY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        Holder<Biome> biome = chunk.getNoiseBiome(chunk.getPos().getMiddleBlockX() >> 2, midY >> 2, chunk.getPos().getMiddleBlockZ() >> 2);
        return image(chunk, chunk.getPos(), level.dimensionType().hasCeiling(),
                (x, z) -> chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1,
                northEdge(data, chunk.getPos().pack()), biome.unwrapKey().map(k -> k.identifier().toString()).orElse(null));
    }

    /** Images a chunk from its saved data (background thread), or null if it isn't a fully generated chunk of this version. */
    static @Nullable Image imageSaved(ServerLevel level, Optional<CompoundTag> saved, int @Nullable [] northEdge) {
        if (saved.isEmpty()) return null;
        CompoundTag tag = saved.get();
        if (tag.getIntOr("DataVersion", -1) != SharedConstants.getCurrentVersion().dataVersion().version()) return null;
        if (SerializableChunkData.getChunkStatusFromTag(tag) != ChunkStatus.FULL) return null;
        SerializableChunkData parsed = SerializableChunkData.parse(level, level.palettedContainerFactory(), tag);
        if (parsed == null) return null;
        Sections sections = new Sections(level.getMinY(), level.getHeight(), parsed.sectionData());
        ChunkPos pos = parsed.chunkPos();
        int midY = sections.top(8, 8);
        String biome = sections.biome(midY);
        return image(sections, pos, level.dimensionType().hasCeiling(), sections::top, northEdge, biome);
    }

    /** First free Y above the surface of a column (like the WORLD_SURFACE heightmap). */
    private interface Top {
        int at(int x, int z);
    }

    /**
     * The pixels of one chunk, walked row by row from north to south like a vanilla map. Row 0
     * is shaded against {@code northEdge} (the chunk to the north's last row) when known.
     */
    private static Image image(BlockGetter chunk, ChunkPos pos, boolean ceiling, Top top, int @Nullable [] northEdge,
                               @Nullable String biome) {
        byte[] pixels = new byte[256];
        int[] north = new int[16], south = new int[16];
        double[] previous = new double[16];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinY();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = pos.getMinBlockX() + x, wz = pos.getMinBlockZ() + z;
                MapColor color;
                int height;
                int waterDepth = 0;
                if (ceiling) {
                    int noise = wx + wz * 231871;
                    noise = noise * noise * 31287121 + noise * 11;
                    color = (noise >> 20 & 1) == 0 ? MapColor.DIRT : MapColor.STONE;
                    height = 100;
                } else {
                    int y = top.at(x, z);
                    BlockState state = Blocks.BEDROCK.defaultBlockState();
                    if (y > minY) {
                        do {
                            p.set(wx, --y, wz);
                            state = chunk.getBlockState(p);
                        } while (state.getMapColor(chunk, p) == MapColor.NONE && y > minY);
                        if (y > minY && !state.getFluidState().isEmpty()) {
                            int below = y - 1;
                            BlockState under;
                            do {
                                p.set(wx, below--, wz);
                                under = chunk.getBlockState(p);
                                waterDepth++;
                            } while (below > minY && !under.getFluidState().isEmpty());
                            p.set(wx, y, wz);
                            state = state.getFluidState().createLegacyBlock();
                        }
                    }
                    color = state.getMapColor(chunk, p);
                    height = y;
                }
                if (z == 0) {
                    north[x] = height;
                    previous[x] = northEdge != null && northEdge[x] != Integer.MIN_VALUE ? northEdge[x] : height;
                }
                if (z == 15) south[x] = height;
                MapColor.Brightness brightness;
                if (color == MapColor.WATER) {
                    double d = waterDepth * 0.1 + ((wx + wz) & 1) * 0.2;
                    brightness = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    brightness = relief(height, (int) previous[x], wx, wz);
                }
                previous[x] = height;
                pixels[z * 16 + x] = color.getPackedId(brightness);
            }
        }
        return new Image(pixels, north, south, biome);
    }

    /** Vanilla map relief at scale 1: brighter facing north-up slopes, darker facing down, with the checkerboard dither. */
    static MapColor.Brightness relief(int height, int previousHeight, int x, int z) {
        double d = (height - previousHeight) * 4.0 / 5.0 + (((x + z) & 1) - 0.5) * 0.4;
        return d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
    }

    /** A saved chunk's block sections as a read-only {@link BlockGetter} (no world access, safe off-thread). */
    private static final class Sections implements BlockGetter {
        private final int minY, height, minSection;
        private final LevelChunkSection[] sections;

        Sections(int minY, int height, List<SerializableChunkData.SectionData> data) {
            this.minY = minY;
            this.height = height;
            this.minSection = minY >> 4;
            this.sections = new LevelChunkSection[height >> 4];
            for (SerializableChunkData.SectionData s : data) {
                int i = s.y() - minSection;
                if (i >= 0 && i < sections.length && s.chunkSection() != null) sections[i] = s.chunkSection();
            }
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            int i = (pos.getY() >> 4) - minSection;
            if (i < 0 || i >= sections.length || sections[i] == null) return Blocks.AIR.defaultBlockState();
            return sections[i].getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getMinY() {
            return minY;
        }

        /** First free Y above the highest non-air block of the column (local x, z). */
        int top(int x, int z) {
            for (int i = sections.length - 1; i >= 0; i--) {
                LevelChunkSection s = sections[i];
                if (s == null || s.hasOnlyAir()) continue;
                for (int y = 15; y >= 0; y--) {
                    if (!s.getBlockState(x & 15, y, z & 15).isAir()) return ((i + minSection) << 4) + y + 1;
                }
            }
            return minY;
        }

        /** Biome id at the middle of the chunk at height y, or null. */
        @Nullable String biome(int y) {
            int i = Math.max(0, Math.min(sections.length - 1, (y >> 4) - minSection));
            if (sections[i] == null) return null;
            return sections[i].getNoiseBiome(2, (y >> 2) & 3, 2).unwrapKey().map(k -> k.identifier().toString()).orElse(null);
        }
    }
}
