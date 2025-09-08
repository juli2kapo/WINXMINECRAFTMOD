package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.effect.ModEffects;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.damagesource.DamageSource;

import java.util.List;
import java.util.Random;

public class TechnologyPowers {

    /**
     * Short-range X-ray vision that allows seeing through blocks for a limited time/range
     */
    public static void shortRangeXray(Player player) {
    int stage = PlayerDataProvider.getStage(player);
    if (stage == 0) return;

    Level level = player.level();
    if (level.isClientSide()) return;

    // Duration and range scale with stage
    int duration = 100 + (stage * 60); // 5s base + 3s per stage
    int range = 8 + (stage * 4); // 8 blocks base + 4 per stage
    int amplifier = stage - 1; // Amplifier affects range

    // Apply both night vision and X-ray vision effects
    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, amplifier, false, false, true));
    player.addEffect(new MobEffectInstance(ModEffects.X_RAY_VISION.get(), duration, amplifier, false, false, true));
    
    // Play tech sound
    level.playSound(null, player.getX(), player.getY(), player.getZ(), 
        SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 2.0F);

    // Create particle effect to show activation
    if (level instanceof ServerLevel serverLevel) {
        serverLevel.sendParticles(ParticleTypes.END_ROD, 
            player.getX(), player.getEyeY(), player.getZ(), 
            10, 0.5, 0.5, 0.5, 0.1);
    }
}

    /**
     * Forces targeted player to drop items or damages mobs and drops loot
     */
    public static void itemDrop(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        // Raycast to find target
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        double range = 16.0 + (stage * 8.0); // Range scales with stage
        Vec3 endPos = eyePos.add(lookVec.scale(range));

        // Check for entity hit
        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            player, eyePos, endPos, searchBox, 
            (entity) -> !entity.isSpectator() && entity.isPickable() && entity != player, 
            range * range
        );

        if (entityHit != null) {
            Entity target = entityHit.getEntity();
            
            if (target instanceof Player targetPlayer) {
                // Force player to drop items
                handlePlayerItemDrop(player, targetPlayer, stage);
            } else if (target instanceof LivingEntity livingTarget) {
                // Damage mob and drop loot
                handleMobDamageAndDrop(player, livingTarget, stage);
            }
        }

        // Play tech sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(), 
            SoundEvents.DISPENSER_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    private static void handlePlayerItemDrop(Player caster, Player target, int stage) {
        Level level = caster.level();
        int itemsToDrop = stage; // 1-3 items based on stage

        // Find non-empty inventory slots
        List<Integer> nonEmptySlots = target.getInventory().items.stream()
            .map(target.getInventory().items::indexOf)
            .filter(i -> !target.getInventory().getItem(i).isEmpty())
            .toList();

        if (nonEmptySlots.isEmpty()) return;

        RandomSource random = level.getRandom();

        int actualDrops = Math.min(itemsToDrop, nonEmptySlots.size());

        for (int i = 0; i < actualDrops; i++) {
            int randomSlot = nonEmptySlots.get(random.nextInt(nonEmptySlots.size()));
            ItemStack stack = target.getInventory().getItem(randomSlot);
            
            if (!stack.isEmpty()) {
                // Drop 1 item from the stack
                ItemStack dropStack = stack.split(1);
                target.drop(dropStack, true);
            }
        }

        // Create particle effects
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0, target.getZ(),
                15, 0.5, 0.5, 0.5, 0.1);
        }
    }

    private static void handleMobDamageAndDrop(Player caster, LivingEntity target, int stage) {
        Level level = caster.level();
        ServerLevel serverLevel = (ServerLevel) level;

        // Damage scales with stage
        float damage = 3.0F + (stage * 2.0F);
        target.hurt(caster.damageSources().indirectMagic(caster, caster), damage);

        // Loot drop amount scales with stage
        int extraDrops = stage;
        
        // Get the mob's loot table
        if (target.getLootTable() != null) {
            LootTable lootTable = serverLevel.getServer().getLootData().getLootTable(target.getLootTable());
            
            LootParams.Builder lootParamsBuilder = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, target)
                .withParameter(LootContextParams.ORIGIN, target.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, caster.damageSources().indirectMagic(caster, caster));

            // Generate extra loot drops
            for (int i = 0; i < extraDrops; i++) {
                List<ItemStack> loot = lootTable.getRandomItems(lootParamsBuilder.create(LootContextParamSets.ENTITY));
                for (ItemStack stack : loot) {
                    if (!stack.isEmpty()) {
                        ItemEntity itemEntity = new ItemEntity(level, target.getX(), target.getY(), target.getZ(), stack);
                        level.addFreshEntity(itemEntity);
                    }
                }
            }
        }

        // Chance to drop mob head (scales with stage)
        float headDropChance = 0.02F + (stage * 0.03F); // 2% base + 3% per stage
        if (level.getRandom().nextFloat() < headDropChance) {
            ItemStack head = getMobHead(target);
            if (!head.isEmpty()) {
                ItemEntity headEntity = new ItemEntity(level, target.getX(), target.getY(), target.getZ(), head);
                level.addFreshEntity(headEntity);
            }
        }

        // Create particle effects
        serverLevel.sendParticles(ParticleTypes.CRIT,
            target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
            20, target.getBbWidth() / 2, target.getBbHeight() / 4, target.getBbWidth() / 2, 0.1);
    }

    private static ItemStack getMobHead(LivingEntity entity) {
        // Return appropriate mob head based on entity type
        String entityType = entity.getType().toString();
        
        return switch (entityType) {
            case "minecraft:zombie" -> new ItemStack(Items.ZOMBIE_HEAD);
            case "minecraft:skeleton" -> new ItemStack(Items.SKELETON_SKULL);
            case "minecraft:wither_skeleton" -> new ItemStack(Items.WITHER_SKELETON_SKULL);
            case "minecraft:creeper" -> new ItemStack(Items.CREEPER_HEAD);
            case "minecraft:dragon" -> new ItemStack(Items.DRAGON_HEAD);
            case "minecraft:piglin" -> new ItemStack(Items.PIGLIN_HEAD);
            // For other mobs, return player head with custom texture (simplified)
            default -> new ItemStack(Items.PLAYER_HEAD);
        };
    }

    /**
     * Spawns a piston that smashes down, creating damage and breaking blocks
     */
    public static void pistonSmash(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0) return;

        Level level = player.level();
        if (level.isClientSide()) return;

        // Raycast to find target location
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        double range = 20.0 + (stage * 5.0); // Range scales with stage

        BlockHitResult hit = level.clip(new ClipContext(eyePos, eyePos.add(lookVec.scale(range)), 
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        BlockPos targetPos;
        if (hit.getType() == HitResult.Type.BLOCK) {
            targetPos = hit.getBlockPos().above();
        } else {
            targetPos = new BlockPos((int)eyePos.add(lookVec.scale(range)).x, 
                                   (int)eyePos.add(lookVec.scale(range)).y, 
                                   (int)eyePos.add(lookVec.scale(range)).z);
        }

        executePistonSmash(player, targetPos, stage);
    }

    private static void executePistonSmash(Player caster, BlockPos targetPos, int stage) {
        Level level = caster.level();
        ServerLevel serverLevel = (ServerLevel) level;

        // Find highest solid block at target location
        BlockPos surfacePos = findSurfaceBlock(level, targetPos);
        if (surfacePos == null) return;

        // Size and damage scale with stage
        int pistonSize = 1 + stage; // 2x2, 3x3, 4x4
        float damage = 6.0F + (stage * 4.0F);
        int depth = stage; // 1, 2, 3 blocks deep

        // Spawn piston above surface
        BlockPos pistonPos = surfacePos.above(3 + stage);
        
        // Create piston blocks temporarily for visual effect
        for (int x = -pistonSize/2; x <= pistonSize/2; x++) {
            for (int z = -pistonSize/2; z <= pistonSize/2; z++) {
                BlockPos blockPos = pistonPos.offset(x, 0, z);
                level.setBlockAndUpdate(blockPos, Blocks.PISTON.defaultBlockState());
            }
        }

        // Play piston sound
        level.playSound(null, pistonPos.getX(), pistonPos.getY(), pistonPos.getZ(),
            SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 2.0F, 0.5F);

        // Schedule the smash effect after a short delay
        level.scheduleTick(pistonPos, Blocks.PISTON, 10);
        
        // Execute smash after delay (simplified immediate execution)
        executePistonImpact(serverLevel, surfacePos, pistonSize, damage, depth, caster);
    }

    private static void executePistonImpact(ServerLevel level, BlockPos impactPos, int size, float damage, int depth, Player caster) {
        // Remove temporary piston blocks
        for (int x = -size/2; x <= size/2; x++) {
            for (int z = -size/2; z <= size/2; z++) {
                BlockPos blockPos = impactPos.above(3 + PlayerDataProvider.getStage(caster)).offset(x, 0, z);
                if (level.getBlockState(blockPos).is(Blocks.PISTON)) {
                    level.removeBlock(blockPos, false);
                }
            }
        }

        // Damage entities in impact area
        AABB damageArea = new AABB(impactPos).inflate(size);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, damageArea,
            entity -> entity != caster && entity.isAlive());

        for (LivingEntity entity : entities) {
            entity.hurt(caster.damageSources().indirectMagic(caster, caster), damage);
            
            // Push entities down
            Vec3 pushDirection = new Vec3(0, -1.5, 0);
            entity.setDeltaMovement(entity.getDeltaMovement().add(pushDirection));
        }

        // Break blocks to create hole
        for (int x = -size/2; x <= size/2; x++) {
            for (int z = -size/2; z <= size/2; z++) {
                for (int y = 0; y <= depth; y++) {
                    BlockPos blockPos = impactPos.offset(x, -y, z);
                    BlockState state = level.getBlockState(blockPos);
                    
                    if (!state.isAir() && state.getDestroySpeed(level, blockPos) >= 0) {
                        level.destroyBlock(blockPos, true, caster);
                    }
                }
            }
        }

        // Create impact effects
        level.sendParticles(ParticleTypes.EXPLOSION,
            impactPos.getX(), impactPos.getY(), impactPos.getZ(),
            20, size / 2.0, 1.0, size / 2.0, 0.2);

        level.sendParticles(ParticleTypes.CLOUD,
            impactPos.getX(), impactPos.getY(), impactPos.getZ(),
            30, size / 2.0, 0.5, size / 2.0, 0.1);

        // Play impact sound
        level.playSound(null, impactPos.getX(), impactPos.getY(), impactPos.getZ(),
            SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 2.0F, 0.8F);
    }

    private static BlockPos findSurfaceBlock(Level level, BlockPos startPos) {
        // Find the highest solid block at the given x,z coordinates
        for (int y = level.getMaxBuildHeight(); y >= level.getMinBuildHeight(); y--) {
            BlockPos checkPos = new BlockPos(startPos.getX(), y, startPos.getZ());
            BlockState state = level.getBlockState(checkPos);
            
            if (!state.isAir() && state.isSolidRender(level, checkPos)) {
                return checkPos;
            }
        }
        return startPos; // Fallback to original position
    }
}