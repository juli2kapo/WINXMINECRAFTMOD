package net.juli2kapo.factoryascent.fluid.machine;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 * A section of the Refinery's distillation column. Three stack on the controller; the top one
 * wears the cap. Pipes on any section reach the controller's tanks.
 */
public class RefineryTowerBlock extends Block implements DescribedBlock {
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final MapCodec<RefineryTowerBlock> CODEC = simpleCodec(RefineryTowerBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public RefineryTowerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TOP, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(TOP, !(context.getLevel().getBlockState(context.getClickedPos().above()).getBlock() instanceof RefineryTowerBlock));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (dir == Direction.UP) return state.setValue(TOP, !(neighborState.getBlock() instanceof RefineryTowerBlock));
        return state;
    }

    /** The refinery controller under this column, if any. */
    public static @Nullable RefineryBlockEntity controller(Level level, BlockPos pos) {
        for (int i = 1; i <= RefineryBlockEntity.TOWER; i++) {
            BlockPos p = pos.below(i);
            if (level.getBlockEntity(p) instanceof RefineryBlockEntity r) return r;
            if (!(level.getBlockState(p).getBlock() instanceof RefineryTowerBlock)) return null;
        }
        return null;
    }

    public static @Nullable ResourceHandler<FluidResource> fluidHandler(Level level, BlockPos pos) {
        RefineryBlockEntity r = controller(level, pos);
        return r == null ? null : r.fluidHandler(null);
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("desc.factoryascent.refinery_tower").withStyle(ChatFormatting.GRAY));
    }
}
