package net.juli2kapo.factoryascent.fusion;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.Age;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.juli2kapo.factoryascent.power.PowerContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The Tokamak Core: the centre of the fusion ring. ACTIVE while the plasma burns. */
public class TokamakCoreBlock extends BaseEntityBlock implements DescribedBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final MapCodec<TokamakCoreBlock> CODEC = simpleCodec(TokamakCoreBlock::new);

    public TokamakCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TokamakCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == PowerContent.TOKAMAK_CORE_BE.get() ? (l, p, s, be) -> ((TokamakCoreBlockEntity) be).serverTick((ServerLevel) l) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TokamakCoreBlockEntity be) {
            be.rescanNow(level);
            player.openMenu(be, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.age", Age.QUANTUM.displayName()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.tokamak_core").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.tokamak_numbers", EnergyUtil.format(TokamakCoreBlockEntity.outputPerTick()),
                EnergyUtil.format(TokamakCoreBlockEntity.startupEnergy())).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.factoryascent.tokamak_build").withStyle(ChatFormatting.DARK_GRAY));
    }
}
