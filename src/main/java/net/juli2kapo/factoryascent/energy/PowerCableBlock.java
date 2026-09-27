package net.juli2kapo.factoryascent.energy;

import com.mojang.serialization.MapCodec;
import net.juli2kapo.factoryascent.Tier;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/** A thin cable that joins every touching cable into one network and connects to anything with energy. */
public class PowerCableBlock extends PipeBlock implements EntityBlock {
    /** FE per tick a network can move; a network runs at the rate of its slowest cable. */
    private static final int[] RATE = {512, 2_048, 8_192, 32_768, 131_072};

    private final Tier tier;
    private final MapCodec<PowerCableBlock> codec;

    public PowerCableBlock(Tier tier, Properties properties) {
        super(6f, properties);
        this.tier = tier;
        this.codec = simpleCodec(p -> new PowerCableBlock(tier, p));
        BlockState state = stateDefinition.any();
        for (var prop : PROPERTY_BY_DIRECTION.values()) state = state.setValue(prop, false);
        registerDefaultState(state);
    }

    public Tier tier() {
        return tier;
    }

    public int rate() {
        return RATE[tier.ordinal()];
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(dir), connects(level, pos, dir));
        }
        return state;
    }

    public static boolean connects(BlockGetter level, BlockPos pos, Direction dir) {
        BlockPos other = pos.relative(dir);
        BlockState state = level.getBlockState(other);
        if (state.getBlock() instanceof PowerCableBlock) return true;
        if (level instanceof Level l) {
            return l.getCapability(Capabilities.Energy.BLOCK, other, state, null, dir.getOpposite()) != null;
        }
        return false;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction dir, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (level instanceof ServerLevel server) {
            EnergyNetworkManager.get(server).onCableNeighborChanged(pos);
        }
        return state.setValue(PROPERTY_BY_DIRECTION.get(dir), connects(level, pos, dir));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            BlockState connected = withConnections(level, pos, state);
            if (connected != state) level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
        }
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            EnergyNetworkManager.get(server).onCablePlaced(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        EnergyNetworkManager.get(level).onCableRemoved(pos);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerCableBlockEntity(pos, state);
    }
}
