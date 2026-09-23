package net.juli2kapo.minewinx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Malla de alas horneada desde los builds voxel (tools/wings/bake.js).
 * Cada ala (izquierda/derecha) es una lista de quads con color por vértice;
 * se dibuja sin textura real (una blanca) para no depender del atlas.
 */
public class WingMesh {

    private static final Map<String, Optional<WingMesh>> CACHE = new HashMap<>();

    /** Tamaño máximo de las alas en bloques (envergadura / alto / cuánto bajan del hombro). */
    private static final float MAX_SPAN = 2.4F;
    private static final float MAX_HEIGHT = 2.2F;
    private static final float MAX_BELOW_ROOT = 1.0F;

    private final float scale;
    private final Side[] sides = new Side[2];

    private static class Side {
        float[] pos;      // 4 vértices * xyz por quad, ya centrados en el pivote y escalados (y hacia abajo)
        float[] normal;   // xyz por quad
        int[] color;      // ARGB por quad
        boolean[] emissive;
        boolean[] translucent;
    }

    @Nullable
    public static WingMesh get(String element) {
        String key = element.toLowerCase(Locale.ROOT);
        return CACHE.computeIfAbsent(key, WingMesh::load).orElse(null);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static Optional<WingMesh> load(String element) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(MineWinx.MOD_ID, "wings/" + element + ".bin");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            MineWinx.LOGGER.warn("[Wings] no hay malla para '{}' ({})", element, location);
            return Optional.empty();
        }
        try (InputStream in = resource.get().open(); DataInputStream data = new DataInputStream(in)) {
            return Optional.of(new WingMesh(data));
        } catch (Exception e) {
            MineWinx.LOGGER.error("[Wings] error cargando {}", location, e);
            return Optional.empty();
        }
    }

    private WingMesh(DataInputStream data) throws Exception {
        byte[] magic = new byte[4];
        data.readFully(magic);
        if (magic[0] != 'W' || magic[1] != 'I' || magic[2] != 'N' || magic[3] != 'G' || data.readUnsignedByte() != 1) {
            throw new IllegalStateException("formato de alas desconocido");
        }
        float px = data.readFloat(), py = data.readFloat(), pz = data.readFloat();
        float minX = data.readFloat(), minY = data.readFloat();
        data.readFloat(); // minZ
        float maxX = data.readFloat(), maxY = data.readFloat();
        data.readFloat(); // maxZ

        this.scale = Math.min(MAX_SPAN / (maxX - minX),
                Math.min(MAX_HEIGHT / (maxY - minY), MAX_BELOW_ROOT / Math.max(1.0F, py - minY)));

        int paletteSize = data.readUnsignedByte();
        int[] paletteColor = new int[paletteSize];
        boolean[] paletteEmissive = new boolean[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            int r = data.readUnsignedByte(), g = data.readUnsignedByte(), b = data.readUnsignedByte(), a = data.readUnsignedByte();
            paletteColor[i] = (a << 24) | (r << 16) | (g << 8) | b;
            paletteEmissive[i] = (data.readUnsignedByte() & 1) != 0;
        }

        for (int s = 0; s < 2; s++) {
            int count = data.readInt();
            Side side = new Side();
            side.pos = new float[count * 12];
            side.normal = new float[count * 3];
            side.color = new int[count];
            side.emissive = new boolean[count];
            side.translucent = new boolean[count];
            float[] corner = new float[3];
            for (int q = 0; q < count; q++) {
                int dir = data.readUnsignedByte();
                int plane = data.readUnsignedByte();
                int u0 = data.readUnsignedByte(), v0 = data.readUnsignedByte();
                int u1 = data.readUnsignedByte(), v1 = data.readUnsignedByte();
                int pal = data.readUnsignedByte();

                int axis = dir >> 1;
                int ua = axis == 0 ? 1 : 0;
                int va = axis == 2 ? 1 : 2;
                int[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                for (int c = 0; c < 4; c++) {
                    corner[axis] = plane;
                    corner[ua] = uv[c][0];
                    corner[va] = uv[c][1];
                    // Espacio del modelo del jugador: y hacia abajo, +z = espalda.
                    side.pos[q * 12 + c * 3] = (corner[0] - px) * scale;
                    side.pos[q * 12 + c * 3 + 1] = -(corner[1] - py) * scale;
                    side.pos[q * 12 + c * 3 + 2] = (corner[2] - pz) * scale;
                }
                float sign = (dir & 1) == 0 ? -1.0F : 1.0F;
                side.normal[q * 3 + axis] = axis == 1 ? -sign : sign;
                side.color[q] = paletteColor[pal];
                side.emissive[q] = paletteEmissive[pal];
                side.translucent[q] = (paletteColor[pal] >>> 24) < 255;
            }
            sides[s] = side;
        }
    }

    /**
     * Dibuja las dos alas en el pose actual (origen = raíz de las alas).
     * @param sweepDegrees cuánto se abren hacia atrás las puntas (aleteo)
     * @param translucentPass false = partes opacas, true = vidrio/agua
     */
    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, float sweepDegrees, boolean translucentPass) {
        for (int s = 0; s < 2; s++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(s == 0 ? sweepDegrees : -sweepDegrees));
            renderSide(sides[s], poseStack, buffer, packedLight, translucentPass);
            poseStack.popPose();
        }
    }

    private static void renderSide(Side side, PoseStack poseStack, VertexConsumer buffer, int packedLight, boolean translucentPass) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalPose = poseStack.last().normal();
        Vector4f p = new Vector4f();
        Vector3f n = new Vector3f();
        int quads = side.color.length;
        for (int q = 0; q < quads; q++) {
            if (side.translucent[q] != translucentPass) continue;
            int argb = side.color[q];
            float a = (argb >>> 24) / 255.0F, r = ((argb >> 16) & 255) / 255.0F,
                    g = ((argb >> 8) & 255) / 255.0F, b = (argb & 255) / 255.0F;
            int light = side.emissive[q] ? LightTexture.FULL_BRIGHT : packedLight;
            normalPose.transform(n.set(side.normal[q * 3], side.normal[q * 3 + 1], side.normal[q * 3 + 2]));
            for (int c = 0; c < 4; c++) {
                int i = q * 12 + c * 3;
                pose.transform(p.set(side.pos[i], side.pos[i + 1], side.pos[i + 2], 1.0F));
                buffer.vertex(p.x, p.y, p.z, r, g, b, a, 0.5F, 0.5F,
                        OverlayTexture.NO_OVERLAY, light, n.x, n.y, n.z);
            }
        }
    }
}
