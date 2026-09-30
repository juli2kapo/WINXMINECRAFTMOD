package net.juli2kapo.factoryascent.nuclear;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Wall blocks of a fission reactor that connect it to the world: the Access Port (fuel rods and
 * coolant items in, spent rods and empty buckets/cells out), the Power Port (FE out), the Coolant
 * Port (pumps water from source blocks touching it) and the Redstone Port (a signal SCRAMs the
 * reactor; a comparator reads its temperature).
 */
public class ReactorPortBlock extends BaseEntityBlock implements DescribedBlock {
    public enum Kind { ACCESS, POWER, COOLANT, REDSTONE }

    private final Kind kind;
    private final MapCodec<ReactorPortBlock> codec;

    public ReactorPortBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new ReactorPortBlock(kind, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ReactorPortBlockEntity port && port.controller(level) != null) {
            ReactorControllerBlockEntity c = port.controller(level);
            c.rescan(level);
            player.openMenu(c, c.getBlockPos());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorPortBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || kind == Kind.ACCESS || kind == Kind.REDSTONE) return null;
        return type == PowerContent.REACTOR_PORT_BE.get() ? (l, p, s, be) -> ((ReactorPortBlockEntity) be).serverTick((ServerLevel) l) : null;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return kind == Kind.REDSTONE;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof ReactorPortBlockEntity port && port.controller(level) != null
                ? port.controller(level).comparatorSignal() : 0;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.reactor_" + kind.name().toLowerCase(Locale.ROOT) + "_port")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.reactor_port_wall").withStyle(ChatFormatting.DARK_GRAY));
    }
}
