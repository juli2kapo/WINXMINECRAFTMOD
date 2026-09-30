package net.juli2kapo.factoryascent.dyson;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Centre of the Dyson Receiver's 3×3 rectenna (see {@link DysonReceiverBlockEntity}). Right-click: status. */
public class DysonReceiverBlock extends BaseEntityBlock implements DescribedBlock {
    public static final MapCodec<DysonReceiverBlock> CODEC = simpleCodec(DysonReceiverBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);

    public DysonReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.dyson_receiver").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.dyson_receiver_power", DysonConfig.FE_PER_COLLECTOR.get(),
                EnergyUtil.format(DysonConfig.RECEIVER_MAX_OUTPUT.get())).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.factoryascent.dyson_receiver_multiblock").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.factoryascent.dyson_receiver_output").withStyle(ChatFormatting.DARK_GREEN));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof DysonReceiverBlockEntity receiver) {
            receiver.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player);
    }

    static InteractionResult interact(Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof DysonReceiverBlockEntity receiver) {
            if (receiver.owner() == null) receiver.setOwner(player.getUUID());
            player.sendOverlayMessage(receiver.statusLine(server));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DysonReceiverBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == DysonContent.DYSON_RECEIVER_BE.get()
                ? (lvl, pos, st, be) -> ((DysonReceiverBlockEntity) be).serverTick((ServerLevel) lvl)
                : null;
    }
}
