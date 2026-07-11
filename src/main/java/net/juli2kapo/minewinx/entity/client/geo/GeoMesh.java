package net.juli2kapo.minewinx.entity.client.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Cargador/renderer mínimo de modelos Bedrock/GeckoLib (.geo.json) para las
 * plantas PvZ. Vanilla no soporta UVs por cara, así que parseamos la geometría
 * en runtime y emitimos los quads a mano. Solo geometría estática + rotación
 * de huesos por nombre (para animación programática); sin sistema de animación.
 */
public class GeoMesh {

    private static final Map<ResourceLocation, GeoMesh> CACHE = new HashMap<>();

    public final List<Bone> roots = new ArrayList<>();

    public static GeoMesh get(ResourceLocation location) {
        return CACHE.computeIfAbsent(location, GeoMesh::load);
    }

    /** Rotación extra por hueso (radianes, ZYX como vanilla); null = sin extra. */
    public interface BoneAnimator extends BiFunction<String, Float, Vector3f> {}

    public static class Quad {
        final Vector3f[] pos = new Vector3f[4];
        final float[] u = new float[4];
        final float[] v = new float[4];
        final Vector3f normal;

        Quad(Vector3f[] pos, float[] u, float[] v, Vector3f normal) {
            System.arraycopy(pos, 0, this.pos, 0, 4);
            System.arraycopy(u, 0, this.u, 0, 4);
            System.arraycopy(v, 0, this.v, 0, 4);
            this.normal = normal;
        }
    }

    public static class Bone {
        public final String name;
        final Vector3f pivot;      // en unidades geo (px)
        final Vector3f rotation;   // grados, del archivo
        final List<Quad> quads = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();

        Bone(String name, Vector3f pivot, Vector3f rotation) {
            this.name = name;
            this.pivot = pivot;
            this.rotation = rotation;
        }
    }

