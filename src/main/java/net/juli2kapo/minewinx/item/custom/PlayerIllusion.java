package net.juli2kapo.minewinx.item.custom;

import com.mojang.authlib.GameProfile;
import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.PlayerIllusionEntity;
import net.juli2kapo.minewinx.powers.DarkPowers;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.particles.ParticleTypes;
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerIllusion extends Item {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<String, GameProfile> PROFILE_CACHE = new ConcurrentHashMap<>();

    public PlayerIllusion(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel && stack.hasCustomHoverName()) {
            String illusionName = stack.getHoverName().getString();
            MinecraftServer server = serverLevel.getServer();

            // Partículas y sonido inmediatos
            serverLevel.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.5, 0.5, 0.05);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.5F);

            GameProfile cachedProfile = PROFILE_CACHE.get(illusionName);
            if (cachedProfile != null) {
                // Usar perfil de la caché
                spawnIllusion(serverLevel, player, cachedProfile, stack);
            } else {
                // Buscar perfil si no está en la caché
                // Dentro de getAsync:
                server.getProfileCache().getAsync(illusionName, gameProfileOpt -> {
                    if (gameProfileOpt.isEmpty()) {
                        LOGGER.warn("Could not find game profile for {}", illusionName);
                        player.sendSystemMessage(Component.literal("Player '" + illusionName + "' not found."));
                        return;
                    }
                    GameProfile gameProfile = gameProfileOpt.get();
                    // Ejecutar la obtención de propiedades en un hilo aparte
                    CompletableFuture.runAsync(() -> {
                        try {
                            server.getSessionService().fillProfileProperties(gameProfile, true);
                            PROFILE_CACHE.put(illusionName, gameProfile);
                        } catch (Exception e) {
                            LOGGER.error("Failed to fill profile properties", e);
                        }
                    }).thenRun(() -> {
                        // Volver al hilo del servidor para el spawn
                        server.execute(() -> spawnIllusion(serverLevel, player, gameProfile, stack));
                    });
                });
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.fail(stack);
    }

    private void spawnIllusion(ServerLevel serverLevel, Player player, GameProfile gameProfile, ItemStack stack) {
        int stage = PlayerDataProvider.getStage(player);
        PlayerIllusionEntity illusion = new PlayerIllusionEntity(ModEntities.PLAYER_ILLUSION.get(), serverLevel);

        illusion.setGameProfile(gameProfile);
        illusion.setCustomName(Component.literal(gameProfile.getName()));
        illusion.setCustomNameVisible(true);
        illusion.setBaby(false);
        illusion.configureByStage(stage);


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
    }
}