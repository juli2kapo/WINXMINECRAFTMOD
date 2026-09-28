package net.juli2kapo.factoryascent.space;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
import org.jspecify.annotations.Nullable;

/**
 * The two FE-powered life-support blocks: the Oxygen Compressor (fills suits; right-click opens
 * its screen, or right-click with a suit to swap it in) and the Oxygen Sealer (keeps a breathable
 * bubble; right-click shows its status). {@link #LIT} is on while they work.
 */
public class OxygenMachineBlock extends BaseEntityBlock implements DescribedBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public enum Kind { COMPRESSOR, SEALER }

    private final Kind kind;

    public OxygenMachineBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.NORTH));
    }

    private static final MapCodec<OxygenMachineBlock> COMPRESSOR_CODEC = simpleCodec(p -> new OxygenMachineBlock(p, Kind.COMPRESSOR));
    private static final MapCodec<OxygenMachineBlock> SEALER_CODEC = simpleCodec(p -> new OxygenMachineBlock(p, Kind.SEALER));

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return kind == Kind.COMPRESSOR ? COMPRESSOR_CODEC : SEALER_CODEC;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind == Kind.COMPRESSOR ? new OxygenCompressorBlockEntity(pos, state) : new OxygenSealerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type == SpaceContent.OXYGEN_COMPRESSOR_BE.get()) {
            return (l, p, s, be) -> ((OxygenCompressorBlockEntity) be).serverTick((ServerLevel) l);
        }
        if (type == SpaceContent.OXYGEN_SEALER_BE.get()) {
            return (l, p, s, be) -> ((OxygenSealerBlockEntity) be).serverTick((ServerLevel) l);
        }
        return null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (kind == Kind.COMPRESSOR && SuitItems.holdsOxygen(stack)
                && level.getBlockEntity(pos) instanceof OxygenCompressorBlockEntity be) {
            if (!level.isClientSide()) {
                ItemStack old = be.items().getItem(0);
                be.items().setItem(0, stack.copyWithCount(1));
                stack.shrink(1);
                if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OxygenCompressorBlockEntity compressor && player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new OxygenCompressorMenu(id, inv, compressor),
                    Component.translatable("block.factoryascent.oxygen_compressor")), buf -> buf.writeBlockPos(pos));
        } else if (be instanceof OxygenSealerBlockEntity sealer) {
            player.sendOverlayMessage(sealer.status());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        if (kind == Kind.COMPRESSOR) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.oxygen_compressor").withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("tooltip.factoryascent.oxygen_compressor_use",
                    SpaceConfig.get(SpaceConfig.COMPRESSOR_ENERGY_PER_SECOND)).withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltip.accept(Component.translatable("tooltip.factoryascent.oxygen_sealer",
                    SpaceConfig.get(SpaceConfig.SEALER_RADIUS)).withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("tooltip.factoryascent.oxygen_sealer_use",
                    SpaceConfig.get(SpaceConfig.SEALER_ENERGY)).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
