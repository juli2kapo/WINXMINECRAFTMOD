package net.juli2kapo.factoryascent.guide.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.juli2kapo.factoryascent.guide.GuideMultiblocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Draws a multiblock layout in 3D inside a GUI rectangle (a picture-in-picture render to texture,
 * orthographic): the real block models, rotated by yaw/pitch. With a layer selected, the layers
 * under it are ghosted and the ones above hidden; the hovered block is tinted.
 */
public final class MultiblockPip extends PictureInPictureRenderer<MultiblockPip.State> {

    /**
     * @param scale pixels (GUI units) per block
     * @param layer the layer shown (others ghosted/hidden), or -1 for all
     */
    public record State(GuideMultiblocks.Layout layout, float yaw, float pitch, float scale, int layer,
                        int hoverX, int hoverY, int hoverZ, int x0, int y0, int x1, int y1,
                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
        public State(GuideMultiblocks.Layout layout, float yaw, float pitch, float scale, int layer, int hoverX, int hoverY, int hoverZ,
                     int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
            this(layout, yaw, pitch, scale, layer, hoverX, hoverY, hoverZ, x0, y0, x1, y1, scissorArea,
                    PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    /** The model part of the transform (applied after the GUI's translate + scale(s, s, -s)). */
    static void orient(Matrix4f m, GuideMultiblocks.Layout layout, float yaw, float pitch) {
        m.rotateZ((float) Math.PI);
        m.rotateX((float) Math.toRadians(pitch));
        m.rotateY((float) Math.toRadians(yaw));
        m.translate(-layout.width / 2f, -layout.height / 2f, -layout.depth / 2f);
    }

    /** Block coordinates → GUI coordinates (x, y; z = depth) for a state's rectangle. */
    public static Matrix4f guiMatrix(GuideMultiblocks.Layout layout, float yaw, float pitch, float scale, int x0, int y0, int x1, int y1) {
        Matrix4f m = new Matrix4f().translate((x0 + x1) / 2f, (y0 + y1) / 2f, 0).scale(scale, scale, -scale);
        orient(m, layout, yaw, pitch);
        return m;
    }

    @Override
    protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        Matrix4f m = new Matrix4f();
        orient(m, state.layout, state.yaw, state.pitch);
        pose.mulPose(m);
        GuideMultiblocks.Layout layout = state.layout;
        var solid = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        var ghost = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        int light = LightCoordsUtil.FULL_BRIGHT;
        for (GuideMultiblocks.Cell c : layout.cells()) {
            boolean shown = state.layer < 0 || c.y() == state.layer;
            boolean ghosted = state.layer >= 0 && c.y() < state.layer;
            if (!shown && !ghosted) continue;
            boolean hover = c.x() == state.hoverX && c.y() == state.hoverY && c.z() == state.hoverZ;
            int color = ghosted ? 0x40FFFFFF : hover ? 0xFFA8D8FF : 0xFFFFFFFF;
            pose.pushPose();
            pose.translate(c.x(), c.y(), c.z());
            var state0 = c.state();
            collector.submitCustomGeometry(pose, ghosted ? ghost : solid, (p, b) -> BlockQuads.emit(p, b, state0, color, light));
            pose.popPose();
        }
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "factoryascent multiblock";
    }
}
