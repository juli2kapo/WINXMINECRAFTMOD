package net.juli2kapo.factoryascent.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.juli2kapo.factoryascent.space.Jetpack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * A jetpack (or the Jet Suit's built-in one) on the wearer's back: a small 3D block model
 * ({@code block/jetpack_*_worn}) that follows the torso, twin tanks with the nozzles at the bottom.
 */
final class JetpackLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    JetpackLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || !(state.chestEquipment.getItem() instanceof Jetpack.Gear gear)) return;
        BlockStateModelPart model = Minecraft.getInstance().getModelManager().getStandaloneModel(
                gear.jetTier() == Jetpack.Tier.ELECTRIC ? SpaceClient.JETPACK_ELECTRIC : SpaceClient.JETPACK_ADVANCED);
        if (model == null) return;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        // Model space is upside down (y points down) with the back towards +z: turn the block model
        // upright and put its z = 8 plane on the back of the torso, its y = 0 at the waist.
        pose.translate(0f, 0f, 2f / 16f);
        pose.scale(-1f, -1f, 1f);
        pose.translate(-0.5f, -12f / 16f, -0.5f);
        collector.submitBlockModel(pose, Sheets.cutoutBlockItemSheet(), List.of(model), new int[0],
                light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
