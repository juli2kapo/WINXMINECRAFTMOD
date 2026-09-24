package net.juli2kapo.minewinx.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.juli2kapo.minewinx.MineWinx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Dibuja el modelo 3D de la semilla en la mano, en el piso y en marcos.
 * En el inventario se sigue viendo el ícono plano (modelo separate_transforms).
 */
public class SeedItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(MineWinx.MOD_ID, "textures/entity/wings_white.png");

    /** Los modelos se construyeron con el emblema mirando a 30° (cámara del ícono). */
    private static final float EMBLEM_YAW = 30.0F;

    private static SeedItemRenderer instance;

    public static SeedItemRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new SeedItemRenderer(mc);
        }
        return instance;
    }

    private SeedItemRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return;
        VoxelMesh mesh = VoxelMesh.seed(id.getPath());
        if (mesh == null) return;

        poseStack.pushPose();
        // Espacio del ítem: cubo [0,1]; la semilla va centrada y con el emblema hacia +Z.
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(EMBLEM_YAW + 180.0F));
        mesh.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE)), packedLight, 0.0F, false);
        mesh.render(poseStack, buffer.getBuffer(RenderType.entityTranslucent(WHITE)), packedLight, 0.0F, true);
        poseStack.popPose();
    }
}
