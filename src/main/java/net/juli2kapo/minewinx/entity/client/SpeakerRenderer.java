package net.juli2kapo.minewinx.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.SpeakerEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;

public class SpeakerRenderer extends EntityRenderer<SpeakerEntity> {
    private static final ResourceLocation TEXTURE_LOCATION = new ResourceLocation(MineWinx.MOD_ID, "textures/entity/speakermodel.png");
    private final ModelPart speakerModel;

    public SpeakerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.speakerModel = createSpeakerModel();
    }

    private ModelPart createSpeakerModel() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // Cuerpo principal del altavoz
        partdefinition.addOrReplaceChild("main_body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6.0F, -12.0F, -6.0F, 12.0F, 12.0F, 12.0F),
                PartPose.offset(0.0F, 12.0F, 0.0F));

        // Altavoz frontal
        partdefinition.addOrReplaceChild("front_speaker",
                CubeListBuilder.create()
                        .texOffs(0, 24).addBox(-4.0F, -10.0F, -7.0F, 8.0F, 8.0F, 1.0F),
                PartPose.offset(0.0F, 12.0F, 0.0F));

        // Tweeter pequeño
        partdefinition.addOrReplaceChild("tweeter",
                CubeListBuilder.create()
                        .texOffs(18, 24).addBox(-2.0F, -8.0F, -7.1F, 4.0F, 4.0F, 1.0F),
                PartPose.offset(0.0F, 8.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64).bakeRoot();
    }

    @Override
    public ResourceLocation getTextureLocation(SpeakerEntity entity) {
        return TEXTURE_LOCATION;
    }

    @Override
    public void render(SpeakerEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // Escalar si es necesario
        poseStack.scale(1.0f, 1.0f, 1.0f);

        // Obtener el VertexConsumer para renderizar
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(getTextureLocation(entity)));

        // Renderizar cada parte del modelo
        speakerModel.getChild("main_body").render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        speakerModel.getChild("front_speaker").render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        speakerModel.getChild("tweeter").render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public boolean shouldRender(SpeakerEntity entity, Frustum camera, double camX, double camY, double camZ) {
        return super.shouldRender(entity, camera, camX, camY, camZ);
    }
}