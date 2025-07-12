//package net.juli2kapo.minewinx.client.renderer.feature;

//import com.mojang.blaze3d.vertex.PoseStack;
//import net.minecraft.client.model.HumanoidModel;
//import net.minecraft.client.model.geom.ModelLayers;
//import net.minecraft.client.player.AbstractClientPlayer;
//import net.minecraft.client.renderer.MultiBufferSource;
//import net.minecraft.client.renderer.block.model.ItemTransforms;
//import net.minecraft.client.renderer.entity.LivingEntityRenderer;
//import net.minecraft.client.renderer.entity.layers.RenderLayer;
//import net.minecraft.client.renderer.entity.player.PlayerRenderer;
//import net.minecraft.client.renderer.texture.OverlayTexture;
//import net.minecraft.core.BlockPos;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.item.BlockItem;
//import net.minecraft.world.level.block.Blocks;
//import net.minecraftforge.client.event.EntityRenderersEvent;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.renderer.block.BlockRenderDispatcher;
//
//public class WaterHatRenderLayer extends RenderLayer<AbstractClientPlayer, HumanoidModel<AbstractClientPlayer>> {
//
//    public WaterHatRenderLayer(LivingEntityRenderer<AbstractClientPlayer, HumanoidModel<AbstractClientPlayer>> renderer) {
//        super(renderer);
//    }
//
//    @Override
//    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
//                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
//
//        if (player.isInvisible()) return;
//
//        poseStack.pushPose();
//
//        // Translate to head position
//        this.getParentModel().head.translateAndRotate(poseStack);
//
//        // Move the block slightly upward so it's on the head
//        poseStack.translate(0.0F, -0.25F, 0.0F);
//        poseStack.scale(0.5F, 0.5F, 0.5F); // Optional: Resize
//
//        // Render the block (water)
//        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
//        blockRenderer.renderSingleBlock(Blocks.WATER.defaultBlockState(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
//
//        poseStack.popPose();
//    }
//
//    public static void registerLayer(EntityRenderersEvent.AddLayers event) {
//        for (String skin : event.getSkins()) {
//            PlayerRenderer renderer = event.getRenderer(skin);
//            renderer.addLayer(new WaterHatRenderLayer(renderer));
//        }
//    }
//}
