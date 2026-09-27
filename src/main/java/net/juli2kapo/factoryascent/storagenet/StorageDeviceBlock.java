package net.juli2kapo.factoryascent.storagenet;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import org.jspecify.annotations.Nullable;

/**
 * The full-block storage devices: controller, drive, terminal and interface. They face the player
 * who placed them and light up ({@link #ONLINE}) while their network is powered and valid.
 */
public class StorageDeviceBlock extends BaseEntityBlock implements StorageNodeBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    public enum Kind { CONTROLLER, DRIVE, TERMINAL, INTERFACE }

    private final Kind kind;
    private final MapCodec<StorageDeviceBlock> codec;

    public StorageDeviceBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new StorageDeviceBlock(kind, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ONLINE, false));
    }

    public Kind kind() {
        return kind;
    }

    static int light(BlockState state) {
        return state.hasProperty(ONLINE) && state.getValue(ONLINE) ? 7 : 0;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ONLINE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (kind) {
            case CONTROLLER -> new StorageControllerBlockEntity(pos, state);
            case DRIVE -> new StorageDriveBlockEntity(pos, state);
            case TERMINAL -> new StorageTerminalBlockEntity(pos, state);
            case INTERFACE -> new StorageInterfaceBlockEntity(pos, state);
        };
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof StorageNodeBlockEntity node) node.serverTick((ServerLevel) lvl);
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel server) StorageNetManager.get(server).onNodeChanged(pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        StorageNetManager.get(level).onNodeChanged(pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        switch (kind) {
            case CONTROLLER -> {
                if (be instanceof StorageControllerBlockEntity controller) player.sendOverlayMessage(controller.statusMessage());
            }
            case DRIVE, TERMINAL -> {
                if (be instanceof net.minecraft.world.MenuProvider provider) player.openMenu(provider, pos);
            }
            case INTERFACE -> {
                if (be instanceof StorageNodeBlockEntity node) player.sendOverlayMessage(statusOf(node.network()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    static Component statusOf(@Nullable StorageNet net) {
        StorageNet.Status status = net == null ? StorageNet.Status.NO_CONTROLLER : net.status();
        return Component.translatable(status.key())
                .withStyle(status == StorageNet.Status.ONLINE ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
