package net.juli2kapo.factoryascent.ender;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Ender Beacon: a one-block stasis chamber and the home point for Recall Charms. As in a vanilla
 * stasis chamber, an ender pearl hangs in its bubble column; a linked Recall Charm is the remote
 * trigger that "fires" it, taking you home and using the pearl up. Load it with a pearl by
 * right-clicking; link a charm by sneaking and using it on the beacon.
 */
public class EnderBeaconBlock extends BaseEntityBlock implements DescribedBlock {
    public static final MapCodec<EnderBeaconBlock> CODEC = simpleCodec(EnderBeaconBlock::new);
    /** A pearl is loaded. */
    public static final BooleanProperty LOADED = BlockStateProperties.ENABLED;
    /** Water surface inside the chamber, in blocks above its floor (under the trapdoor lid). */
    public static final double SURFACE = 13.0 / 16.0;

    public EnderBeaconBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LOADED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOADED);
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.ender_beacon").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.ender_beacon_pearl").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof EnderBeaconBlockEntity beacon) {
            beacon.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.ENDER_PEARL)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EnderBeaconBlockEntity beacon) {
            if (beacon.insertPearl() && !player.getAbilities().instabuild) stack.shrink(1);
            player.sendOverlayMessage(beacon.statusLine());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EnderBeaconBlockEntity beacon) {
            player.sendOverlayMessage(beacon.statusLine());
        }
        return InteractionResult.SUCCESS;
    }

    /** A loaded beacon keeps a bubble column going under its pearl. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LOADED)) return;
        for (int i = 0; i < 2; i++) {
            double bx = pos.getX() + 0.3 + random.nextDouble() * 0.4;
            double bz = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
            // The water surface's Y rides in the y-speed slot (see StasisBubbleParticle).
            level.addAlwaysVisibleParticle(EnderContent.STASIS_BUBBLE.get(), bx, pos.getY() + 0.27, bz, 0, pos.getY() + SURFACE, 0);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnderBeaconBlockEntity(pos, state);
    }
}
