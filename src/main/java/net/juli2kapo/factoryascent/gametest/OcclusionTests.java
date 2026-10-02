package net.juli2kapo.factoryascent.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.juli2kapo.factoryascent.FactoryAscent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks whose model leaves part of an outer face open (data/factoryascent/dev/open_faces.json,
 * written by tools/gen_resources.py) must not occlude as a full cube: if they did, the faces of
 * the blocks touching them would be culled and you would see straight through the world there.
 */
public final class OcclusionTests {
    private OcclusionTests() {}

    public static void openModelsDoNotOccludeNeighbours(GameTestHelper h) {
        List<String> bad = new ArrayList<>();
        for (Identifier id : openFaceBlocks(h)) {
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                if (state.canOcclude() && Block.isShapeFullBlock(state.getOcclusionShape())) {
                    bad.add(id.getPath());
                    break;
                }
            }
        }
        h.assertTrue(bad.isEmpty(), "these blocks have open model faces but occlude like a full cube "
                + "(add noOcclusion): " + bad);
        h.succeed();
    }

    private static List<Identifier> openFaceBlocks(GameTestHelper h) {
        String path = "/data/" + FactoryAscent.MOD_ID + "/dev/open_faces.json";
        List<Identifier> out = new ArrayList<>();
        try (InputStream in = OcclusionTests.class.getResourceAsStream(path)) {
            h.assertTrue(in != null, "missing " + path + " (run tools/gen_resources.py)");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            json.getAsJsonArray("blocks").forEach(e -> out.add(Identifier.parse(e.getAsString())));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        h.assertTrue(!out.isEmpty(), "the open-face list is empty");
        return out;
    }
}
