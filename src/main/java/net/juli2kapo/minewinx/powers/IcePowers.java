package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import java.util.Collections;
import net.minecraft.world.level.ClipContext; // Add this import
import net.minecraft.world.phys.HitResult;   // Add this import
import java.util.HashSet;
import java.util.Set;

public class IcePowers {

    public static void throwSnowball(Player player) {
        Level level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.SNOWBALL_THROW, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide()) {
            net.minecraft.world.entity.projectile.Snowball snowball = new net.minecraft.world.entity.projectile.Snowball(level, player);
            snowball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(snowball);
        }
    }

    public static void activateIceRing(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;
        Level level = player.level();
        if (level.isClientSide()) return;

        int radius = 5 + stage * 5;
        int baseHeight = 2;
        BlockPos center = player.blockPosition();
        ServerLevel serverLevel = (ServerLevel) level;

        // --- Animation (No changes from previous version) ---
        serverLevel.playSound(null, center, SoundEvents.PLAYER_HURT_FREEZE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        // Particle animation can be added here if desired

        // --- MODIFICATION: Two-Pass System for Structure Interaction ---

        // Pass 1: Identify valid, non-obstructed positions and place the ice floor.
        Set<BlockPos> validPositions = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Check if the point is within the circular radius
                if (Math.sqrt(dx * dx + dz * dz) > radius) {
                    continue;
                }

                // --- NEW: Obstacle Check ---
                // Checks for a solid structure (like a wall) at this coordinate.
                boolean isObstructed = false;
                int solidBlockCount = 0;
                // Check a column from the player's feet up to 3 blocks high.
                for (int dy_check = 0; dy_check <= 3; dy_check++) {
                    BlockPos checkPos = center.offset(dx, dy_check, dz);
                    BlockState state = level.getBlockState(checkPos);
                    // A solid block is one that isn't air and has a collision box. We ignore our own ice.
                    if (!state.isAir() && state.getBlock() != Blocks.PACKED_ICE && !state.getCollisionShape(level, checkPos).isEmpty()) {
                        solidBlockCount++;
                    }
                }

                // If there's a structure at least 2 blocks high, it's an obstacle.
                if (solidBlockCount >= 2) {
                    isObstructed = true;
                }
                // --- End of Obstacle Check ---

                if (!isObstructed) {
                    // This position is valid. Add its relative coordinates to our set.
                    validPositions.add(new BlockPos(dx, 0, dz));
                    // Place the ice floor.
                    BlockPos groundPos = center.offset(dx, -1, dz);
                    if (isDestructibleAndReplaceable(level, groundPos)) {
                        level.setBlockAndUpdate(groundPos, Blocks.PACKED_ICE.defaultBlockState());
                    }
                }
            }
        }

        // Pass 2: Build the ice wall along the new, irregular boundary.
        for (BlockPos relativePos : validPositions) {
            boolean isEdge = false;
            // Check the four cardinal neighbors of the current valid position.
            int[][] neighbors = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] offset : neighbors) {
                // If a neighbor is NOT in our set of valid positions, it means we are at an edge.
                if (!validPositions.contains(relativePos.offset(offset[0], 0, offset[1]))) {
                    isEdge = true;
                    break;
                }
            }

            if (isEdge) {
                // This is an edge block, so we build a random ice spike here.
                if (level.getRandom().nextFloat() > 0.35f) {
                    int spikeHeight = 1 + level.getRandom().nextInt(baseHeight + 1);
                    for (int dy = 0; dy < spikeHeight; dy++) {
                        BlockPos wallPos = center.offset(relativePos.getX(), dy, relativePos.getZ());
                        if (level.getBlockState(wallPos).isAir() || isDestructibleAndReplaceable(level, wallPos)) {
                            level.setBlockAndUpdate(wallPos, Blocks.PACKED_ICE.defaultBlockState());
                        }
                    }
                }
            }
        }

        serverLevel.playSound(null, center, SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 1.0F);

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, stage - 1, false, false, true));
    }

    private static boolean isDestructibleAndReplaceable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            return true;
        }
        if (state.getDestroySpeed(level, pos) == -1.0F) {
            return false;
        }
        if (level.getBlockEntity(pos) != null) {
            return false;
        }
        return true;
    }

    public static void fireIceVolley(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        double maxDistance = 64.0;
        // Use ClipContext for precise ray trace including block outlines
        HitResult hitResult = level.clip(new ClipContext(
                player.getEyePosition(1.0F),
                player.getEyePosition(1.0F).add(player.getLookAngle().scale(maxDistance)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        Vec3 targetPos;
        if (hitResult.getType() != HitResult.Type.MISS) {
            targetPos = hitResult.getLocation();
        } else {
            targetPos = player.getEyePosition(1.0F).add(player.getLookAngle().scale(maxDistance));
        }

        BlockPos center = player.blockPosition();
        ServerLevel serverLevel = (ServerLevel) level;
        int searchRadius = 10 + stage * 5;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-searchRadius, -searchRadius, -searchRadius),
                center.offset( searchRadius,  searchRadius,  searchRadius)
        )) {
            if (!level.getBlockState(pos).is(Blocks.PACKED_ICE) || pos.getY() == center.getY() - 1) continue;
            if (!hasHorizontalIceNeighbor(level, pos)) continue;

            // Spawn arrows from just above the ice block center
            Vec3 sourcePos = new Vec3(
                    pos.getX() + 0.5D,
                    pos.getY() + 1.0D,
                    pos.getZ() + 0.5D
            );

            Vec3 direction = targetPos.subtract(sourcePos).normalize();

            // Offset spawn slightly to avoid intersection
            Vec3 spawnPos = sourcePos.add(direction.scale(0.6D));

            Arrow arrow = new Arrow(level, spawnPos.x, spawnPos.y, spawnPos.z);
            arrow.setOwner(player);
            arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2));
            arrow.setCritArrow(true);

            // Use zero inaccuracy to ensure arrows go straight
            arrow.shoot(direction.x, direction.y, direction.z, 2.2F, 0.0F);
            level.addFreshEntity(arrow);
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    sourcePos.x, sourcePos.y, sourcePos.z,
                    10, 0.2, 0.2, 0.2, 0.05
            );
        }
    }

    private static boolean hasHorizontalIceNeighbor(Level level, BlockPos pos) {
        int[][] neighbors = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] offset : neighbors) {
            if (level.getBlockState(pos.offset(offset[0], 0, offset[1])).is(Blocks.PACKED_ICE)) {
                return true;
            }
        }
        return false;
    }

}