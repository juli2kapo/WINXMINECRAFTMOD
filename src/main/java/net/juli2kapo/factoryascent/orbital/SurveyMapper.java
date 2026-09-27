package net.juli2kapo.factoryascent.orbital;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.common.Tags;

/**
 * Survey maps: a vanilla filled map centred on a Ground Station, filled in from orbit instead of
 * by walking around.
 *
 * <ul>
 * <li>Chunks that are loaded are drawn from the real blocks, like a vanilla map.</li>
 * <li>Chunks that aren't are drawn from the world generator's terrain height and biome (a
 *     "satellite photo" of the land as generated): nothing gets loaded or generated for it.</li>
 * <li>The work is spread over ticks with a time budget ({@link #BUDGET_NANOS} per tick), so the
 *     map paints itself in over a second or so and the server never stalls.</li>
 * <li>Loaded chunks near the station with the most ore get a red X marker.</li>
 * </ul>
 */
public final class SurveyMapper {
    /** 2 → 4 blocks per pixel, 512×512 blocks. */
    public static final byte SCALE = 2;
    private static final long BUDGET_NANOS = 8_000_000L;
    /** Chunk radius around the station searched for ore (loaded chunks only). */
    private static final int ORE_RADIUS = 6;
    private static final int ORE_MARKERS = 5;
    private static final int ORE_RICH = 16;

    private static final List<Job> JOBS = new ArrayList<>();

    private SurveyMapper() {}

