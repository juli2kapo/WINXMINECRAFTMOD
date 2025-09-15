package net.juli2kapo.minewinx.item.custom;

import com.mojang.authlib.GameProfile;
import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.PlayerIllusionEntity;
import net.juli2kapo.minewinx.powers.DarkPowers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlayerIllusion extends Item {

    private static final Logger LOGGER = LogManager.getLogger();

    public PlayerIllusion(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel && stack.hasCustomHoverName()) {
            String illusionName = stack.getHoverName().getString();
            MinecraftServer server = serverLevel.getServer();

            // Usar getAsync para asegurar que las propiedades de la skin se carguen
            server.getProfileCache().getAsync(illusionName, gameProfileOpt -> {
                server.execute(() -> { // Ejecutar en el hilo principal del servidor
                    if (gameProfileOpt.isEmpty()) {
                        LOGGER.warn("Could not find game profile for {}", illusionName);
                        player.sendSystemMessage(Component.literal("Player '" + illusionName + "' not found."));
                        return;
                    }

                    GameProfile gameProfile = gameProfileOpt.get();
                    LOGGER.info("Found game profile: {}", gameProfile);

                    // Completar el perfil con las propiedades de textura
                    try {
                        server.getSessionService().fillProfileProperties(gameProfile, true);
                        LOGGER.info("Completed profile with properties: {}", gameProfile.getProperties());
                    } catch (Exception e) {
                        LOGGER.error("Failed to fill profile properties", e);
                    }

                    // Sonido sutil de aparición
                    serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.5F);

                    PlayerIllusionEntity illusion = new PlayerIllusionEntity(ModEntities.PLAYER_ILLUSION.get(), serverLevel);

                    illusion.setGameProfile(gameProfile);
                    illusion.setCustomName(Component.literal(illusionName));
                    illusion.setCustomNameVisible(true);
                    illusion.setBaby(false);

                    illusion.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                    illusion.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(illusion.blockPosition()), MobSpawnType.EVENT, null, null);

                    illusion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0D);
                    illusion.setHealth(1.0F);
                    illusion.setPersistenceRequired();
                    illusion.setSilent(true);

                    serverLevel.addFreshEntity(illusion);
                    DarkPowers.markIllusionCreator(illusion, player);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                });
            });

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.fail(stack);
    }
}