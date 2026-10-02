package net.juli2kapo.factoryascent.orbital;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.MapColor;

/** Approximate survey imaging of a chunk that was never generated (listed in ModGameTests.TESTS). */
public final class SurveyApproxTests {
    private SurveyApproxTests() {}

    public static void approximatesUngeneratedChunk(GameTestHelper h) {
        var level = h.getLevel();
        ChunkPos far = new ChunkPos(250_000 >> 4, -250_000 >> 4);
        h.assertTrue(level.getChunkSource().getChunkNow(far.x(), far.z()) == null, "the far chunk must not be loaded");
        SurveyScanner.Image image = SurveyScanner.imageApproximate(level, far, null);
        h.assertTrue(image != null && image.approximate(), "an approximate image is made");
        int coloured = 0;
        for (byte b : image.pixels()) if (MapColor.byId((b & 0xFF) >> 2) != MapColor.NONE) coloured++;
        h.assertTrue(coloured > 200, "the approximate image must be coloured, " + coloured + "/256");
        h.assertTrue(level.getChunkSource().getChunkNow(far.x(), far.z()) == null, "imaging must not load or generate the chunk");
        SurveyData data = SurveyData.get(level.getServer(), "gametest_approx", level.dimension());
        long chunk = far.pack();
        data.put(chunk, image.pixels(), image.north(), image.south(), image.biome(), true);
        h.assertTrue(data.has(chunk) && data.approximate(chunk), "stored as approximate");
        data.put(chunk, image.pixels(), image.north(), image.south(), image.biome(), false);
        h.assertFalse(data.approximate(chunk), "a real image replaces the approximate flag");
        h.succeed();
    }
}
