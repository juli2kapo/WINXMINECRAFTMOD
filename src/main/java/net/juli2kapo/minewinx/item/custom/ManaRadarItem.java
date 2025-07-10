package net.juli2kapo.minewinx.item.custom;

import net.juli2kapo.minewinx.block.ModBlocks;
import net.juli2kapo.minewinx.util.ModTags;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ManaRadarItem extends Item {
    public ManaRadarItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        if (!pLevel.isClientSide()) {
            handleOreDetection(pPlayer.getOnPos(), pPlayer, pLevel);
        }

        pPlayer.getItemInHand(pUsedHand).hurtAndBreak(1, pPlayer, player -> player.broadcastBreakEvent(player.getUsedItemHand()));

        return InteractionResultHolder.success(pPlayer.getItemInHand(pUsedHand));
    }

    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        if (!pContext.getLevel().isClientSide()) {
            handleOreDetection(pContext.getClickedPos(), pContext.getPlayer(), pContext.getLevel());
        }

        pContext.getItemInHand().hurtAndBreak(1, pContext.getPlayer(), player -> player.broadcastBreakEvent(player.getUsedItemHand()));

        return InteractionResult.SUCCESS;
    }

    private void handleOreDetection(BlockPos initialPos, Player player, Level level) {
        boolean itemFound = false;
        // Max radius to search, consistent with your original 32x32x32 cube.
        // A radius of 32 means the search extends 32 blocks in each direction from the center.
        int maxRadius = 32;

        // Iterate through increasing radii
        for (int r = 0; r <= maxRadius; r++) {
            // Iterate around the cube shell for the current radius 'r'
            // This ensures we check all blocks at a given distance 'r' from the center.
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        // We only want to check the "shell" of the cube at radius 'r',
                        // not the entire cube up to 'r'.
                        // So, if the current point is within a smaller cube (r-1), skip it.
                        if (Math.abs(x) < r && Math.abs(y) < r && Math.abs(z) < r) {
                            continue;
                        }

                        BlockPos currentPos = initialPos.offset(x, y, z);
                        // Make sure we don't check outside the world bounds (optional, but good practice)
                        if (currentPos.getY() < level.getMinBuildHeight() || currentPos.getY() >= level.getMaxBuildHeight()) {
                            continue;
                        }

                        BlockState state = level.getBlockState(currentPos);

                        if (isManaOre(state)) {
                            spawnParticlesTowards(player, currentPos, (ServerLevel) level);
                            // showValuableCoordinates(currentPos, player, state.getBlock());
                            itemFound = true;
                            return; // Exit the method immediately
                        }
                    }
                }
            }
        }

        if (!itemFound) {
            player.sendSystemMessage(Component.literal("Busca mejor"));
        }
    }

    private void spawnParticlesTowards(Player player, BlockPos target, ServerLevel level) {
        Vec3 playerPos = player.getEyePosition(1.0F);
        Vec3 targetCenter = Vec3.atCenterOf(target);
        Vec3 direction = targetCenter.subtract(playerPos).normalize();

        double distance = playerPos.distanceTo(targetCenter);

        for (double i = 0; i < distance; i += 0.25) {
            double px = playerPos.x + direction.x * i;
            double py = playerPos.y + direction.y * i;
            double pz = playerPos.z + direction.z * i;

            level.sendParticles(ParticleTypes.FLAME, px, py, pz, 5, 0.1D, 0.1D, 0.1D, 0.01D);
        }
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltipComponents, TooltipFlag pIsAdvanced) {
        pTooltipComponents.add(Component.translatable("tooltip.minewinx.manaradar"));
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
    }

    private void showValuableCoordinates(BlockPos orePos, Player player, Block block) {
        player.sendSystemMessage(Component.literal("Se encontro: " + I18n.get(block.getDescriptionId()) + " en: " + orePos.getX() + ", " + orePos.getY() + ", " + orePos.getZ()));
    }

    private boolean isManaOre(BlockState state) {
        return state.is(ModTags.Blocks.MANAORES);
    }
}