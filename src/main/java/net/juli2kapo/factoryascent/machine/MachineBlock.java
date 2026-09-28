package net.juli2kapo.factoryascent.machine;

import com.mojang.serialization.MapCodec;
import net.juli2kapo.factoryascent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** One block class for every machine; behaviour lives in the block entity. */
public class MachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final VoxelShape SOLAR_SHAPE = Block.box(0, 0, 0, 16, 6, 16);
    private static final VoxelShape QUERN_SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 6, 15), Block.box(3, 6, 3, 13, 11, 13));
    private static final VoxelShape SIEVE_SHAPE = Block.box(0, 0, 0, 16, 12, 16);
    private static final VoxelShape RACK_SHAPE = Block.box(1, 0, 1, 15, 15, 15);
    private static final VoxelShape FLOODLIGHT_SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 3, 14), Block.box(3, 3, 3, 13, 14, 13));
    private static final VoxelShape CHARGER_SHAPE = Block.box(1, 0, 1, 15, 10, 15);
    private static final VoxelShape VACUUM_SHAPE = Shapes.or(Block.box(0, 4, 0, 16, 16, 16), Block.box(4, 0, 4, 12, 4, 12));

    private final MachineType type;
    private final MapCodec<MachineBlock> codec;

    public MachineBlock(MachineType type, Properties properties) {
        super(properties);
        this.type = type;
        this.codec = simpleCodec(p -> new MachineBlock(type, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    public MachineType type() {
        return type;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = type.hasFacing() ? context.getHorizontalDirection().getOpposite() : Direction.NORTH;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (type) {
            case SOLAR_PANEL -> SOLAR_SHAPE;
            case QUERN -> QUERN_SHAPE;
            case SIEVE -> SIEVE_SHAPE;
            case DRYING_RACK -> RACK_SHAPE;
            case FLOODLIGHT -> FLOODLIGHT_SHAPE;
            case CHARGER -> CHARGER_SHAPE;
            case VACUUM_HOPPER -> VACUUM_SHAPE;
            default -> Shapes.block();
        };
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return false;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.machine(type).get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> beType) {
        if (level.isClientSide()) return null;
        return beType == ModBlockEntities.machine(type).get()
                ? (lvl, pos, st, be) -> ((AbstractMachineBlockEntity) be).serverTick((ServerLevel) lvl, pos, st)
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (type.power() == MachineType.Power.MANUAL && player.isShiftKeyDown()) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ProcessingMachineBlockEntity quern) {
                quern.crank(player);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AbstractMachineBlockEntity be) {
            player.openMenu(be, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof AbstractMachineBlockEntity be ? be.comparatorSignal() : 0;
    }

    /**
     * Turns a machine to face {@code facing} (horizontal only): the Wrench and the machine screen's
     * Sides panel. For an Energy Cell the facing is its output face. Returns false if the block
     * can not face that way.
     */
    public static boolean setFacing(Level level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MachineBlock machine) || !machine.type().hasFacing() || !facing.getAxis().isHorizontal()) {
            return false;
        }
        if (state.getValue(FACING) != facing) {
            level.setBlock(pos, state.setValue(FACING, facing), Block.UPDATE_ALL);
            level.invalidateCapabilities(pos);
        }
        return true;
    }

    public static boolean isActive(BlockState state) {
        return state.hasProperty(ACTIVE) && state.getValue(ACTIVE);
    }
}
