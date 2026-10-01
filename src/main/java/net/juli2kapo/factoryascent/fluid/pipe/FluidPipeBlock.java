package net.juli2kapo.factoryascent.fluid.pipe;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.juli2kapo.factoryascent.item.WrenchItem;
import net.juli2kapo.factoryascent.pipe.PipeConnection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/**
 * Fluid pipe: a riveted metal duct with a glass window, in the four stage colours of the item
 * pipes and cables. Touching pipes form a network that delivers fluid to every tank and machine
 * on it; faces set to {@link PipeConnection#EXTRACT} (Wrench, or the pipe screen) pump out of the
 * block there. Throughput per extracting face grows with the tier.
 */
public class FluidPipeBlock extends BaseEntityBlock implements DescribedBlock {
    public static final Map<Direction, EnumProperty<PipeConnection>> PROPERTIES;
    /** mB per tick an extracting face pulls (and the most a face can push into the network per tick). */
    private static final int[] RATE = {100, 400, 1600, 6400};

    static {
        EnumMap<Direction, EnumProperty<PipeConnection>> map = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.values()) map.put(dir, EnumProperty.create(dir.getSerializedName(), PipeConnection.class));
        PROPERTIES = ImmutableMap.copyOf(map);
    }

    private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);
    private static final Map<Direction, VoxelShape> ARMS = Shapes.rotateAll(Block.boxZ(8, 0, 4));

    private final Tier tier;
    private final MapCodec<FluidPipeBlock> codec;
    private final Map<BlockState, VoxelShape> shapeCache = new java.util.IdentityHashMap<>();

    public FluidPipeBlock(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.codec = simpleCodec(p -> new FluidPipeBlock(tier, p));
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

    public static int rate(Tier tier) {
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
        for (var prop : PROPERTIES.values()) if (state.getValue(prop) == PipeConnection.EXTRACT) return true;
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
        if (state.getBlock() instanceof FluidPipeBlock) return PipeConnection.CONNECTED;
        if (level instanceof Level l && l.getCapability(Capabilities.Fluid.BLOCK, other, state, null, dir.getOpposite()) != null) {
            return previous == PipeConnection.EXTRACT ? PipeConnection.EXTRACT : PipeConnection.CONNECTED;
        }
        return PipeConnection.NONE;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction dir, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        var prop = PROPERTIES.get(dir);
        BlockState updated = state.setValue(prop, connection(level, pos, dir, state.getValue(prop)));
        if (level instanceof ServerLevel server) FluidNetworkManager.get(server).invalidateAt(pos);
        return updated;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            BlockState connected = state;
            for (Direction dir : Direction.values()) {
                var prop = PROPERTIES.get(dir);
                connected = connected.setValue(prop, connection(level, pos, dir, state.getValue(prop)));
            }
            if (connected != state) level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
        }
        if (level instanceof ServerLevel server) FluidNetworkManager.get(server).onPipeChanged(pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        FluidNetworkManager.get(level).onPipeChanged(pos);
    }

    /** Which arm a click at {@code hit} landed on, or null for the core. */
    public static @Nullable Direction armAt(BlockPos pos, Vec3 hit) {
        Vec3 local = hit.subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Direction best = Direction.getApproximateNearest(local.x, local.y, local.z);
        double along = Math.abs(local.x * best.getStepX() + local.y * best.getStepY() + local.z * best.getStepZ());
        return along > 4 / 16.0 ? best : null;
    }

    public static boolean configurable(Level level, BlockPos pos, BlockState state, Direction dir) {
        return state.getValue(PROPERTIES.get(dir)) != PipeConnection.NONE
                && !(level.getBlockState(pos.relative(dir)).getBlock() instanceof FluidPipeBlock);
    }

    /** Sets the arm towards {@code dir} to extracting or delivering. False if that arm touches no tank or machine. */
    public boolean setExtract(Level level, BlockPos pos, BlockState state, Direction dir, boolean extract) {
        if (!configurable(level, pos, state, dir)) return false;
        var prop = PROPERTIES.get(dir);
        PipeConnection wanted = extract ? PipeConnection.EXTRACT : PipeConnection.CONNECTED;
        if (state.getValue(prop) == wanted) return true;
        level.setBlock(pos, state.setValue(prop, wanted), Block.UPDATE_ALL);
        if (level instanceof ServerLevel server) FluidNetworkManager.get(server).invalidateAt(pos);
        return true;
    }

    public boolean toggleExtract(Level level, BlockPos pos, BlockState state, Direction dir) {
        return setExtract(level, pos, state, dir, state.getValue(PROPERTIES.get(dir)) != PipeConnection.EXTRACT);
    }

    /** The Wrench flips the clicked arm between delivering and extracting (as on item pipes). */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof WrenchItem)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        Direction arm = armAt(pos, hit.getLocation());
        if (arm == null) arm = hit.getDirection();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (toggleExtract(level, pos, state, arm)) {
            boolean extracting = level.getBlockState(pos).getValue(PROPERTIES.get(arm)) == PipeConnection.EXTRACT;
            player.sendOverlayMessage(Component.translatable(extracting ? "message.factoryascent.fluid_pipe_extract"
                    : "message.factoryascent.fluid_pipe_insert"));
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, extracting ? 1.2f : 0.9f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    /** Empty-handed right-click opens the pipe screen. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) FluidPipeScreens.open(sp, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != FluidContent.FLUID_PIPE_BE.get() || !hasExtractFace(state)) return null;
        return (l, p, s, be) -> ((FluidPipeBlockEntity) be).serverTick((ServerLevel) l);
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(tier.displayName());
        tooltip.accept(Component.translatable("desc.factoryascent.fluid_pipe").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.fluid_pipe_rate", rate() * 20).withStyle(ChatFormatting.DARK_AQUA));
    }
}
