package net.juli2kapo.factoryascent.trains;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Train Station / loading platform: placed beside the rails (it faces the track). A train arriving
 * on the rails next to it stops (always, only while the station gets redstone, or never), waits
 * (a set time, or until loading is done) and moves items, fluids and energy between its wagons and
 * the containers, tanks and batteries touching the station. Its signal lamp shows green while it
 * holds a train. Pipes may also connect to the station itself to reach the docked wagons.
 */
public class StationBlock extends BaseEntityBlock implements DescribedBlock {
    public static final MapCodec<StationBlock> CODEC = simpleCodec(StationBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape PLATFORM = Block.box(0, 0, 0, 16, 12, 16);
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // the signal post stands at the back corner, away from the track
            Direction back = d.getOpposite();
            double cx = 8 + back.getStepX() * 5, cz = 8 + back.getStepZ() * 5;
            VoxelShape post = Block.box(cx - 1.5, 12, cz - 1.5, cx + 1.5, 16, cz + 1.5);
            SHAPES[d.get2DDataValue()] = Shapes.or(PLATFORM, post);
        }
    }

    public StationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    /** Faces the rails next to it (if any), else away from the player. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = context.getClickedPos().relative(d);
            Level level = context.getLevel();
            if (TrackWalker.isRail(level.getBlockState(p)) || TrackWalker.isRail(level.getBlockState(p.above()))
                    || TrackWalker.isRail(level.getBlockState(p.below()))) {
                facing = d;
                break;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof StationBlockEntity be) {
            sp.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.train_station").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.train_station.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == TrainContent.STATION_BE.get() ? (lvl, pos, st, be) -> ((StationBlockEntity) be).serverTick((ServerLevel) lvl) : null;
    }
}