    private static GeoMesh load(ResourceLocation location) {
        try (InputStreamReader reader = new InputStreamReader(
                Minecraft.getInstance().getResourceManager().getResource(location).orElseThrow().open(),
                StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            JsonObject description = geometry.getAsJsonObject("description");
            float texW = description.get("texture_width").getAsFloat();
            float texH = description.get("texture_height").getAsFloat();

            GeoMesh mesh = new GeoMesh();
            Map<String, Bone> byName = new HashMap<>();
            for (JsonElement boneEl : geometry.getAsJsonArray("bones")) {
                JsonObject boneJson = boneEl.getAsJsonObject();
                String name = boneJson.get("name").getAsString();
                Bone bone = new Bone(name, vec(boneJson, "pivot"), vec(boneJson, "rotation"));
                byName.put(name, bone);
                if (boneJson.has("parent")) {
                    Bone parent = byName.get(boneJson.get("parent").getAsString());
                    if (parent != null) parent.children.add(bone);
                    else mesh.roots.add(bone);
                } else {
                    mesh.roots.add(bone);
                }
                if (boneJson.has("cubes")) {
                    for (JsonElement cubeEl : boneJson.getAsJsonArray("cubes")) {
                        bakeCube(bone, cubeEl.getAsJsonObject(), texW, texH);
                    }
                }
            }
            return mesh;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar el geo model " + location, e);
        }
    }

    private static Vector3f vec(JsonObject obj, String key) {
        if (!obj.has(key)) return new Vector3f();
        JsonArray arr = obj.getAsJsonArray(key);
        return new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
    }

    private static void bakeCube(Bone bone, JsonObject cube, float texW, float texH) {
        Vector3f origin = vec(cube, "origin");
        Vector3f size = vec(cube, "size");
        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0F;
        Vector3f min = new Vector3f(origin.x - inflate, origin.y - inflate, origin.z - inflate);
        Vector3f max = new Vector3f(origin.x + size.x + inflate, origin.y + size.y + inflate, origin.z + size.z + inflate);

        // Rotación por cubo: se hornea en los vértices (es estática)
        Matrix4f cubeMatrix = new Matrix4f();
        if (cube.has("rotation")) {
            Vector3f rot = vec(cube, "rotation");
            Vector3f pivot = vec(cube, "pivot");
            cubeMatrix.translate(pivot.x, pivot.y, pivot.z);
            cubeMatrix.rotate(new Quaternionf().rotationZYX(
                    (float) Math.toRadians(rot.z), (float) Math.toRadians(rot.y), (float) Math.toRadians(rot.x)));
            cubeMatrix.translate(-pivot.x, -pivot.y, -pivot.z);
        }

        JsonElement uvEl = cube.get("uv");
        if (uvEl != null && uvEl.isJsonObject()) {
            JsonObject faces = uvEl.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : faces.entrySet()) {
                JsonObject face = entry.getValue().getAsJsonObject();
                JsonArray uv = face.getAsJsonArray("uv");
                JsonArray uvSize = face.getAsJsonArray("uv_size");
                addFace(bone, entry.getKey(), min, max, cubeMatrix,
                        uv.get(0).getAsFloat(), uv.get(1).getAsFloat(),
                        uvSize.get(0).getAsFloat(), uvSize.get(1).getAsFloat(), texW, texH);
            }
        } else if (uvEl != null) {
            // UV de caja clásico (bedrock box mapping)
            JsonArray uv = uvEl.getAsJsonArray();
            float u = uv.get(0).getAsFloat(), v = uv.get(1).getAsFloat();
            float sx = size.x, sy = size.y, sz = size.z;
            addFace(bone, "north", min, max, cubeMatrix, u + sz, v + sz, sx, sy, texW, texH);
            addFace(bone, "east", min, max, cubeMatrix, u, v + sz, sz, sy, texW, texH);
            addFace(bone, "south", min, max, cubeMatrix, u + sz * 2 + sx, v + sz, sx, sy, texW, texH);
            addFace(bone, "west", min, max, cubeMatrix, u + sz + sx, v + sz, sz, sy, texW, texH);
            addFace(bone, "up", min, max, cubeMatrix, u + sz, v, sx, sz, texW, texH);
            addFace(bone, "down", min, max, cubeMatrix, u + sz + sx, v, sx, -sz, texW, texH);
        }
    }

    /**
     * Esquinas por cara, convención bedrock: TL/TR/BR/BL vistos desde afuera de
     * la cara; u crece de TL a TR, v crece de TL a BL.
     */
    private static void addFace(Bone bone, String faceName, Vector3f min, Vector3f max, Matrix4f cubeMatrix,
                                float u0, float v0, float uSize, float vSize, float texW, float texH) {
        Vector3f tl, tr, br, bl, normal;
        switch (faceName) {
            case "north" -> { // z = min.z, visto desde -z
                tl = new Vector3f(max.x, max.y, min.z); tr = new Vector3f(min.x, max.y, min.z);
                br = new Vector3f(min.x, min.y, min.z); bl = new Vector3f(max.x, min.y, min.z);
                normal = new Vector3f(0, 0, -1);
            }
            case "south" -> { // z = max.z
                tl = new Vector3f(min.x, max.y, max.z); tr = new Vector3f(max.x, max.y, max.z);
                br = new Vector3f(max.x, min.y, max.z); bl = new Vector3f(min.x, min.y, max.z);
                normal = new Vector3f(0, 0, 1);
            }
            case "east" -> { // x = max.x
                tl = new Vector3f(max.x, max.y, max.z); tr = new Vector3f(max.x, max.y, min.z);
                br = new Vector3f(max.x, min.y, min.z); bl = new Vector3f(max.x, min.y, max.z);
                normal = new Vector3f(1, 0, 0);
            }
            case "west" -> { // x = min.x
                tl = new Vector3f(min.x, max.y, min.z); tr = new Vector3f(min.x, max.y, max.z);
                br = new Vector3f(min.x, min.y, max.z); bl = new Vector3f(min.x, min.y, min.z);
                normal = new Vector3f(-1, 0, 0);
            }
            case "up" -> { // y = max.y, visto desde arriba
                tl = new Vector3f(min.x, max.y, min.z); tr = new Vector3f(max.x, max.y, min.z);
                br = new Vector3f(max.x, max.y, max.z); bl = new Vector3f(min.x, max.y, max.z);
                normal = new Vector3f(0, 1, 0);
            }
            case "down" -> { // y = min.y
                tl = new Vector3f(min.x, min.y, max.z); tr = new Vector3f(max.x, min.y, max.z);
                br = new Vector3f(max.x, min.y, min.z); bl = new Vector3f(min.x, min.y, min.z);
                normal = new Vector3f(0, -1, 0);
            }
            default -> { return; }
        }

        Vector3f[] pos = {tl, tr, br, bl};
        for (Vector3f p : pos) {
            Vector4f transformed = cubeMatrix.transform(new Vector4f(p.x, p.y, p.z, 1.0F));
            p.set(transformed.x, transformed.y, transformed.z);
        }
        Vector3f n = cubeMatrix.transformDirection(new Vector3f(normal)).normalize();

        float u1 = (u0 + uSize) / texW, v1 = (v0 + vSize) / texH;
        float nu0 = u0 / texW, nv0 = v0 / texH;
        float[] us = {nu0, u1, u1, nu0};
        float[] vs = {nv0, nv0, v1, v1};
        bone.quads.add(new Quad(pos, us, vs, n));
    }

