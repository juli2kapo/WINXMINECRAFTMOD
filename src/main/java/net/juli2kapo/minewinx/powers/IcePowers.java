package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.IceArrowEntity;
import net.juli2kapo.minewinx.entity.IceCrystalEntity;
import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext; // Add this import
import net.minecraft.world.phys.HitResult;   // Add this import
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import java.util.function.Predicate;

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
    // for now just spawn an IceCrystalEntity at where the player is looking
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

        Vec3 targetPos;
        HitResult hitResult = getBowShotHitResult(player, level);
        targetPos = hitResult != null ? hitResult.getLocation() : player.getEyePosition(1.0F).add(player.getLookAngle().scale(64.0D));

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
                    blockPos.getY() + 1.0D,
                    blockPos.getZ() + 0.5D
            );

            Vec3 dir = targetPos.subtract(sourcePos).normalize();
            Vec3 spawnPos = sourcePos.add(dir);
            Vec3 shootDirection  = calculateProjectileVelocity(
                    spawnPos,
                    targetPos,
                    2.2F // Adjusted velocity for the ice arrow
            );
            player.sendSystemMessage(Component.literal("Calculated shootDirection: " + shootDirection));
            IceArrowEntity arrow = new IceArrowEntity(level, spawnPos.x, spawnPos.y, spawnPos.z);
            arrow.setOwner(player);
            arrow.setCritArrow(true);
            arrow.pickup = Arrow.Pickup.DISALLOWED;

            //arrow.shoot(dir.x, dir.y, dir.z, 2.2F, 0.0F);
//            arrow.shoot(targetPos.x, targetPos.y, targetPos.z, 2.2F, 0.0F);
            arrow.shoot(shootDirection.x, shootDirection.y, shootDirection.z, 2.2F, 0.0F);

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

    public static HitResult getBowShotHitResult(Player player, Level level) {
        // Properties directly from BowItem.releaseUsing for a max strength shot:
        float power = 1.0F; // Corresponds to 'f' in BowItem.releaseUsing after getPowerForTime(20)
        float velocityMultiplier = 3.0F; // The multiplier applied to 'f' in shootFromRotation
        float inaccuracy = 1.0F; // The inaccuracy parameter passed to shootFromRotation

        // The arrow generally spawns slightly below the player's eye position.
        // This mimics the origin point used by AbstractArrow when created from a LivingEntity.
        Vec3 startVec = new Vec3(player.getX(), player.getEyeY() - 0.1D, player.getZ());

        // Get player's pitch and yaw (in degrees)
        float playerPitch = player.getXRot();
        float playerYaw = player.getYRot();

        // This is equivalent to what shootFromRotation() does internally for the base direction.
        // Note: Mth.sin/cos expect radians, so convert degrees to radians.
        float yawRad = playerYaw * Mth.DEG_TO_RAD;
        float pitchRad = playerPitch * Mth.DEG_TO_RAD;

        float xDir = -Mth.sin(yawRad) * Mth.cos(pitchRad);
        float yDir = -Mth.sin(pitchRad);
        float zDir = Mth.cos(yawRad) * Mth.cos(pitchRad);

        Vec3 initialDirectionBase = new Vec3(xDir, yDir, zDir);

        // Apply randomness for inaccuracy, matching BowItem's 'shootFromRotation'
        // The random component is added *before* normalization and scaling.
        RandomSource random = level.getRandom(); // Use the level's random for consistent results

        Vec3 initialMotionWithInaccuracy = initialDirectionBase.add(
                random.nextGaussian() * 0.0075D * inaccuracy, // 0.0075D is a common "noise factor"
                random.nextGaussian() * 0.0075D * inaccuracy,
                random.nextGaussian() * 0.0075D * inaccuracy
        );

        // Normalize and then scale by the full velocity (power * velocityMultiplier)
        Vec3 initialMotion = initialMotionWithInaccuracy.normalize().scale(power * velocityMultiplier);

        // Simulation parameters
        double maxDistance = 120.0D; // Increased to cover typical arrow range
        Vec3 currentPos = startVec;
        Vec3 currentMotion = initialMotion;

        // Simplified physics: apply gravity and air resistance over steps
        float resistance = 0.99F; // Air resistance for arrows
        float gravity = 0.05F;    // Gravity for arrows

        // Predicate to define which entities can be hit.
        // Excludes the shooter.
        Predicate<Entity> canHitEntityPredicate = (entity) -> {
            return entity.isPickable() && entity != player;
        };

        // Increased simulation steps for better range coverage
        for (int i = 0; i < 400; i++) { // Simulate up to 400 ticks (approx 20 seconds of flight)
            Vec3 nextPos = currentPos.add(currentMotion);

            // 1. Check for block collision
            ClipContext blockClipContext = new ClipContext(currentPos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
            BlockHitResult blockHitResult = level.clip(blockClipContext);

            // 2. Check for entity collision
            // Use a small bounding box for the 'arrow' for collision checks.
            // The inflate amount can be slightly tuned, 0.1D is a reasonable approximation.
            AABB arrowBoundingBox = new AABB(currentPos, nextPos).inflate(0.1D);
            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                    level,
                    player, // The shooting entity (owner of the simulated projectile)
                    currentPos,
                    nextPos,
                    arrowBoundingBox,
                    canHitEntityPredicate
            );

            // Determine which hit occurred first (if any)
            if (entityHitResult != null) {
                if (blockHitResult.getType() == HitResult.Type.MISS || entityHitResult.getLocation().distanceToSqr(currentPos) < blockHitResult.getLocation().distanceToSqr(currentPos)) {
                    player.sendSystemMessage(Component.literal("Disparando flecha de hielo hacia entidad: " + entityHitResult.getEntity().getName()));
                    return entityHitResult; // Entity hit first
                }
            }
            if (blockHitResult.getType() != HitResult.Type.MISS) {
                player.sendSystemMessage(Component.literal("Disparando flecha de hielo hacia bloque: " + blockHitResult.getBlockPos()));
                return blockHitResult; // Block hit
            }

            // If nothing hit, update position and motion for the next step
            currentPos = nextPos;

            // Apply air resistance and gravity
            currentMotion = currentMotion.scale(resistance);
            // Apply gravity downwards
            currentMotion = currentMotion.subtract(0.0D, gravity, 0.0D);

            // If the arrow has traveled too far, or its speed is negligible, consider it a miss.
            // The lengthSqr() check is important to prevent infinite loops for tiny motions.
            if (currentPos.distanceToSqr(startVec) > maxDistance * maxDistance || currentMotion.lengthSqr() < 0.001D) {
                break;
            }
        }

        // If no hit occurred within the simulated range, return a miss result.
        // It's better to return HitResult.miss(currentPos) than null for consistency.
        return null;
    }



    public static Vec3 calculateProjectileVelocity(Vec3 origin, Vec3 target, float velocity) {
        // Difference between origin and target
        Vec3 diff = target.subtract(origin);
        double dx = diff.x;
        double dz = diff.z;
        double dy = diff.y;

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        // Calculate the angle (theta) for pitch to arc to the target (assumes flat ground target)
        double angle = Math.atan2(dy, horizontalDistance);

        // Calculate unit direction in x/z and apply angle to y
        double directionX = dx / horizontalDistance;
        double directionZ = dz / horizontalDistance;

        double vx = directionX * Math.cos(angle) * velocity;
        double vz = directionZ * Math.cos(angle) * velocity;
        double vy = Math.sin(angle) * velocity;

        return new Vec3(vx, vy, vz);
    }


}