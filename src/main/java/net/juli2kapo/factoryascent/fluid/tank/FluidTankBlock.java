package net.juli2kapo.factoryascent.fluid.tank;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.fluid.FluidTank;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.Nullable;

/** A fluid tank block (see {@link FluidTankBlockEntity}): buckets and cells fill and empty it by hand. */
public class FluidTankBlock extends BaseEntityBlock implements DescribedBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);
    private final TankSize size;
    private final MapCodec<FluidTankBlock> codec;

    public FluidTankBlock(TankSize size, Properties properties) {
        super(properties);
        this.size = size;
        this.codec = simpleCodec(p -> new FluidTankBlock(size, p));
    }

    public TankSize size() {
        return size;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
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
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != FluidContent.FLUID_TANK_BE.get()) return null;
        return (l, p, s, be) -> ((FluidTankBlockEntity) be).serverTick((ServerLevel) l);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FluidTankBlockEntity be) || stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            showContents(player, be.tank());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FluidTankBlockEntity be) showContents(player, be.tank());
        return InteractionResult.SUCCESS;
    }

    static void showContents(Player player, FluidTank tank) {
        Component what = tank.isEmpty() ? Component.translatable("message.factoryascent.tank_empty", tank.capacity() / 1000)
                : Component.translatable("message.factoryascent.tank_contents", tank.fluid().getFluidType().getDescription(tank.stack()),
                        tank.amount(), tank.capacity());
        player.sendOverlayMessage(what);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof FluidTankBlockEntity be ? be.comparatorSignal() : 0;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.age", size.age().displayName()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("desc.factoryascent.fluid_tank").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.tank_capacity", size.capacity() / 1000).withStyle(ChatFormatting.DARK_AQUA));
    }
}
