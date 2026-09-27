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
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(EnderContent.ENDER_ANCHOR_BE.get(), AnchorRenderer::new));
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
    static final class AnchorRenderer implements BlockEntityRenderer<EnderAnchorBlockEntity, AnchorState> {
        private final ItemModelResolver items;
        /**
         * Made on first render: renderers are built during resource loading, before item
         * components are bound, and an ItemStack can't exist before that.
         */
        private @Nullable ItemStack pearl;

        AnchorRenderer(BlockEntityRendererProvider.Context context) {
            this.items = context.itemModelResolver();
        }

        @Override
        public AnchorState createRenderState() {
            return new AnchorState();
        }

        @Override
        public void extractRenderState(EnderAnchorBlockEntity anchor, AnchorState state, float partialTicks,
                                       Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderer.super.extractRenderState(anchor, state, partialTicks, camera, breakProgress);
            state.active = anchor.getBlockState().getValue(EnderAnchorBlock.ACTIVE);
            state.hasPearl = anchor.hasPearl();
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
                y = 1.72f - swell * 0.95f + wobble;
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
