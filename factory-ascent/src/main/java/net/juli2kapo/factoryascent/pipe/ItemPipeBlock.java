package net.juli2kapo.factoryascent.pipe;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/**
 * Item pipe. Touching pipes form a network that delivers items instantly to every inventory on
 * it. Faces set to {@link PipeConnection#EXTRACT} (with a Wrench) pull from the inventory there.
 */
public class ItemPipeBlock extends BaseEntityBlock {
    public static final Map<Direction, EnumProperty<PipeConnection>> PROPERTIES;
    /** Items pulled per extracting face every {@link ItemPipeBlockEntity#EXTRACT_INTERVAL} ticks. */
    private static final int[] RATE = {4, 16, 32, 64, 128};

    static {
        EnumMap<Direction, EnumProperty<PipeConnection>> map = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) {
            map.put(dir, EnumProperty.create(dir.getSerializedName(), PipeConnection.class));
        }
        PROPERTIES = ImmutableMap.copyOf(map);
    }

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = Shapes.rotateAll(Block.boxZ(6, 0, 8));

    private final Tier tier;
    private final MapCodec<ItemPipeBlock> codec;
    private final Map<BlockState, VoxelShape> shapeCache = new java.util.IdentityHashMap<>();

    public ItemPipeBlock(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.codec = simpleCodec(p -> new ItemPipeBlock(tier, p));
        BlockState state = stateDefinition.any();
        for (var prop : PROPERTIES.values()) state = state.setValue(prop, PipeConnection.NONE);
        registerDefaultState(state);
    }

    public Tier tier() {
        return tier;
    }

    public int rate() {
        return RATE[tier.ordinal()];
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PROPERTIES.values().forEach(builder::add);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeCache.computeIfAbsent(state, s -> {
            VoxelShape shape = CORE;
            for (Direction dir : Direction.values()) {
                if (s.getValue(PROPERTIES.get(dir)) != PipeConnection.NONE) shape = Shapes.or(shape, ARMS.get(dir));
            }
            return shape;
        });
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    public static boolean hasExtractFace(BlockState state) {
        for (var prop : PROPERTIES.values()) {
            if (state.getValue(prop) == PipeConnection.EXTRACT) return true;
        }
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(PROPERTIES.get(dir), connection(context.getLevel(), context.getClickedPos(), dir, PipeConnection.NONE));
        }
        return state;
    }

    private static PipeConnection connection(BlockGetter level, BlockPos pos, Direction dir, PipeConnection previous) {
        BlockPos other = pos.relative(dir);
        BlockState state = level.getBlockState(other);
        if (state.getBlock() instanceof ItemPipeBlock) return PipeConnection.CONNECTED;
        if (level instanceof Level l && l.getCapability(Capabilities.Item.BLOCK, other, state, null, dir.getOpposite()) != null) {
            return previous == PipeConnection.EXTRACT ? PipeConnection.EXTRACT : PipeConnection.CONNECTED;
        }
        return PipeConnection.NONE;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction dir, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        var prop = PROPERTIES.get(dir);
        BlockState updated = state.setValue(prop, connection(level, pos, dir, state.getValue(prop)));
        if (level instanceof ServerLevel server) ItemNetworkManager.get(server).invalidateAt(pos);
        return updated;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            // Placed without a player (commands, structures): work out connections now.
            BlockState connected = state;
            for (Direction dir : Direction.values()) {
                var prop = PROPERTIES.get(dir);
                connected = connected.setValue(prop, connection(level, pos, dir, state.getValue(prop)));
            }
            if (connected != state) level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
        }
        if (level instanceof ServerLevel server) ItemNetworkManager.get(server).onPipeChanged(pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ItemNetworkManager.get(level).onPipeChanged(pos);
    }

    /** Which arm of the pipe a click at {@code hit} landed on, or null for the core. */
    public static @Nullable Direction armAt(BlockPos pos, Vec3 hit) {
        Vec3 local = hit.subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Direction best = Direction.getApproximateNearest(local.x, local.y, local.z);
        double along = Math.abs(local.x * best.getStepX() + local.y * best.getStepY() + local.z * best.getStepZ());
        return along > 3 / 16.0 ? best : null;
    }

    /** Wrench action: flip the arm between delivering and extracting. Returns true if it changed. */
    public boolean toggleExtract(Level level, BlockPos pos, BlockState state, Direction dir) {
        var prop = PROPERTIES.get(dir);
        PipeConnection now = state.getValue(prop);
        if (now == PipeConnection.NONE || level.getBlockState(pos.relative(dir)).getBlock() instanceof ItemPipeBlock) return false;
        level.setBlock(pos, state.setValue(prop, now == PipeConnection.EXTRACT ? PipeConnection.CONNECTED : PipeConnection.EXTRACT), Block.UPDATE_ALL);
        if (level instanceof ServerLevel server) ItemNetworkManager.get(server).invalidateAt(pos);
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.ITEM_PIPE.get() || !hasExtractFace(state)) return null;
        return (l, p, s, be) -> ((ItemPipeBlockEntity) be).serverTick((ServerLevel) l);
    }
}
