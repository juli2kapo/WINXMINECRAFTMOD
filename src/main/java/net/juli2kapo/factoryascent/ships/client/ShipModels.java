package net.juli2kapo.factoryascent.ships.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * The ships' part models. tools/features/ships.py cuts every part into 48-px cells (one block
 * model may only span 48 px) and writes assets/factoryascent/ships/parts.json: per part, whether it
 * is translucent and, per cell, the model and where its origin sits in ship pixels. This class
 * reads that manifest from the mod's resources, registers every cell as a standalone model, and
 * draws a whole part in ship pixel space.
 */
public final class ShipModels {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MANIFEST = "/assets/factoryascent/ships/parts.json";

    record Cell(StandaloneModelKey<BlockStateModelPart> key, float x, float y, float z) {}

    record Part(boolean translucent, List<Cell> cells) {}

    private static final Map<String, Part> PARTS = load();

    private ShipModels() {}

    private static Map<String, Part> load() {
        Map<String, Part> parts = new LinkedHashMap<>();
        try (InputStream in = ShipModels.class.getResourceAsStream(MANIFEST)) {
            if (in == null) {
                LOGGER.error("Ship part manifest {} is missing: ships will be invisible (run tools/gen_resources.py)", MANIFEST);
                return parts;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                List<Cell> cells = new ArrayList<>();
                for (JsonElement c : o.getAsJsonArray("cells")) {
                    JsonObject co = c.getAsJsonObject();
                    String model = co.get("model").getAsString();
                    var off = co.getAsJsonArray("offset");
                    cells.add(new Cell(new StandaloneModelKey<>(() -> model), off.get(0).getAsFloat(), off.get(1).getAsFloat(),
                            off.get(2).getAsFloat()));
                }
                parts.put(e.getKey(), new Part(o.get("translucent").getAsBoolean(), cells));
            }
        } catch (Exception ex) {
            LOGGER.error("Could not read the ship part manifest", ex);
        }
        return parts;
    }

    static void registerModels(ModelEvent.RegisterStandalone event) {
        for (Part part : PARTS.values()) {
            for (Cell cell : part.cells()) {
                String id = cell.key().getName();
                event.register(cell.key(), SimpleUnbakedStandaloneModel.simpleModelWrapper(Identifier.parse(id)));
            }
        }
    }

    /** Draws a part; the pose is in ship pixels' block space (1 unit = 1 block, origin = the entity's feet). */
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
