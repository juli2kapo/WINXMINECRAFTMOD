package net.juli2kapo.minewinx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Dibuja las alas del elemento en la espalda de los jugadores transformados. */
public class WingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(MineWinx.MOD_ID, "textures/entity/wings_white.png");

    public WingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        String element = ClientWingState.getWings(player.getId());
        if (element == null) return;
        VoxelMesh mesh = VoxelMesh.wings(element);
        if (mesh == null) return;

        // Aleteo suave en el piso, más rápido y amplio en el aire.
        float sweep = player.onGround()
                ? 14.0F + 8.0F * Mth.sin(ageInTicks * 0.12F)
                : 18.0F + 22.0F * Mth.sin(ageInTicks * 0.55F);

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 0.3F, 0.2F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-8.0F));
        mesh.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE)), packedLight, sweep, false);
        mesh.render(poseStack, buffer.getBuffer(RenderType.entityTranslucent(WHITE)), packedLight, sweep, true);
        poseStack.popPose();
    }
}
