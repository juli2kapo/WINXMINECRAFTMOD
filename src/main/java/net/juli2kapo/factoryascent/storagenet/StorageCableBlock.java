package net.juli2kapo.factoryascent.storagenet;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.Nullable;

/** Thin cable joining storage devices into one network. Connects only to other storage blocks. */
public class StorageCableBlock extends PipeBlock implements EntityBlock, StorageNodeBlock {
    public static final MapCodec<StorageCableBlock> CODEC = simpleCodec(StorageCableBlock::new);

    public StorageCableBlock(Properties properties) {
        super(4f, properties);
        BlockState state = stateDefinition.any();
        for (var prop : PROPERTY_BY_DIRECTION.values()) state = state.setValue(prop, false);
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    private static BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(dir), connects(level, pos, dir));
        }
        return state;
    }

    public static boolean connects(BlockGetter level, BlockPos pos, Direction dir) {
        return level.getBlockState(pos.relative(dir)).getBlock() instanceof StorageNodeBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction dir, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(dir), neighborState.getBlock() instanceof StorageNodeBlock);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            BlockState connected = withConnections(level, pos, state);
            if (connected != state) level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
            if (level instanceof ServerLevel server) StorageNetManager.get(server).onNodeChanged(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        StorageNetManager.get(level).onNodeChanged(pos);
    }

    /** Empty-handed right-click opens the network screen; holding anything leaves the click to the item. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) return net.minecraft.world.InteractionResult.PASS;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) net.juli2kapo.factoryascent.ui.NetworkScreens.open(sp, pos);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageCableBlockEntity(pos, state);
    }
}
