package net.juli2kapo.factoryascent.orbital.client;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.juli2kapo.factoryascent.orbital.OrbitalPayloads.SurveyTiles;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * The survey imagery the open survey map has received, painted into 256×256-pixel textures of
 * 16×16 chunks each (one pixel per block). Unimaged pixels stay transparent so the map's grid shows
 * through. Everything is dropped when the map closes.
 */
final class SurveyImagery {
    static final int REGION_CHUNKS = 16;
    static final int REGION_PIXELS = REGION_CHUNKS * 16;

    static final class Region {
        final DynamicTexture texture;
        boolean dirty = true;

        Region(long key) {
            texture = new DynamicTexture(() -> "factoryascent survey " + ChunkPos.getX(key) + "," + ChunkPos.getZ(key),
                    REGION_PIXELS, REGION_PIXELS, true);
        }
    }

    private static final Long2ObjectOpenHashMap<Region> REGIONS = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<String> BIOMES = new Long2ObjectOpenHashMap<>();
    private static int chunks;

    private SurveyImagery() {}

    static void reset() {
        for (Region r : REGIONS.values()) r.texture.close();
        REGIONS.clear();
        BIOMES.clear();
        chunks = 0;
    }

    static Long2ObjectOpenHashMap<Region> regions() {
        return REGIONS;
    }

    /** Chunks received so far. */
    static int chunks() {
        return chunks;
    }

    static @Nullable String biome(long chunk) {
        return BIOMES.get(chunk);
    }

    static void accept(SurveyTiles tiles) {
        int n = tiles.chunks().length;
        byte[] raw = inflate(tiles.data(), n * 258);
        if (raw == null) return;
        for (int i = 0; i < n && (i + 1) * 258 <= raw.length; i++) {
            long chunk = tiles.chunks()[i];
            int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
            long key = ChunkPos.pack(Math.floorDiv(cx, REGION_CHUNKS), Math.floorDiv(cz, REGION_CHUNKS));
            Region region = REGIONS.get(key);
            if (region == null) {
                region = new Region(key);
                REGIONS.put(key, region);
            }
            NativeImage image = region.texture.getPixels();
            int ox = Math.floorMod(cx, REGION_CHUNKS) * 16, oz = Math.floorMod(cz, REGION_CHUNKS) * 16;
            int base = i * 258;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int packed = raw[base + z * 16 + x] & 0xFF;
                    image.setPixel(ox + x, oz + z, packed < 4 ? 0 : MapColor.getColorFromPackedId(packed));
                }
            }
            region.dirty = true;
            short b = (short) (((raw[base + 256] & 0xFF) << 8) | (raw[base + 257] & 0xFF));
            if (b >= 0 && b < tiles.biomes().size()) BIOMES.put(chunk, tiles.biomes().get(b));
            if (!BIOMES.containsKey(chunk)) BIOMES.put(chunk, "");
            chunks = BIOMES.size();
        }
    }

    /** Uploads the regions that changed since the last frame. */
    static void upload() {
        for (Region r : REGIONS.values()) {
            if (r.dirty) {
                r.texture.upload();
                r.dirty = false;
            }
        }
    }

    private static byte @Nullable [] inflate(byte[] data, int expected) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(data);
            byte[] out = new byte[expected];
            int len = 0;
            while (len < expected && !inflater.finished()) {
                int got = inflater.inflate(out, len, expected - len);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                len += got;
            }
            return len == expected ? out : null;
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }
}
