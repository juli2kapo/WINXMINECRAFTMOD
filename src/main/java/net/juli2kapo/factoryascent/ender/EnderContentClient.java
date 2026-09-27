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
    }

    static final class AnchorState extends BlockEntityRenderState {
        final ItemStackRenderState eye = new ItemStackRenderState();
        boolean active;
        float time;
    }

    /**
     * Awake, the eye hovers above the water, bobbing and turning, at full brightness; asleep it
     * sinks and rests still near the bottom of the tank.
     */
    static final class AnchorRenderer implements BlockEntityRenderer<EnderAnchorBlockEntity, AnchorState> {
        private static final ItemStack EYE = new ItemStack(Items.ENDER_EYE);
        private final ItemModelResolver items;

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
            state.time = anchor.getLevel() == null ? 0 : (anchor.getLevel().getGameTime() % 24000L) + partialTicks;
            items.updateForTopItem(state.eye, EYE, ItemDisplayContext.FIXED, anchor.getLevel(), null,
                    (int) anchor.getBlockPos().asLong());
        }

        @Override
        public void submit(AnchorState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            if (state.eye.isEmpty()) return;
            pose.pushPose();
            if (state.active) {
                float bob = (float) Math.sin(state.time / 12.0) * 0.06f;
                pose.translate(0.5f, 0.72f + bob, 0.5f);
                pose.mulPose(Axis.YP.rotationDegrees(state.time * 2.5f % 360f));
                pose.scale(0.45f, 0.45f, 0.45f);
                state.eye.submit(pose, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            } else {
                pose.translate(0.5f, 0.3f, 0.5f);
                pose.mulPose(Axis.XP.rotationDegrees(90f));
                pose.scale(0.4f, 0.4f, 0.4f);
                state.eye.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            }
            pose.popPose();
        }
    }
}
