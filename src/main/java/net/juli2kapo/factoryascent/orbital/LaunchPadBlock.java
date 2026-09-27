package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One plate of the Launch Pad: eight of them ring a {@link LaunchControllerBlock} to make the 3×3
 * pad. Clicking a plate acts on the controller next to it, so any part of the pad works.
 */
public class LaunchPadBlock extends Block implements DescribedBlock {
    public static final MapCodec<LaunchPadBlock> CODEC = simpleCodec(LaunchPadBlock::new);
    static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public LaunchPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.launch_pad").withStyle(ChatFormatting.GRAY));
    }

    /** The controller this plate belongs to (any of its 8 horizontal neighbours), or null. */
    static @Nullable LaunchControllerBlockEntity controllerNear(Level level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && level.getBlockEntity(pos.offset(dx, 0, dz)) instanceof LaunchControllerBlockEntity c) {
                    return c;
                }
            }
        }
        return null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        LaunchControllerBlockEntity controller = controllerNear(level, pos);
        if (controller == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        return LaunchControllerBlock.interact(controller, stack, level, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        LaunchControllerBlockEntity controller = controllerNear(level, pos);
        if (controller == null) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.factoryascent.pad_no_controller"));
            return InteractionResult.SUCCESS;
        }
        return LaunchControllerBlock.interact(controller, ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND);
    }
}
