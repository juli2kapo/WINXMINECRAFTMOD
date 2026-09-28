package net.juli2kapo.factoryascent.ender;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jspecify.annotations.Nullable;

/** Client side of the ender tech: the eye of ender floating in the Ender Anchor. */
public final class EnderContentClient {
    private EnderContentClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            // Anchor: two blocks of water, the pearl rides up to near the top of the upper half.
            e.registerBlockEntityRenderer(EnderContent.ENDER_ANCHOR_BE.get(), ctx -> new AnchorRenderer<EnderAnchorBlockEntity>(
                    ctx, EnderAnchorBlockEntity::hasPearl, EnderAnchorBlockEntity::isRunning, 1.72f, 0.95f));
            // Beacon: one block of water under a trapdoor lid.
            e.registerBlockEntityRenderer(EnderContent.ENDER_BEACON_BE.get(), ctx -> new AnchorRenderer<EnderBeaconBlockEntity>(
                    ctx, EnderBeaconBlockEntity::hasPearl, EnderBeaconBlockEntity::hasPearl, 0.68f, 0.22f));
        });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) -> {
            e.register(EnderContent.ENDER_ANCHOR_MENU.get(), net.juli2kapo.factoryascent.ender.client.EnderAnchorScreen::new);
            e.register(EnderContent.ENDER_BEACON_MENU.get(), net.juli2kapo.factoryascent.ender.client.EnderBeaconScreen::new);
        });
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent e) ->
                e.registerSpriteSet(EnderContent.STASIS_BUBBLE.get(), StasisBubbleParticle.Provider::new));
    }

    static final class AnchorState extends BlockEntityRenderState {
        final ItemStackRenderState pearl = new ItemStackRenderState();
        boolean active;
        boolean hasPearl;
        float time;
        float phase;
    }

    /**
     * A stasis chamber: the pearl rides the bubble column, drifting up to just under the surface
     * in the top half and sinking back a little, with a small wobble, always facing the camera
     * like a dropped item. An empty chamber shows nothing.
     */
    static final class AnchorRenderer<T extends net.minecraft.world.level.block.entity.BlockEntity>
            implements BlockEntityRenderer<T, AnchorState> {
        private final ItemModelResolver items;
        private final java.util.function.Predicate<T> hasPearl;
        /** Whether the pearl rides the bubble column (a switched-off anchor lets it sink). */
        private final java.util.function.Predicate<T> active;
        /** Highest point of the pearl's ride and how far it dips below it, in blocks. */
        private final float top;
        private final float dip;
        /**
         * Made on first render: renderers are built during resource loading, before item
         * components are bound, and an ItemStack can't exist before that.
         */
        private @Nullable ItemStack pearl;

        AnchorRenderer(BlockEntityRendererProvider.Context context, java.util.function.Predicate<T> hasPearl,
                       java.util.function.Predicate<T> active, float top, float dip) {
            this.items = context.itemModelResolver();
            this.hasPearl = hasPearl;
            this.active = active;
            this.top = top;
            this.dip = dip;
        }

        @Override
        public AnchorState createRenderState() {
            return new AnchorState();
        }

        @Override
        public void extractRenderState(T anchor, AnchorState state, float partialTicks,
                                       Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderer.super.extractRenderState(anchor, state, partialTicks, camera, breakProgress);
            state.hasPearl = hasPearl.test(anchor);
            state.active = state.hasPearl && active.test(anchor);
            state.time = anchor.getLevel() == null ? 0 : (anchor.getLevel().getGameTime() % 72000L) + partialTicks;
            state.phase = (anchor.getBlockPos().hashCode() & 0xFF) / 40f;
            if (pearl == null) pearl = new ItemStack(Items.ENDER_PEARL);
            items.updateForTopItem(state.pearl, pearl, ItemDisplayContext.GROUND, anchor.getLevel(), null,
                    (int) anchor.getBlockPos().asLong());
        }

        @Override
        public boolean shouldRenderOffScreen() {
            return true; // the chamber is two blocks tall; keep the pearl when only the top is on screen
        }

        @Override
        public void submit(AnchorState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            if (!state.hasPearl || state.pearl.isEmpty()) return;
            pose.pushPose();
            float y;
            if (state.active) {
                // Mostly near the top of the column, now and then dipping into the bubbles and rising again.
                float t = state.time / 20f + state.phase;
                float swell = (float) (0.5 - 0.5 * Math.cos(t * 0.9));      // 0..1, slow
                float wobble = (float) Math.sin(t * 5.3) * 0.025f;          // bubbles jostling it
                y = top - swell * dip + wobble;
                float sway = (float) Math.sin(t * 2.1) * 0.05f;
                pose.translate(0.5f + sway, y, 0.5f + (float) Math.cos(t * 1.7) * 0.05f);
            } else {
                y = 0.3f;
                pose.translate(0.5f, y, 0.5f);
            }
            pose.mulPose(camera.orientation);
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            pose.scale(0.9f, 0.9f, 0.9f);
            state.pearl.submit(pose, collector, state.active ? 0xF000F0 : state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
