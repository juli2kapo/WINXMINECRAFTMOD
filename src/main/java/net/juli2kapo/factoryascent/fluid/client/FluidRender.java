package net.juli2kapo.factoryascent.fluid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/** Drawing fluids with their own still/flowing textures: GUI gauges and in-world boxes (tanks, pipes). */
public final class FluidRender {
    private FluidRender() {}

    public static @Nullable FluidModel model(Fluid fluid) {
        if (fluid == Fluids.EMPTY) return null;
        try {
            return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static @Nullable TextureAtlasSprite still(Fluid fluid) {
        FluidModel m = model(fluid);
        return m == null ? null : m.stillMaterial().sprite();
    }

    public static @Nullable TextureAtlasSprite flowing(Fluid fluid) {
        FluidModel m = model(fluid);
        return m == null ? null : m.flowingMaterial().sprite();
    }

    /** ARGB tint (opaque white when the texture carries its own colour). */
    public static int tint(Fluid fluid) {
        FluidModel m = model(fluid);
        if (m == null || m.fluidTintSource() == null) return 0xFFFFFFFF;
        return 0xFF000000 | m.fluidTintSource().color(fluid.defaultFluidState());
    }

    /** A vertical gauge filled from the bottom with the fluid's still texture (tiled 16×16). */
    public static void gauge(GuiGraphicsExtractor g, int x, int y, int w, int h, Fluid fluid, int amount, int capacity) {
        if (fluid == Fluids.EMPTY || amount <= 0 || capacity <= 0) return;
        TextureAtlasSprite sprite = still(fluid);
        if (sprite == null) return;
        int filled = Math.max(1, Math.round(h * Math.min(1f, (float) amount / capacity)));
        int color = tint(fluid);
        int top = y + h - filled;
        g.enableScissor(x, top, x + w, y + h);
        for (int ty = y + h - 16; ty > top - 16; ty -= 16) {
            for (int tx = x; tx < x + w; tx += 16) g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, tx, ty, 16, 16, color);
        }
        g.disableScissor();
    }

    /**
     * An axis-aligned box of fluid (block coordinates 0..1) on the given buffer (an entity
     * translucent type over the block atlas), each face mapped to the sprite.
     */
    public static void box(PoseStack.Pose pose, VertexConsumer b, TextureAtlasSprite sprite, int color, int light,
                           float x0, float y0, float z0, float x1, float y1, float z1, boolean top, boolean bottom) {
        float u0 = sprite.getU0(), v0 = sprite.getV0();
        float du = sprite.getU1() - u0, dv = sprite.getV1() - v0;
        // sides: u along the face, v from the top down (clamped to one tile)
        float hu = Math.min(1f, y1 - y0);
        quad(pose, b, color, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
                u0, v0 + dv * hu, u0 + du * (x1 - x0), v0, sprite);
        quad(pose, b, color, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                u0, v0 + dv * hu, u0 + du * (x1 - x0), v0, sprite);
        quad(pose, b, color, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                u0, v0 + dv * hu, u0 + du * (z1 - z0), v0, sprite);
        quad(pose, b, color, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                u0, v0 + dv * hu, u0 + du * (z1 - z0), v0, sprite);
        if (top) quad(pose, b, color, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
                u0, v0 + dv * (z1 - z0), u0 + du * (x1 - x0), v0, sprite);
        if (bottom) quad(pose, b, color, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                u0, v0 + dv * (z1 - z0), u0 + du * (x1 - x0), v0, sprite);
    }

    /** Corners in order: bottom-left, bottom-right, top-right, top-left (as seen from outside). */
    private static void quad(PoseStack.Pose pose, VertexConsumer b, int color, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, float uMin, float vMax, float uMax, float vMin, TextureAtlasSprite s) {
        vertex(pose, b, ax, ay, az, uMin, vMax, color, light, nx, ny, nz);
        vertex(pose, b, bx, by, bz, uMax, vMax, color, light, nx, ny, nz);
        vertex(pose, b, cx, cy, cz, uMax, vMin, color, light, nx, ny, nz);
        vertex(pose, b, dx, dy, dz, uMin, vMin, color, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer b, float x, float y, float z, float u, float v, int color, int light,
                               float nx, float ny, float nz) {
        b.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
