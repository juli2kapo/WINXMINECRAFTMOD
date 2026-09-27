package net.juli2kapo.factoryascent.machine;

import com.mojang.serialization.MapCodec;
import net.juli2kapo.factoryascent.Tier;
import net.juli2kapo.factoryascent.item.UpgradeKitItem;
import net.juli2kapo.factoryascent.registry.ModBlockEntities;
import net.juli2kapo.factoryascent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

/** One block class for every tiered machine; behaviour lives in the block entity. */
public class MachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final VoxelShape SOLAR_SHAPE = Block.box(0, 0, 0, 16, 6, 16);

    private final MachineType type;
    private final Tier tier;
    private final MapCodec<MachineBlock> codec;

    public MachineBlock(MachineType type, Tier tier, Properties properties) {
        super(properties);
        this.type = type;
        this.tier = tier;
        this.codec = simpleCodec(p -> new MachineBlock(type, tier, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
    }

    public MachineType type() {
        return type;
    }

    public Tier tier() {
        return tier;
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
        return type == MachineType.SOLAR_PANEL ? SOLAR_SHAPE : Shapes.block();
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

    /** Upgrading swaps the block for the next tier; keep the block entity (and its contents). */
    @Override
    protected boolean shouldChangedStateKeepBlockEntity(BlockState oldState) {
        return oldState.getBlock() instanceof MachineBlock other && other.type == type;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof UpgradeKitItem kit) {
            return tryUpgrade(kit, stack, state, level, pos, player);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    private InteractionResult tryUpgrade(UpgradeKitItem kit, ItemStack stack, BlockState state, Level level,
                                         BlockPos pos, Player player) {
        Tier next = tier.next();
        if (next == null) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.factoryascent.max_tier"));
            return InteractionResult.FAIL;
        }
        if (kit.tier() != next) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.factoryascent.wrong_kit",
                        next.displayName(), kit.tier().displayName()));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        Block target = ModBlocks.machine(type, next).get();
        BlockState upgraded = target.defaultBlockState()
                .setValue(FACING, state.getValue(FACING))
                .setValue(ACTIVE, state.getValue(ACTIVE));
        level.setBlock(pos, upgraded, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof AbstractMachineBlockEntity be) {
            be.onTierChanged();
        }
        level.invalidateCapabilities(pos);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
        player.sendOverlayMessage(Component.translatable("message.factoryascent.upgraded", next.displayName()));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
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

    public static boolean isActive(BlockState state) {
        return state.hasProperty(ACTIVE) && state.getValue(ACTIVE);
    }

}
