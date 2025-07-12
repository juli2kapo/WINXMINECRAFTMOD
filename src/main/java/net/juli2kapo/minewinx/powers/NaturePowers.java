package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.SporeBombEntity;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

public class NaturePowers {

    /**
     * Lanza un proyectil de bomba de esporas.
     * @param player El jugador que lanza la bomba.
     */
    public static void sporeBomb(Player player) {
        Level world = player.level();
        if (!world.isClientSide()) {
            player.sendSystemMessage(Component.literal("Lanzando bomba de esporas..."));
            SporeBombEntity sporeBomb = new SporeBombEntity(ModEntities.SPOREBOMB.get(), player, world);
            // Dispara el proyectil desde la posición y dirección del jugador.
            sporeBomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            world.addFreshEntity(sporeBomb);
        }
    }

    /**
     * Aplica un efecto pasivo de crecimiento a los cultivos cercanos.
     * @param player El jugador con el poder de la naturaleza.
     */
    public static void applyPassiveNatureGrowth(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0 || !(player.level() instanceof ServerLevel level)) return;

        // Ejecutar la lógica con menos frecuencia para no ser demasiado OP y mejorar el rendimiento.
        if (level.random.nextInt(20) != 0) {
            return;
        }

        int radius = 2 + stage; // El radio aumenta con el nivel.
        BlockPos center = player.blockPosition();

        BlockPos.betweenClosedStream(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))
                .forEach(pos -> {
                    BlockState blockState = level.getBlockState(pos);
                    Block block = blockState.getBlock();

                    // Probabilidad de crecimiento para que no too crezca a la vez.
                    if (level.random.nextInt(10) < stage) {
                        if (block instanceof BonemealableBlock growable && !(block instanceof GrassBlock) && growable.isValidBonemealTarget(level, pos, blockState, level.isClientSide)) {
                            if (growable.isBonemealSuccess(level, level.random, pos, blockState)) {
                                growable.performBonemeal(level, level.random, pos, blockState);
                                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.5, 0.5, 0.5, 0.0);
                            }
                        } else if (block instanceof SugarCaneBlock || block instanceof CactusBlock) {
                            // Manejo especial para caña de azúcar y cactus.
                            if (level.isEmptyBlock(pos.above())) {
                                int height = 1;
                                while (level.getBlockState(pos.below(height)).is(block)) {
                                    height++;
                                }
                                if (height < 3) {
                                    BlockPos abovePos = pos.above();
                                    level.setBlock(abovePos, block.defaultBlockState(), 2);
                                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, abovePos.getX() + 0.5, abovePos.getY() + 0.5, abovePos.getZ() + 0.5, 5, 0.5, 0.5, 0.5, 0.0);
                                }
                            }
                        }
                    }
                });
    }
}