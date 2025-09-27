package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.IceArrowEntity;
import net.juli2kapo.minewinx.entity.IceCrystalEntity;
import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class IcePowers {

    // MODIFICATION: Added configurable lifetime for the simulated arrow in ticks (20 ticks = 1 second)
    private static final int SIMULATED_ARROW_LIFETIME_TICKS = 40; // 2 seconds

    public static void throwSnowball(Player player) {
        Level level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.SNOWBALL_THROW, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide()) {
            net.minecraft.world.entity.projectile.Snowball snowball = new net.minecraft.world.entity.projectile.Snowball(level, player);
            snowball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(snowball);
        }
    }

    public static void encapsuleInIceCrystal(Player player){
        Level level = player.level();
        if (level.isClientSide()) {
            player.sendSystemMessage(Component.literal("[IceCrystal] Abortado: lado cliente."));
            return;
        }

        BlockPos playerPos = player.blockPosition();
        player.sendSystemMessage(Component.literal("[IceCrystal] Posición del jugador: " + playerPos));

        try {
            IceCrystalEntity iceCrystal = new IceCrystalEntity(ModEntities.ICE_CRYSTAL.get(), level);
            level.addFreshEntity(iceCrystal);
            player.sendSystemMessage(Component.literal("[IceCrystal] Entidad creada y añadida al mundo."));
            iceCrystal.setPos(player.getX(), player.getY(), player.getZ());
            player.sendSystemMessage(Component.literal("[IceCrystal] Entidad posicionada en: " + iceCrystal.position()));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("[IceCrystal] Error: " + e.getMessage()));
            e.printStackTrace();
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

        serverLevel.playSound(null, center, SoundEvents.PLAYER_HURT_FREEZE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

        Set<BlockPos> validPositions = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) > radius) {
                    continue;
                }

                boolean isObstructed = false;
                int solidBlockCount = 0;
                for (int dy_check = 0; dy_check <= 3; dy_check++) {
                    BlockPos checkPos = center.offset(dx, dy_check, dz);
                    BlockState state = level.getBlockState(checkPos);
                    if (!state.isAir() && state.getBlock() != Blocks.PACKED_ICE && !state.getCollisionShape(level, checkPos).isEmpty()) {
                        solidBlockCount++;
                    }
                }

                if (solidBlockCount >= 2) {
                    isObstructed = true;
                }

                if (!isObstructed) {
                    validPositions.add(new BlockPos(dx, 0, dz));
                    BlockPos groundPos = center.offset(dx, -1, dz);
                    if (isDestructibleAndReplaceable(level, groundPos)) {
                        level.setBlockAndUpdate(groundPos, Blocks.PACKED_ICE.defaultBlockState());
                    }
                }
            }
        }

        for (BlockPos relativePos : validPositions) {
            boolean isEdge = false;
            int[][] neighbors = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] offset : neighbors) {
                if (!validPositions.contains(relativePos.offset(offset[0], 0, offset[1]))) {
                    isEdge = true;
                    break;
                }
            }

            if (isEdge) {
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
        return level.getBlockEntity(pos) == null;
    }

    public static void fireIceVolley(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage <= 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        HitResult hitResult = getBowShotHitResult(player, level);

        if (hitResult.getType() == HitResult.Type.MISS) {
            player.sendSystemMessage(Component.literal("Simulated arrow missed."));
            return;
        }

        // MODIFICATION: Adjust target position based on hit type
        Vec3 targetPos;
        if (hitResult instanceof EntityHitResult) {
            // If we hit an entity, aim 1 block higher to target the torso instead of the feet.
            targetPos = hitResult.getLocation().add(0, 1.0, 0);
        } else {
            // Otherwise, use the exact hit location.
            targetPos = hitResult.getLocation();
        }

        BlockPos center = player.blockPosition();
        ServerLevel serverLevel = (ServerLevel) level;
        int searchRadius = 10 + stage * 5;

        for (BlockPos blockPos : BlockPos.betweenClosed(
                center.offset(-searchRadius, -searchRadius, -searchRadius),
                center.offset( searchRadius,  searchRadius,  searchRadius)
        )) {
            if (!level.getBlockState(blockPos).is(Blocks.PACKED_ICE) || blockPos.getY() == center.getY() - 1) continue;
            if (!hasHorizontalIceNeighbor(level, blockPos)) continue;

            Vec3 sourcePos = new Vec3(
                    blockPos.getX() + 0.5D,
                    blockPos.getY() + 1.0D, // Spawn slightly above the block
                    blockPos.getZ() + 0.5D
            );

            Vec3 initialVelocity = calculateProjectileVelocity(sourcePos, targetPos, 2.2F, player);

            IceArrowEntity arrow = new IceArrowEntity(level, sourcePos.x, sourcePos.y, sourcePos.z);
            arrow.setOwner(player);
            arrow.setCritArrow(true);
            arrow.pickup = Arrow.Pickup.DISALLOWED;

            arrow.setDeltaMovement(initialVelocity);

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

    /**
     * MODIFIED: Simulates the trajectory of a max-strength arrow for a fixed lifetime.
     * If it doesn't collide with anything, the target is set to its final position.
     * @param player The player shooting the simulated arrow.
     * @param level The level to perform the simulation in.
     * @return The HitResult of the collision or the final position after the lifetime expires.
     */
    public static HitResult getBowShotHitResult(Player player, Level level) {
        // --- 1. Set Initial Conditions ---
        float power = 1.0F; // Represents a fully drawn bow.
        float velocityMultiplier = 3.0F;
        Vec3 startPos = player.getEyePosition(1.0f);
        Vec3 lookAngle = player.getLookAngle();
        Vec3 motion = lookAngle.scale(power * velocityMultiplier);

        // --- 2. Step-by-Step Simulation ---
        Vec3 currentPos = startPos;
        Vec3 currentMotion = motion;

        Predicate<Entity> entityPredicate = e -> !e.isSpectator() && e.isPickable() && !e.equals(player);

        // MODIFICATION: Loop now runs for the configured lifetime
        for (int i = 0; i < SIMULATED_ARROW_LIFETIME_TICKS; i++) {
            Vec3 nextPos = currentPos.add(currentMotion);

            // --- 3. Collision Detection ---
            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                    level, player, currentPos, nextPos,
                    new AABB(currentPos, nextPos).inflate(0.5D),
                    entityPredicate
            );

            BlockHitResult blockHitResult = level.clip(new ClipContext(
                    currentPos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
            ));

            HitResult finalHitResult;
            if (entityHitResult != null) {
                if (blockHitResult.getType() == HitResult.Type.MISS ||
                        player.distanceToSqr(entityHitResult.getLocation()) < player.distanceToSqr(blockHitResult.getLocation())) {
                    finalHitResult = entityHitResult;
                } else {
                    finalHitResult = blockHitResult;
                }
            } else {
                finalHitResult = blockHitResult;
            }

            // If we had a definitive hit, return it immediately.
            if (finalHitResult.getType() != HitResult.Type.MISS) {
                if (finalHitResult instanceof EntityHitResult ehr) {
                    player.sendSystemMessage(Component.literal("Target Acquired (Entity): " + ehr.getEntity().getName().getString()));
                } else {
                    player.sendSystemMessage(Component.literal("Target Acquired (Block): " + ((BlockHitResult)finalHitResult).getBlockPos()));
                }
                return finalHitResult;
            }

            // --- 4. Update for Next Step ---
            currentPos = nextPos;

            // Apply physics (gravity and drag)
            float gravity = 0.05F; // Standard arrow gravity
            float drag = 0.99F;    // Standard arrow air resistance
            currentMotion = currentMotion.scale(drag);
            currentMotion = currentMotion.subtract(0.0D, gravity, 0.0D);
        }

        // --- 5. Lifetime Expired ---
        // If the loop finishes without a collision, the arrow's lifetime is over.
        // We return its final position as a valid target.
        player.sendSystemMessage(Component.literal("Target Acquired (Lifetime Expired): " + BlockPos.containing(currentPos)));
        return new BlockHitResult(
                currentPos,
                Direction.UP,
                BlockPos.containing(currentPos),
                false
        );
    }

    public static Vec3 calculateProjectileVelocity(Vec3 origin, Vec3 target, float velocity, Player player) {
        double gravity = 0.05D; // Arrow gravity

        Vec3 diff = target.subtract(origin);
        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

        if (horizontalDist == 0) { // Firing straight up or down
            return new Vec3(0, diff.y > 0 ? velocity : -velocity, 0);
        }

        double v2 = velocity * velocity;
        double v4 = v2 * v2;
        double term = v4 - gravity * (gravity * horizontalDist * horizontalDist + 2 * diff.y * v2);

        if (term < 0) {
            // Target is out of range for this velocity
            player.sendSystemMessage(Component.literal("Target out of range, aiming directly."));
            return diff.normalize().scale(velocity);
        }

        double sqrtTerm = Math.sqrt(term);
        // We choose the lower-angle trajectory for a more direct shot.
        double pitch = Math.atan2(v2 - sqrtTerm, gravity * horizontalDist);

        double vy = velocity * Math.sin(pitch);
        double horizontalVelocity = velocity * Math.cos(pitch);

        double horizontalAngle = Math.atan2(diff.z, diff.x);
        double vx = horizontalVelocity * Math.cos(horizontalAngle);
        double vz = horizontalVelocity * Math.sin(horizontalAngle);

        return new Vec3(vx, vy, vz);
    }
}