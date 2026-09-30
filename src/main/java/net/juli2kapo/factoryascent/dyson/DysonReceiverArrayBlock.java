package net.juli2kapo.factoryascent.dyson;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A rectenna panel: eight of these around a Dyson Receiver make its 3×3 array. Clicks go to the receiver. */
public class DysonReceiverArrayBlock extends Block implements DescribedBlock {
    static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    public DysonReceiverArrayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.dyson_receiver_array").withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = pos.offset(dx, 0, dz);
                if (level.getBlockEntity(p) instanceof DysonReceiverBlockEntity) {
                    return DysonReceiverBlock.interact(level, p, player);
                }
            }
        }
        return InteractionResult.PASS;
    }
}
