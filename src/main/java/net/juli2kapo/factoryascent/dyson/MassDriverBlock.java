package net.juli2kapo.factoryascent.dyson;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Mass Driver's breech (see {@link MassDriverBlockEntity}). Right-click with Solar Collectors
 * to load them, with an empty hand for its status; sneak-use with an empty hand takes the loaded
 * collectors back. The rails on top forward clicks to it.
 */
public class MassDriverBlock extends BaseEntityBlock implements DescribedBlock {
    public static final MapCodec<MassDriverBlock> CODEC = simpleCodec(MassDriverBlock::new);

    public MassDriverBlock(Properties properties) {
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
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.mass_driver").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.mass_driver_multiblock", MassDriverBlockEntity.RAILS)
                .withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable("tooltip.factoryascent.mass_driver_energy",
                net.juli2kapo.factoryascent.util.EnergyUtil.format(DysonConfig.LAUNCH_ENERGY.get()),
                String.format("%.1f", DysonConfig.LAUNCH_TICKS.get() / 20.0)).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.factoryascent.mass_driver_use").withStyle(ChatFormatting.DARK_GREEN));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof MassDriverBlockEntity driver) {
            driver.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MassDriverBlockEntity driver)) return InteractionResult.PASS;
        return interact(driver, stack, level, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MassDriverBlockEntity driver)) return InteractionResult.PASS;
        return interact(driver, ItemStack.EMPTY, level, player);
    }

    /** What a click on the breech or any of its rails does. */
    static InteractionResult interact(MassDriverBlockEntity driver, ItemStack stack, Level level, Player player) {
        if (MassDriverBlockEntity.isCollector(stack)) {
            if (!level.isClientSide()) {
                if (driver.owner() == null) driver.setOwner(player.getUUID());
                int n = driver.insert(stack);
                if (n > 0 && !player.getAbilities().instabuild) stack.shrink(n);
                if (n > 0) level.playSound(null, driver.getBlockPos(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6f, 1.4f);
                player.sendOverlayMessage(driver.statusLine());
            }
            return InteractionResult.SUCCESS;
        }
        if (!stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                ItemStack back = driver.takeOut();
                if (!back.isEmpty() && !player.getInventory().add(back)) player.drop(back, false);
            }
            player.sendOverlayMessage(driver.statusLine());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MassDriverBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == DysonContent.MASS_DRIVER_BE.get()
                ? (lvl, pos, st, be) -> ((MassDriverBlockEntity) be).serverTick((ServerLevel) lvl)
                : null;
    }
}
