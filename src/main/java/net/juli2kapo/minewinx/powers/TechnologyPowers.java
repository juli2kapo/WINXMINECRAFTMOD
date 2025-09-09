package net.juli2kapo.minewinx.powers;

import net.juli2kapo.minewinx.entity.ModEntities;
import net.juli2kapo.minewinx.entity.PistonEntity;
import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
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

import java.util.List;

public class TechnologyPowers {

    public static void freezeTime(Player player) {
        int stage = PlayerDataProvider.getStage(player);
        if (stage == 0) return;
        
        Level level = player.level();
        if (level.isClientSide()) return;
        
        // Define size and duration based on stage
        int size = stage * 5; // 5, 10, or 15 blocks
        float duration = 1.5f + (stage - 1) * 0.5f; // 1.5, 2.0, or 2.5 seconds
        
        // Get dimension ID
        String dimension = level.dimension().location().toString();
        
        // Calculate cube coordinates around player
        BlockPos playerPos = player.blockPosition();
        int x1 = playerPos.getX() - size;
        int y1 = playerPos.getY() - size;
        int z1 = playerPos.getZ() - size;
        int x2 = playerPos.getX() + size;
        int y2 = playerPos.getY() + size;
        int z2 = playerPos.getZ() + size;
        
        // Server instance for command execution
        ServerLevel serverLevel = (ServerLevel) level;
        
        // First, make player immune to time freeze
        String excludeCommand = "setTickrate exclude " + player.getName().getString() + " true";
        serverLevel.getServer().getCommands().performPrefixedCommand(
            serverLevel.getServer().createCommandSourceStack(), excludeCommand);
        
        // Freeze time in the area
        String freezeCommand = "setTickrate area " + dimension + " " + 
                            x1 + " " + y1 + " " + z1 + " " + 
                            x2 + " " + y2 + " " + z2 + " 0";
        serverLevel.getServer().getCommands().performPrefixedCommand(
            serverLevel.getServer().createCommandSourceStack(), freezeCommand);
        
        // Play sound effect
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.5F);
        
        // Schedule restoration of normal time
        int tickDuration = (int)(duration * 20); // Convert seconds to ticks

        // Use Minecraft's built-in scheduler instead of TickFreezeManager
        serverLevel.getServer().getLevel(serverLevel.dimension()).getServer().tell(new net.minecraft.server.TickTask(
            serverLevel.getServer().getTickCount() + tickDuration, () -> {
                // Restore normal time
                String unfreezeCommand = "setTickrate area " + dimension + " " + 
                                    x1 + " " + y1 + " " + z1 + " " + 
                                    x2 + " " + y2 + " " + z2 + " 20";
                serverLevel.getServer().getCommands().performPrefixedCommand(
                    serverLevel.getServer().createCommandSourceStack(), unfreezeCommand);
                
                // Remove player from exclusion list
                String removeExcludeCommand = "setTickrate exclude " + player.getName().getString() + " false";
                serverLevel.getServer().getCommands().performPrefixedCommand(
                    serverLevel.getServer().createCommandSourceStack(), removeExcludeCommand);
            }
        ));
        
        // Visual effects
        if (serverLevel != null) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 1.0, player.getZ(),
                50, size/2.0, size/2.0, size/2.0, 0.05);
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

        executePistonSmashWithEntity(player, targetPos, stage);
    }

        private static void executePistonSmashWithEntity(Player caster, BlockPos targetPos, int stage) {
        Level level = caster.level();
        ServerLevel serverLevel = (ServerLevel) level;

        // Find highest solid block at target location
        BlockPos surfacePos = findSurfaceBlock(level, targetPos);
        if (surfacePos == null) return;

        // Size and damage scale with stage
        float scale = 4.0f + (stage - 1) * 6f; // stage 1: 4, stage 2: 10, stage 3: 16
        float damage = 6.0F + (stage - 1) * 8.0F;
        int depth = stage * 2; // 2, 4, 6 blocks deep

        // Spawn single piston entity above surface center
        BlockPos pistonPos = surfacePos.above(-2 + (stage - 1) * 4);

        // Create single piston entity at center
        Vec3 spawnPos = new Vec3(
            pistonPos.getX() + 0.5, 
            pistonPos.getY() + 0.5, 
            pistonPos.getZ() + 0.5
        );
        
        PistonEntity pistonEntity = new PistonEntity(ModEntities.PISTON.get(), level);
        pistonEntity.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        
        // Store impact data in the entity for later use
        pistonEntity.getPersistentData().putInt("impactX", surfacePos.getX());
        pistonEntity.getPersistentData().putInt("impactY", surfacePos.getY());
        pistonEntity.getPersistentData().putInt("impactZ", surfacePos.getZ());
        pistonEntity.getPersistentData().putFloat("damage", damage);
        pistonEntity.getPersistentData().putInt("depth", depth);
        pistonEntity.getPersistentData().putString("casterUUID", caster.getUUID().toString());
        pistonEntity.getPersistentData().putInt("stage", stage);
        pistonEntity.getPersistentData().putFloat("scale", scale); // Store the scale
        
        level.addFreshEntity(pistonEntity);
        // Play piston sound
        level.playSound(null, pistonPos.getX(), pistonPos.getY(), pistonPos.getZ(),
            SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 2.0F, 0.5F);
    }


    // private static BlockPos findSurfaceBlock(Level level, BlockPos startPos) {
        
    //     // Find the highest solid block at the given x,z coordinates
    //     for (int y = level.getMaxBuildHeight(); y >= level.getMinBuildHeight(); y--) {
    //         BlockPos checkPos = new BlockPos(startPos.getX(), y, startPos.getZ());
    //         BlockState state = level.getBlockState(checkPos);
    //         if (!state.isAir() && state.isSolidRender(level, checkPos)) {
    //             return checkPos;
    //         }
    //     }
    //     return startPos; // Fallback to original position
    // }
    private static BlockPos findSurfaceBlock(Level level, BlockPos startPos) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, startPos.getX(), startPos.getZ());
        return new BlockPos(startPos.getX(), y, startPos.getZ());
    }
}