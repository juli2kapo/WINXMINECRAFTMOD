package net.juli2kapo.factoryascent.dyson;

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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * One section of the Mass Driver's electromagnetic rail: four guide posts and two coil rings.
 * Stack {@link MassDriverBlockEntity#RAILS} of them on the breech. Clicks go to the breech below.
 */
public class MassDriverRailBlock extends Block implements DescribedBlock {
    /** Four guide posts and two coil rings around an open bore (see tools/features/dyson.py). */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 4, 16, 4), Block.box(12, 0, 1, 15, 16, 4),
            Block.box(1, 0, 12, 4, 16, 15), Block.box(12, 0, 12, 15, 16, 15),
            Block.box(2, 4, 2, 14, 6, 14), Block.box(2, 10, 2, 14, 12, 14));

    public MassDriverRailBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.mass_driver_rail", MassDriverBlockEntity.RAILS)
                .withStyle(ChatFormatting.GRAY));
    }

    /** The breech this rail belongs to, if it sits within {@link MassDriverBlockEntity#RAILS} blocks below. */
    static @Nullable MassDriverBlockEntity breech(Level level, BlockPos pos) {
        for (int i = 1; i <= MassDriverBlockEntity.RAILS; i++) {
            BlockPos below = pos.below(i);
            if (level.getBlockEntity(below) instanceof MassDriverBlockEntity driver) return driver;
            if (!(level.getBlockState(below).getBlock() instanceof MassDriverRailBlock)) return null;
        }
        return null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        MassDriverBlockEntity driver = breech(level, pos);
        return driver == null ? InteractionResult.TRY_WITH_EMPTY_HAND : MassDriverBlock.interact(driver, stack, level, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        MassDriverBlockEntity driver = breech(level, pos);
        return driver == null ? InteractionResult.PASS : MassDriverBlock.interact(driver, ItemStack.EMPTY, level, player);
    }
}
