package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The centre of the 3×3 Launch Pad: holds the rocket, its payload and its fuel. Right-click
 * with a satellite or an Anti-Satellite missile to mount it, with Blaze Powder or Rocket Fuel to
 * fuel it (hoppers and pipes can do both); launch with a redstone pulse or by sneak-using flint
 * and steel. Sneak with an empty hand to take the payload back off.
 */
public class LaunchControllerBlock extends BaseEntityBlock implements DescribedBlock {
    public static final MapCodec<LaunchControllerBlock> CODEC = simpleCodec(LaunchControllerBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public LaunchControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LaunchPadBlock.SHAPE;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.launch_controller").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.launch_controller_how",
                LaunchControllerBlockEntity.FUEL_PER_LAUNCH).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.factoryascent.launch_controller_multiblock").withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.factoryascent.launch_controller_automation").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The placer owns the controller: payloads fed in by hoppers or pipes launch for their team. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof LaunchControllerBlockEntity controller) {
            controller.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaunchControllerBlockEntity controller)) return InteractionResult.PASS;
        return interact(controller, stack, level, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof LaunchControllerBlockEntity controller)) return InteractionResult.PASS;
        return interact(controller, ItemStack.EMPTY, level, player, InteractionHand.MAIN_HAND);
    }

    /** Everything a click on any part of the pad does. */
    static InteractionResult interact(LaunchControllerBlockEntity controller, ItemStack stack, Level level, Player player,
                                      InteractionHand hand) {
        boolean creative = player.getAbilities().instabuild;
        if (LaunchControllerBlockEntity.isPayload(stack)) {
            if (!level.isClientSide()) {
                Component problem = controller.mount(stack, player.getUUID());
                if (problem == null) {
                    if (!creative) stack.shrink(1);
                    player.sendOverlayMessage(controller.statusLine());
                } else {
                    player.sendOverlayMessage(problem);
                }
            }
            return InteractionResult.SUCCESS;
        }
        int fuel = LaunchControllerBlockEntity.fuelValue(stack);
        if (fuel > 0) {
            if (!level.isClientSide()) {
                if (controller.addFuel(fuel)) {
                    if (!creative) stack.shrink(1);
                }
                player.sendOverlayMessage(controller.statusLine());
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.FLINT_AND_STEEL) && player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                Component problem = controller.tryLaunch();
                if (problem != null) {
                    player.sendOverlayMessage(problem);
                } else if (!creative) {
                    stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                ItemStack back = controller.dismount();
                if (!back.isEmpty()) {
                    if (!player.getInventory().add(back)) player.drop(back, false);
                }
            }
            player.sendOverlayMessage(controller.statusLine());
        }
        return InteractionResult.SUCCESS;
    }

    /** A rising redstone edge launches. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
                                   boolean movedByPiston) {
        if (level.isClientSide()) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered && level.getBlockEntity(pos) instanceof LaunchControllerBlockEntity controller) {
            Component problem = controller.tryLaunch();
            if (problem != null) controller.tellLauncher(problem);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == OrbitalContent.LAUNCH_CONTROLLER_BE.get()
                ? (lvl, pos, st, be) -> ((LaunchControllerBlockEntity) be).serverTick((ServerLevel) lvl)
                : null;
    }
}
