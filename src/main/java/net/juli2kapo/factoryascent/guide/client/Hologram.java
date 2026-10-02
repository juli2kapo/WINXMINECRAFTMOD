package net.juli2kapo.factoryascent.guide.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.juli2kapo.factoryascent.guide.GuideMultiblocks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

/**
 * The in-world hologram projector (client side only, nothing is placed): ghost blocks show where
 * each block of a multiblock goes, one layer at a time. The current layer pulses, the next one is
 * a faint preview, blocks in the way are tinted red. When a layer is complete it moves on by
 * itself; when the whole structure stands it disappears with a fanfare.
 */
public final class Hologram {
    private static GuideMultiblocks.@Nullable Layout layout;
    private static BlockPos base = BlockPos.ZERO;
    private static Rotation rotation = Rotation.NONE;
    private static int layer;
    private static int missing, wrong;
    private static @Nullable String dimension;

    private Hologram() {}

    public static boolean active() {
        return layout != null;
    }

    public static GuideMultiblocks.@Nullable Layout layout() {
        return layout;
    }

    /** Projects {@code l} with its front-bottom-centre at {@code at}, its front facing {@code front}. */
    public static void start(GuideMultiblocks.Layout l, BlockPos at, Direction front) {
        Minecraft mc = Minecraft.getInstance();
        layout = l;
        base = at;
        rotation = Rotation.NONE;
        for (Rotation r : Rotation.values()) if (r.rotate(Direction.NORTH) == front) rotation = r;
        dimension = mc.level == null ? null : mc.level.dimension().identifier().toString();
        layer = 0;
        refresh(false);
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.translatable("guide.factoryascent.holo.started",
                    Component.translatable(l.nameKey)).withStyle(ChatFormatting.AQUA));
        }
    }

    public static void stop() {
        layout = null;
    }

    /** Moves the projection so its front-bottom-centre is at {@code at}. */
    public static void moveTo(BlockPos at) {
        base = at;
        layer = 0;
        refresh(false);
    }

    public static void rotate() {
        rotation = rotation.getRotated(Rotation.CLOCKWISE_90);
        layer = 0;
        refresh(false);
    }

    public static void changeLayer(int delta) {
        if (layout == null) return;
        layer = Math.max(0, Math.min(layout.height - 1, layer + delta));
        refresh(false);
    }

    public static int layer() {
        return layer;
    }

    /** World position of grid cell (x, y, z). */
    public static BlockPos world(int x, int y, int z) {
        GuideMultiblocks.Layout l = layout;
        int cx = l == null ? 0 : l.controller.getX();
        return base.offset(new BlockPos(x - cx, y, z).rotate(rotation));
    }

    private static boolean correct(ClientLevel level, GuideMultiblocks.Cell c) {
        return level.getBlockState(world(c.x(), c.y(), c.z())).getBlock() == c.state().getBlock();
    }

    /** Counts what's left on the current layer; moves up when it is done (with {@code advance}). */
    private static void refresh(boolean advance) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        GuideMultiblocks.Layout l = layout;
        if (level == null || l == null) return;
        while (true) {
            missing = 0;
            wrong = 0;
            for (GuideMultiblocks.Cell c : l.layer(layer)) {
                if (correct(level, c)) continue;
                missing++;
                BlockState s = level.getBlockState(world(c.x(), c.y(), c.z()));
                if (!s.isAir() && !s.canBeReplaced()) wrong++;
            }
            for (int z = 0; z < l.depth; z++) {
                for (int x = 0; x < l.width; x++) {
                    if (l.mustBeAir(x, layer, z) && !level.getBlockState(world(x, layer, z)).getCollisionShape(level, world(x, layer, z)).isEmpty()) {
                        missing++;
                        wrong++;
                    }
                }
            }
            if (missing > 0 || !advance) return;
            if (layer + 1 >= l.height) {
                finish(mc, l);
                return;
            }
            layer++;
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 1.2f + 0.1f * layer);
            }
        }
    }

    private static void finish(Minecraft mc, GuideMultiblocks.Layout l) {
        layout = null;
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.8f, 1.0f);
            mc.player.sendOverlayMessage(Component.translatable("guide.factoryascent.holo.done",
                    Component.translatable(l.nameKey)).withStyle(ChatFormatting.GREEN));
        }
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (layout == null || mc.level == null) return;
        if (!mc.level.dimension().identifier().toString().equals(dimension)) {
            layout = null;
            return;
        }
        if (mc.level.getGameTime() % 4 == 0) refresh(true);
    }

    // ---------------------------------------------------------------- drawing

    static void submit(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        GuideMultiblocks.Layout l = layout;
        if (level == null || l == null) return;
        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack pose = event.getPoseStack();
        var collector = event.getSubmitNodeCollector();
        var type = RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        float time = (level.getGameTime() % 40 + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 40f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * Math.PI * 2);
        int light = LightCoordsUtil.FULL_BRIGHT;
        int top = Math.min(l.height - 1, layer + 1);
        for (int y = 0; y <= top; y++) {
            for (GuideMultiblocks.Cell c : l.layer(y)) {
                BlockPos p = world(c.x(), c.y(), c.z());
                BlockState actual = level.getBlockState(p);
                if (actual.getBlock() == c.state().getBlock()) continue;
                boolean blocked = !actual.isAir() && !actual.canBeReplaced();
                if (blocked && y > layer) continue;
                BlockState shown = blocked ? actual : c.state().rotate(rotation);
                int alpha = blocked ? 150 : y == layer ? (int) (90 + 70 * pulse) : y < layer ? 110 : 40;
                int rgb = blocked ? 0xFF3030 : y == layer ? 0xB8E8FF : 0xFFFFFF;
                float grow = blocked ? 0.02f : y == layer ? -0.04f : -0.1f;
                draw(pose, collector, type, p, cam, shown, (alpha << 24) | rgb, light, grow);
            }
            if (y > layer) continue;
            for (int z = 0; z < l.depth; z++) {
                for (int x = 0; x < l.width; x++) {
                    if (!l.mustBeAir(x, y, z)) continue;
                    BlockPos p = world(x, y, z);
                    BlockState actual = level.getBlockState(p);
                    if (actual.getCollisionShape(level, p).isEmpty()) continue;
                    draw(pose, collector, type, p, cam, actual, 0x96FF3030, light, 0.02f);
                }
            }
        }
    }

    private static void draw(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
                             net.minecraft.client.renderer.rendertype.RenderType type, BlockPos p, Vec3 cam,
                             BlockState state, int argb, int light, float grow) {
        pose.pushPose();
        pose.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.scale(1 + grow, 1 + grow, 1 + grow);
        pose.translate(-0.5f, -0.5f, -0.5f);
        collector.submitCustomGeometry(pose, type, (pp, b) -> BlockQuads.emit(pp, b, state, argb, light));
        pose.popPose();
    }

    /** One line at the top of the screen while projecting. */
    static void hud(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        GuideMultiblocks.Layout l = layout;
        if (l == null) return;
        Component line = Component.translatable("guide.factoryascent.holo.hud", Component.translatable(l.nameKey),
                layer + 1, l.height, missing);
        Component keys = Component.translatable("guide.factoryascent.holo.keys",
                GuideClient.NEXT_LAYER.getTranslatedKeyMessage(), GuideClient.PREV_LAYER.getTranslatedKeyMessage(),
                GuideClient.CLEAR.getTranslatedKeyMessage());
        int w = g.guiWidth();
        int lw = mc.font.width(line), kw = mc.font.width(keys);
        int bw = Math.max(lw, kw) + 10;
        g.fill(w / 2 - bw / 2, 4, w / 2 + bw / 2, 30, 0x90000000);
        g.text(mc.font, line, w / 2 - lw / 2, 7, wrong > 0 ? 0xFFFF8080 : 0xFFB8E8FF, true);
        g.text(mc.font, keys, w / 2 - kw / 2, 18, 0xFFA0A0A0, true);
    }
}