    /** Makes the map and queues its painting. */
    public static ItemStack create(ServerLevel level, BlockPos station) {
        MapItemSavedData data = centred(level, station);
        MapId id = level.getFreeMapId();
        level.setMapData(id, data);
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, id);
        map.set(DataComponents.ITEM_NAME, Component.translatable("orbital.factoryascent.survey_map"));
        MapItemSavedData.addTargetDecoration(map, station, "ground_station", MapDecorationTypes.TARGET_POINT);
        int n = 0;
        for (ChunkPos rich : oreRichChunks(level, station)) {
            MapItemSavedData.addTargetDecoration(map, rich.getMiddleBlockPosition(station.getY()), "ore_" + n++, MapDecorationTypes.RED_X);
        }
        JOBS.add(new Job(level, id, data));
        return map;
    }

    /** True while the map is still being painted in. */
    public static boolean isPainting(MapId id) {
        return JOBS.stream().anyMatch(j -> j.id.equals(id));
    }

    static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        long deadline = System.nanoTime() + BUDGET_NANOS;
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
            Job job = it.next();
            if (job.level.getServer() != server) {
                it.remove();
                continue;
            }
            while (job.row < 128 && System.nanoTime() < deadline) job.paintRow();
            if (job.row >= 128) it.remove();
            if (System.nanoTime() >= deadline) break;
        }
    }

    static void clear() {
        JOBS.clear();
    }

    /** Vanilla snaps new maps to a grid; a survey map is centred exactly on the station. */
    private static MapItemSavedData centred(ServerLevel level, BlockPos station) {
        MapItemSavedData fresh = MapItemSavedData.createFresh(station.getX(), station.getZ(), SCALE, true, false, level.dimension());
        Tag encoded = MapItemSavedData.CODEC.encodeStart(NbtOps.INSTANCE, fresh).getOrThrow();
        if (!(encoded instanceof CompoundTag tag)) return fresh;
        tag.putInt("xCenter", station.getX());
        tag.putInt("zCenter", station.getZ());
        return MapItemSavedData.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(fresh);
    }

    // ---------------------------------------------------------------- ore

    private static List<ChunkPos> oreRichChunks(ServerLevel level, BlockPos station) {
        int cx = station.getX() >> 4, cz = station.getZ() >> 4;
        record Rich(ChunkPos pos, int ores) {}
        List<Rich> found = new ArrayList<>();
        for (int dx = -ORE_RADIUS; dx <= ORE_RADIUS; dx++) {
            for (int dz = -ORE_RADIUS; dz <= ORE_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                int ores = countOres(chunk);
                if (ores >= ORE_RICH) found.add(new Rich(chunk.getPos(), ores));
            }
        }
        return found.stream().sorted(Comparator.comparingInt(Rich::ores).reversed()).limit(ORE_MARKERS).map(Rich::pos).toList();
    }

    /** Ore blocks in a chunk, not counting coal (which is everywhere). Palette checks skip ore-free sections. */
    static int countOres(LevelChunk chunk) {
        int[] total = {0};
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir() || !section.maybeHas(SurveyMapper::isValuableOre)) continue;
            section.getStates().count((state, count) -> {
                if (isValuableOre(state)) total[0] += count;
            });
        }
        return total[0];
    }

    private static boolean isValuableOre(BlockState state) {
        return state.is(Tags.Blocks.ORES) && !state.is(Tags.Blocks.ORES_COAL);
    }

    // ---------------------------------------------------------------- painting

    private static final class Job {
        final ServerLevel level;
        final MapId id;
        final MapItemSavedData data;
        final int scale;
        final int minX, minZ;
        final boolean ceiling;
        final int seaLevel;
        /** Terrain height of the pixel above (previous row), for the vanilla-style relief shading. */
        final double[] previous = new double[128];
        /** Generator estimates for unloaded ground, per 8×8 block cell: height << 8 | color id. */
        final Long2LongOpenHashMap estimates = new Long2LongOpenHashMap();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        /** Row being painted; starts at -1 to seed the relief shading. */
        int row = -1;

        Job(ServerLevel level, MapId id, MapItemSavedData data) {
            this.level = level;
            this.id = id;
            this.data = data;
            this.scale = 1 << data.scale;
            this.minX = data.centerX - 64 * scale;
            this.minZ = data.centerZ - 64 * scale;
            this.ceiling = level.dimensionType().hasCeiling();
            this.seaLevel = level.getSeaLevel();
        }

        void paintRow() {
            int imgY = row++;
            int z = minZ + imgY * scale + scale / 2;
            for (int imgX = 0; imgX < 128; imgX++) {
                int x = minX + imgX * scale + scale / 2;
                MapColor color;
                double height;
                int waterDepth = 0;
                if (ceiling) {
                    // Same as vanilla maps under a ceiling: a dirt/stone speckle.
                    int noise = x + z * 231871;
                    noise = noise * noise * 31287121 + noise * 11;
                    color = (noise >> 20 & 1) == 0 ? MapColor.DIRT : MapColor.STONE;
                    height = 100;
                } else {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk != null) {
                        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
                        BlockState state = Blocks.BEDROCK.defaultBlockState();
                        if (y > level.getMinY()) {
                            do {
                                pos.set(x, --y, z);
                                state = chunk.getBlockState(pos);
                            } while (state.getMapColor(level, pos) == MapColor.NONE && y > level.getMinY());
                            if (!state.getFluidState().isEmpty()) {
                                int below = y - 1;
                                BlockState under;
                                do {
                                    pos.set(x, below--, z);
                                    under = chunk.getBlockState(pos);
                                    waterDepth++;
                                } while (below > level.getMinY() && !under.getFluidState().isEmpty());
                                pos.set(x, y, z);
                                state = state.getFluidState().createLegacyBlock();
                            }
                        }
                        color = state.getMapColor(level, pos);
                        height = y;
                    } else {
                        long est = estimate(x, z);
                        height = (int) (est >> 8);
                        color = MapColor.byId((int) (est & 0xFF));
                        if (color == MapColor.WATER) waterDepth = Math.max(1, (seaLevel - (int) height) / 2);
                        if (color == MapColor.WATER) height = seaLevel;
                    }
                }
                MapColor.Brightness brightness;
                if (color == MapColor.WATER) {
                    double d = waterDepth * 0.1 + ((imgX + imgY) & 1) * 0.2;
                    brightness = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else {
                    double d = (height - previous[imgX]) * 4.0 / (scale + 4) + (((imgX + imgY) & 1) - 0.5) * 0.4;
                    brightness = d > 0.6 ? MapColor.Brightness.HIGH : d < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                previous[imgX] = height;
                if (imgY >= 0) data.setColor(imgX, imgY, color.getPackedId(brightness));
            }
        }

        /** Terrain height and colour of unloaded ground, from the generator; cached per 8×8 cell. */
        private long estimate(int x, int z) {
            long key = ChunkPos.pack(x >> 3, z >> 3);
            long cached = estimates.getOrDefault(key, Long.MIN_VALUE);
            if (cached != Long.MIN_VALUE) return cached;
            int sx = (x & ~7) + 4, sz = (z & ~7) + 4;
            var source = level.getChunkSource();
            int h = source.getGenerator().getBaseHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, level, source.randomState());
            Holder<Biome> biome = level.getUncachedNoiseBiome(sx >> 2, Math.max(h, seaLevel) >> 2, sz >> 2);
            MapColor color = h < seaLevel || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)
                    ? MapColor.WATER : colorOf(biome, h);
            long out = ((long) h << 8) | color.id;
            estimates.put(key, out);
            return out;
        }

        private MapColor colorOf(Holder<Biome> biome, int height) {
            if (biome.is(BiomeTags.IS_NETHER)) return MapColor.NETHER;
            if (biome.is(BiomeTags.IS_END)) return MapColor.SAND;
            if (biome.is(BiomeTags.IS_BADLANDS)) return MapColor.COLOR_ORANGE;
            if (biome.is(Tags.Biomes.IS_DESERT) || biome.is(BiomeTags.IS_BEACH)) return MapColor.SAND;
            if (biome.is(Tags.Biomes.IS_SNOWY) || height > seaLevel + 130) return MapColor.SNOW;
            if (biome.is(Tags.Biomes.IS_MUSHROOM)) return MapColor.COLOR_PURPLE;
            if (biome.is(Tags.Biomes.IS_MOUNTAIN) && height > seaLevel + 60) return MapColor.STONE;
            if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_TAIGA)
                    || biome.is(Tags.Biomes.IS_SWAMP)) {
                return MapColor.PLANT;
            }
            return MapColor.GRASS;
        }
    }
}
