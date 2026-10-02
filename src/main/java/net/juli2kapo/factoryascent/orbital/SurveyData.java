package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * One team's survey imagery of one dimension, shared by the whole team: 16×16 vanilla map colours
 * (packed {@link MapColor} ids with brightness, like a filled map's pixels) per imaged chunk, the
 * chunk's biome, and the heights of its north and south pixel rows (so a chunk imaged later can
 * shade the border with its neighbour like a vanilla map).
 *
 * <p>Ground Stations fill it in ({@link SurveyScanner}); the survey map screen shows it
 * ({@link SurveyService}). Get it with {@link #get(MinecraftServer, String, ResourceKey)}. Stored as
 * {@code data/factoryascent/survey/<team>/<dimension namespace>/<dimension path>.dat} in the
 * overworld's data folder, about 290 bytes per chunk.
 */
public final class SurveyData extends SavedData {
    public static final int PIXELS = 256;

    private record Stored(long[] chunks, ByteBuffer colors, int[] edges, int[] biomes, List<String> palette, long[] approximate) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG_STREAM.fieldOf("chunks").forGetter(s -> LongStream.of(s.chunks())),
                Codec.BYTE_BUFFER.fieldOf("colors").forGetter(Stored::colors),
                Codec.INT_STREAM.fieldOf("edges").forGetter(s -> IntStream.of(s.edges())),
                Codec.INT_STREAM.fieldOf("biomes").forGetter(s -> IntStream.of(s.biomes())),
                Codec.STRING.listOf().fieldOf("palette").forGetter(Stored::palette),
                Codec.LONG_STREAM.optionalFieldOf("approximate").forGetter(s -> s.approximate().length == 0
                        ? java.util.Optional.empty() : java.util.Optional.of(LongStream.of(s.approximate())))
        ).apply(i, (c, col, e, b, p, a) -> new Stored(c.toArray(), col, e.toArray(), b.toArray(), p,
                a.map(LongStream::toArray).orElse(new long[0]))));
    }

    private static final Codec<SurveyData> CODEC = Stored.CODEC.xmap(SurveyData::new, SurveyData::store);
    private static final Map<Identifier, SavedDataType<SurveyData>> TYPES = new HashMap<>();

    private final Long2ObjectOpenHashMap<byte[]> colors = new Long2ObjectOpenHashMap<>();
    /** Per chunk: 16 × (north row height &lt;&lt; 16 | south row height &amp; 0xFFFF). */
    private final Long2ObjectOpenHashMap<int[]> edges = new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap biomes = new Long2IntOpenHashMap();
    /** Chunks imaged approximately from the world generator (not generated yet): re-imaged for real later. */
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet approximate = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();

    private SurveyData() {
        biomes.defaultReturnValue(-1);
    }

    private SurveyData(Stored s) {
        this();
        for (String b : s.palette()) paletteIndex.put(b, palette.size()) ;
        palette.addAll(s.palette());
        ByteBuffer buf = s.colors().duplicate();
        for (int n = 0; n < s.chunks().length; n++) {
            if (buf.remaining() < PIXELS) break;
            byte[] px = new byte[PIXELS];
            buf.get(px);
            long pos = s.chunks()[n];
            colors.put(pos, px);
            if (s.edges().length >= (n + 1) * 16) edges.put(pos, java.util.Arrays.copyOfRange(s.edges(), n * 16, n * 16 + 16));
            if (n < s.biomes().length && s.biomes()[n] >= 0 && s.biomes()[n] < palette.size()) biomes.put(pos, s.biomes()[n]);
        }
        for (long a : s.approximate()) if (colors.containsKey(a)) approximate.add(a);
    }

    private Stored store() {
        long[] keys = colors.keySet().toLongArray();
        ByteBuffer col = ByteBuffer.allocate(keys.length * PIXELS);
        int[] e = new int[keys.length * 16];
        int[] b = new int[keys.length];
        for (int n = 0; n < keys.length; n++) {
            col.put(colors.get(keys[n]));
            int[] edge = edges.get(keys[n]);
            if (edge != null) System.arraycopy(edge, 0, e, n * 16, 16);
            b[n] = biomes.get(keys[n]);
        }
        col.flip();
        return new Stored(keys, col, e, b, List.copyOf(palette), approximate.toLongArray());
    }

    /** The team's survey of that dimension (created empty on first use). */
    public static SurveyData get(MinecraftServer server, String team, ResourceKey<Level> dimension) {
        Identifier id = Identifier.fromNamespaceAndPath(FactoryAscent.MOD_ID, "survey/" + safe(team) + "/"
                + safe(dimension.identifier().getNamespace()) + "/" + safe(dimension.identifier().getPath()));
        SavedDataType<SurveyData> type;
        synchronized (TYPES) {
            type = TYPES.computeIfAbsent(id, k -> new SavedDataType<>(k, SurveyData::new, CODEC));
        }
        return server.getDataStorage().computeIfAbsent(type);
    }

    /** Team keys ("team:foo", a UUID) and dimension paths as file-name-safe path segments. */
    private static String safe(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' ? c : (c == '/' ? '/' : '_'));
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- queries

    public boolean has(long chunk) {
        return colors.containsKey(chunk);
    }

    /** The chunk's 256 packed map colours (index z * 16 + x), or null if not imaged. Don't modify. */
    public byte @Nullable [] colors(long chunk) {
        return colors.get(chunk);
    }

    /** The chunk's biome id ("minecraft:plains"), or null. */
    public @Nullable String biome(long chunk) {
        int i = biomes.get(chunk);
        return i >= 0 && i < palette.size() ? palette.get(i) : null;
    }

    /** True if the chunk's image is only an approximation (the chunk wasn't generated when it was imaged). */
    public boolean approximate(long chunk) {
        return approximate.contains(chunk);
    }

    public int approximateCount() {
        return approximate.size();
    }

    public int count() {
        return colors.size();
    }

    public LongSet chunks() {
        return colors.keySet();
    }

    /** South-row height of pixel column x of an imaged chunk, or Integer.MIN_VALUE. */
    int southHeight(long chunk, int x) {
        int[] e = edges.get(chunk);
        return e == null ? Integer.MIN_VALUE : (short) e[x];
    }

    // ---------------------------------------------------------------- changes

    /**
     * Stores a freshly imaged chunk. {@code north}/{@code south} are the heights of its first and
     * last pixel rows. If the chunk to the south was imaged before, its first row is re-shaded
     * against this chunk's last row. Returns the chunks whose pixels changed.
     */
    long[] put(long chunk, byte[] pixels, int[] north, int[] south, @Nullable String biome) {
        return put(chunk, pixels, north, south, biome, false);
    }

    long[] put(long chunk, byte[] pixels, int[] north, int[] south, @Nullable String biome, boolean approximated) {
        if (approximated) approximate.add(chunk);
        else approximate.remove(chunk);
        colors.put(chunk, pixels);
        int[] e = new int[16];
        for (int x = 0; x < 16; x++) e[x] = (north[x] << 16) | (south[x] & 0xFFFF);
        edges.put(chunk, e);
        if (biome != null) {
            Integer index = paletteIndex.get(biome);
            if (index == null) {
                index = palette.size();
                palette.add(biome);
                paletteIndex.put(biome, index);
            }
            biomes.put(chunk, (int) index);
        }
        setDirty();
        int cx = (int) chunk, cz = (int) (chunk >> 32);
        long southChunk = net.minecraft.world.level.ChunkPos.pack(cx, cz + 1);
        byte[] below = colors.get(southChunk);
        int[] belowEdges = edges.get(southChunk);
        if (below == null || belowEdges == null) return new long[] {chunk};
        for (int x = 0; x < 16; x++) {
            int packed = below[x] & 0xFF;
            MapColor color = MapColor.byId(packed >> 2);
            if (color == MapColor.WATER || color == MapColor.NONE) continue;
            below[x] = color.getPackedId(SurveyScanner.relief(belowEdges[x] >> 16, south[x], x, 0));
        }
        return new long[] {chunk, southChunk};
    }
}
