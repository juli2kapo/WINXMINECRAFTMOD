package net.juli2kapo.factoryascent.trains.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.slf4j.Logger;

/**
 * The rolling stock's part models and layouts. tools/features/trains.py cuts every part into 48-px
 * cells and writes assets/factoryascent/trains/parts.json: the parts (cells and their offsets in
 * train pixels, translucency), each vehicle's layout (static parts, glass, wheelsets and bogies,
 * half length) and the steam engine's motion numbers. This class reads it, registers every cell as
 * a standalone model and draws parts in train pixel space.
 */
public final class TrainModels {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MANIFEST = "/assets/factoryascent/trains/parts.json";

    record Cell(StandaloneModelKey<BlockStateModelPart> key, float x, float y, float z) {}

    record Part(boolean translucent, List<Cell> cells) {}

    /** A wheelset or bogie: which part, at what height and position along the vehicle (train pixels). */
    record Placed(String part, float y, float z) {}

    record Layout(List<String> body, List<String> glass, List<Placed> wheels, List<Placed> bogies, float half) {}

    private static final Map<String, Part> PARTS = new LinkedHashMap<>();
    private static final Map<String, Layout> LAYOUTS = new HashMap<>();
    static float crank = 2.2f, cylinderY = 5f, mainrod = 6f, hopperFloor = 12.5f, hopperTop = 21.8f;
    static final List<float[]> FANS = new ArrayList<>();

    static {
        load();
    }

    private TrainModels() {}

    private static void load() {
        try (InputStream in = TrainModels.class.getResourceAsStream(MANIFEST)) {
            if (in == null) {
                LOGGER.error("Train part manifest {} is missing: trains will be invisible (run tools/gen_resources.py)", MANIFEST);
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("parts").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                List<Cell> cells = new ArrayList<>();
                for (JsonElement c : o.getAsJsonArray("cells")) {
                    JsonObject co = c.getAsJsonObject();
                    String model = co.get("model").getAsString();
                    JsonArray off = co.getAsJsonArray("offset");
                    cells.add(new Cell(new StandaloneModelKey<>(() -> model), off.get(0).getAsFloat(), off.get(1).getAsFloat(),
                            off.get(2).getAsFloat()));
                }
                PARTS.put(e.getKey(), new Part(o.get("translucent").getAsBoolean(), cells));
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("layout").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                LAYOUTS.put(e.getKey(), new Layout(strings(o.getAsJsonArray("body")),
                        o.has("glass") ? strings(o.getAsJsonArray("glass")) : List.of(), placed(o.getAsJsonArray("wheels")),
                        placed(o.getAsJsonArray("bogies")), o.get("half").getAsFloat()));
            }
            JsonObject m = root.getAsJsonObject("motion");
            crank = m.get("crank").getAsFloat();
            cylinderY = m.get("cylinder_y").getAsFloat();
            mainrod = m.get("mainrod").getAsFloat();
            hopperFloor = m.get("hopper_floor").getAsFloat();
            hopperTop = m.get("hopper_top").getAsFloat();
            for (JsonElement f : m.getAsJsonArray("fans")) {
                JsonArray a = f.getAsJsonArray();
                FANS.add(new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat()});
            }
        } catch (Exception ex) {
            LOGGER.error("Could not read the train part manifest", ex);
        }
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }

    private static List<Placed> placed(JsonArray a) {
        List<Placed> out = new ArrayList<>();
        for (JsonElement e : a) {
            JsonArray p = e.getAsJsonArray();
            out.add(new Placed(p.get(0).getAsString(), p.get(1).getAsFloat(), p.get(2).getAsFloat()));
        }
        return out;
    }

    static Layout layout(String vehicle) {
        return LAYOUTS.get(vehicle);
    }

    static void registerModels(ModelEvent.RegisterStandalone event) {
        for (Part part : PARTS.values()) {
            for (Cell cell : part.cells()) {
                event.register(cell.key(), SimpleUnbakedStandaloneModel.simpleModelWrapper(Identifier.parse(cell.key().getName())));
            }
        }
    }

    /** Draws a part; the pose is in blocks with its origin at the vehicle's feet (or wherever the caller moved it). */
    static void draw(PoseStack pose, SubmitNodeCollector collector, String name, int light) {
        Part part = PARTS.get(name);
        if (part == null) return;
        var manager = Minecraft.getInstance().getModelManager();
        for (Cell cell : part.cells()) {
            BlockStateModelPart model = manager.getStandaloneModel(cell.key());
            if (model == null) continue;
            pose.pushPose();
            pose.translate(cell.x() / 16f, cell.y() / 16f, cell.z() / 16f);
            collector.submitBlockModel(pose, part.translucent() ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet(),
                    List.of(model), new int[0], light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