    // ------------------------------------------------------------ render

    private static final float SCALE = 1.0F / 16.0F;

    /**
     * @param animator devuelve rotación extra (radianes ZYX) por nombre de hueso,
     *                 o null. Recibe (nombre, ageInTicks).
     */
    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                       float ageInTicks, @Nullable BoneAnimator animator,
                       float red, float green, float blue, float alpha) {
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        for (Bone root : roots) {
            renderBone(root, poseStack, buffer, packedLight, packedOverlay, ageInTicks, animator, red, green, blue, alpha);
        }
        poseStack.popPose();
    }

    private void renderBone(Bone bone, PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                            float ageInTicks, @Nullable BoneAnimator animator,
                            float red, float green, float blue, float alpha) {
        poseStack.pushPose();

        Vector3f extra = animator == null ? null : animator.apply(bone.name, ageInTicks);
        boolean hasBaseRot = bone.rotation.lengthSquared() > 1.0E-6F;
        if (hasBaseRot || extra != null) {
            poseStack.translate(bone.pivot.x, bone.pivot.y, bone.pivot.z);
            if (hasBaseRot) {
                poseStack.mulPose(new Quaternionf().rotationZYX(
                        (float) Math.toRadians(bone.rotation.z),
                        (float) Math.toRadians(bone.rotation.y),
                        (float) Math.toRadians(bone.rotation.x)));
            }
            if (extra != null) {
                poseStack.mulPose(new Quaternionf().rotationZYX(extra.z, extra.y, extra.x));
            }
            poseStack.translate(-bone.pivot.x, -bone.pivot.y, -bone.pivot.z);
        }

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalPose = poseStack.last().normal();
        for (Quad quad : bone.quads) {
            Vector3f n = normalPose.transform(new Vector3f(quad.normal));
            for (int i = 0; i < 4; i++) {
                Vector4f p = pose.transform(new Vector4f(quad.pos[i].x, quad.pos[i].y, quad.pos[i].z, 1.0F));
                buffer.vertex(p.x, p.y, p.z, red, green, blue, alpha, quad.u[i], quad.v[i],
                        packedOverlay, packedLight, n.x, n.y, n.z);
            }
        }

        for (Bone child : bone.children) {
            renderBone(child, poseStack, buffer, packedLight, packedOverlay, ageInTicks, animator, red, green, blue, alpha);
        }
        poseStack.popPose();
    }
}
