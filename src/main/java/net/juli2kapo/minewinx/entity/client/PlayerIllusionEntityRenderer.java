package net.juli2kapo.minewinx.entity.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.juli2kapo.minewinx.entity.PlayerIllusionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class PlayerIllusionEntityRenderer extends LivingEntityRenderer<PlayerIllusionEntity, PlayerModel<PlayerIllusionEntity>> {

    private static final Logger LOGGER = LogManager.getLogger();
    private final PlayerModel<PlayerIllusionEntity> defaultModel;
    private final PlayerModel<PlayerIllusionEntity> slimModel;

    public PlayerIllusionEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.defaultModel = this.getModel();
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerIllusionEntity entity) {
        return entity.getGameProfile().map(gameProfile -> {
            Minecraft minecraft = Minecraft.getInstance();
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = minecraft.getSkinManager().getInsecureSkinInformation(gameProfile);

            if (map.containsKey(MinecraftProfileTexture.Type.SKIN)) {
                MinecraftProfileTexture texture = map.get(MinecraftProfileTexture.Type.SKIN);
                String modelName = texture.getMetadata("model");
                this.model = "slim".equals(modelName) ? this.slimModel : this.defaultModel;
                ResourceLocation skinLocation = minecraft.getSkinManager().registerTexture(texture, MinecraftProfileTexture.Type.SKIN);
                return skinLocation;
            } else {
                LOGGER.debug("No skin found in profile for {}, using default.", gameProfile.getName());
                this.model = this.defaultModel;
                return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
            }
        }).orElseGet(() -> {
            LOGGER.debug("No game profile for illusion entity, using default skin.");
            this.model = this.defaultModel;
            return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
        });
    }
}