package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.SporeBombEntity;
import net.juli2kapo.minewinx.entity.plants.PlantEntity;
import net.juli2kapo.minewinx.entity.plants.PlantType;
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
            SporeBombEntity sporeBomb = new SporeBombEntity(ModEntities.SPOREBOMB.get(), player, world);
            // Dispara el proyectil desde la posición y dirección del jugador.
            sporeBomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            world.addFreshEntity(sporeBomb);
        }
    }

    private static final String SELECTED_PLANT_KEY = "MinewinxSelectedPlant";

    /** Planta seleccionada del jugador, garantizada dentro del roster de su stage. */
    public static PlantType getSelectedPlant(Player player) {
        int stage = Math.max(1, PlayerDataProvider.getStage(player));
        java.util.List<PlantType> roster = PlantType.rosterForStage(stage);
        PlantType selected = PlantType.byName(player.getPersistentData().getString(SELECTED_PLANT_KEY));
        return roster.contains(selected) ? selected : roster.get(0);
    }

    /**
     * Slot 2: rota qué planta está seleccionada para plantar (roster según stage).
     */
    public static void cyclePlant(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;
        if (player.level().isClientSide()) return;

        java.util.List<PlantType> roster = PlantType.rosterForStage(stage);
        PlantType current = PlantType.byName(player.getPersistentData().getString(SELECTED_PLANT_KEY));
        int index = roster.indexOf(current);
        PlantType next = roster.get((index + 1) % roster.size());
        player.getPersistentData().putString(SELECTED_PLANT_KEY, next.name());

        player.displayClientMessage(Component.translatable("power.minewinx.plant_selected",
                Component.translatable(next.translationKey())), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.juli2kapo.minewinx.sound.ModSounds.TAP.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
        // Actualizar el HUD al instante (sin esperar el sync periódico)
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            net.juli2kapo.minewinx.network.PacketHandler.sendToPlayer(
                    new net.juli2kapo.minewinx.network.HudStateS2CPacket(
                            PlayerDataProvider.getElement(player), stage, next.name()), serverPlayer);
        }
    }

    /**
     * Slot 3: planta la planta seleccionada en el bloque al que mira Flora.
     * Respeta el límite de plantas por stage marchitando la más vieja.
     */
    public static void spawnPlant(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;

        java.util.List<PlantType> roster = PlantType.rosterForStage(stage);
        PlantType selected = PlantType.byName(player.getPersistentData().getString(SELECTED_PLANT_KEY));
        if (!roster.contains(selected)) {
            selected = roster.get(0);
            player.getPersistentData().putString(SELECTED_PLANT_KEY, selected.name());
        }

        double maxRange = 20.0;
        net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
        net.minecraft.world.phys.Vec3 endPos = eyePos.add(player.getViewVector(1.0F).scale(maxRange));
        net.minecraft.world.phys.BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
                eyePos, endPos, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.translatable("message.minewinx.plant.aim_block"), true);
            return;
        }
        BlockPos plantPos = hit.getBlockPos().above();
        if (!level.getBlockState(plantPos).getCollisionShape(level, plantPos).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.minewinx.plant.no_space"), true);
            return;
        }

        // Límite de plantas: marchitar la más vieja si se pasa
        java.util.List<PlantEntity> owned = serverLevel.getEntitiesOfClass(PlantEntity.class,
                player.getBoundingBox().inflate(96.0),
                p -> player.getUUID().equals(p.getOwnerUUID()));
        int cap = PlantType.plantCapForStage(stage);
        if (owned.size() >= cap) {
            owned.stream().min(java.util.Comparator.comparingLong(PlantEntity::getPlantedAt))
                    .ifPresent(PlantEntity::wither);
        }

        PlantEntity plant = new PlantEntity(ModEntities.PLANT.get(), level);
        plant.moveTo(plantPos.getX() + 0.5, plantPos.getY(), plantPos.getZ() + 0.5,
                player.getYRot() + 180.0F, 0);
        plant.yBodyRot = plant.getYRot();
        plant.init(selected, player, stage);
        serverLevel.addFreshEntity(plant);

        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                plantPos.getX() + 0.5, plantPos.getY() + 0.5, plantPos.getZ() + 0.5,
                10, 0.3, 0.3, 0.3, 0.02);
        serverLevel.playSound(null, plantPos.getX() + 0.5, plantPos.getY(), plantPos.getZ() + 0.5,
                net.juli2kapo.minewinx.sound.ModSounds.PLANT.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
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