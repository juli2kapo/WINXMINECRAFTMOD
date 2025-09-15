package net.juli2kapo.minewinx.entity.client.model;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.juli2kapo.minewinx.MineWinx;
import net.juli2kapo.minewinx.entity.SpeakerEntity;
import net.juli2kapo.minewinx.entity.client.animations.SpeakerAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;


public class SpeakerModel<T extends SpeakerEntity> extends HierarchicalModel<T> {
    // This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(MineWinx.MOD_ID, "speakermodel"), "main");  
    private final ModelPart root;
    private final ModelPart Abajo;
	private final ModelPart Abajo2;
	private final ModelPart Medio;
	private final ModelPart Base;
	private final ModelPart Blanco;
	private final ModelPart WireFrame;
	private final ModelPart WireFrameAbajo;
	private final ModelPart WireFrameArriba;
	private final ModelPart PuntoNegroArriba;
	private final ModelPart PuntoNegroAbajo;

	public SpeakerModel(ModelPart root) {
		this.root = root;
        this.Abajo = root.getChild("Abajo");
		this.Abajo2 = root.getChild("Abajo2");
		this.Medio = root.getChild("Medio");
		this.Base = root.getChild("Base");
		this.Blanco = root.getChild("Blanco");
		this.WireFrame = root.getChild("WireFrame");
		this.WireFrameAbajo = root.getChild("WireFrameAbajo");
		this.WireFrameArriba = root.getChild("WireFrameArriba");
		this.PuntoNegroArriba = root.getChild("PuntoNegroArriba");
		this.PuntoNegroAbajo = root.getChild("PuntoNegroAbajo");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition Abajo = partdefinition.addOrReplaceChild("Abajo", CubeListBuilder.create().texOffs(30, 42).addBox(1.0F, -14.0F, -7.0F, 1.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(44, 0).addBox(1.0F, -3.0F, -7.0F, 1.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(60, 50).addBox(1.0F, -11.0F, 4.0F, 1.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(60, 61).addBox(1.0F, -11.0F, -7.0F, 1.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition Abajo2 = partdefinition.addOrReplaceChild("Abajo2", CubeListBuilder.create().texOffs(44, 17).addBox(1.0F, -14.0F, -7.0F, 1.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(30, 59).addBox(1.0F, -3.0F, -7.0F, 1.0F, 3.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(68, 50).addBox(1.0F, -11.0F, 4.0F, 1.0F, 8.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(68, 61).addBox(1.0F, -11.0F, -7.0F, 1.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 10.0F, 0.0F));

		PartDefinition Medio = partdefinition.addOrReplaceChild("Medio", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition Base = partdefinition.addOrReplaceChild("Base", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -28.0F, -7.0F, 7.0F, 28.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition Blanco = partdefinition.addOrReplaceChild("Blanco", CubeListBuilder.create().texOffs(94, 75).addBox(0.0F, -28.0F, -7.0F, 1.0F, 28.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition WireFrame = partdefinition.addOrReplaceChild("WireFrame", CubeListBuilder.create().texOffs(0, 42).addBox(2.0F, -16.0F, -7.0F, 1.0F, 4.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition WireFrameAbajo = partdefinition.addOrReplaceChild("WireFrameAbajo", CubeListBuilder.create().texOffs(66, 72).addBox(2.0F, -12.0F, -7.0F, 1.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(60, 72).addBox(2.0F, -12.0F, 5.0F, 1.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 60).addBox(2.0F, -2.0F, -7.0F, 1.0F, 2.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition WireFrameArriba = partdefinition.addOrReplaceChild("WireFrameArriba", CubeListBuilder.create().texOffs(60, 34).addBox(2.0F, -28.0F, -7.0F, 1.0F, 2.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(72, 72).addBox(2.0F, -26.0F, 5.0F, 1.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(74, 0).addBox(2.0F, -26.0F, -7.0F, 1.0F, 10.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition PuntoNegroArriba = partdefinition.addOrReplaceChild("PuntoNegroArriba", CubeListBuilder.create().texOffs(44, 38).addBox(1.0F, -22.0F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition PuntoNegroAbajo = partdefinition.addOrReplaceChild("PuntoNegroAbajo", CubeListBuilder.create().texOffs(44, 34).addBox(1.0F, -8.0F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}


    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        
        // Apply animation based on the entity's animation progress
        float animationProgress = entity.getAnimationProgress();
        this.animate(entity.getAnimationState(), SpeakerAnimations.MakeSound, ageInTicks, 1.0f);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}