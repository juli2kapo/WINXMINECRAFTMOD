package net.juli2kapo.factoryascent.ender;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Ender Anchor: a two-block stasis chamber. Glass all round, soul sand at the bottom, water
 * above it. Drop one ender pearl in and it keeps the chunks around it loaded for as long as it
 * stands, the pearl riding the bubble column inside. Breaking it loses the pearl.
 * The block entity lives in the lower half; the upper half forwards to it.
 */
public class EnderAnchorBlock extends BaseEntityBlock implements DescribedBlock {
    public static final MapCodec<EnderAnchorBlock> CODEC = simpleCodec(EnderAnchorBlock::new);
    public static final BooleanProperty ACTIVE = BlockStateProperties.ENABLED;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public EnderAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, HALF);
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        int side = Config.ANCHOR_RADIUS.get() * 2 + 1;
        tooltip.accept(Component.translatable("tooltip.factoryascent.ender_anchor", side, side).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.ender_anchor_pearl").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.accept(Component.translatable("tooltip.factoryascent.two_tall").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static BlockPos lowerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    // ---------------------------------------------------------------- placing and breaking (door-style)

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        Player player = context.getPlayer();
        int limit = Config.ANCHORS_PER_PLAYER.get();
        if (player != null && limit > 0 && !player.getAbilities().instabuild && level instanceof ServerLevel server
                && AnchorLedger.get(server.getServer()).countFor(player.getUUID()) >= limit) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.anchor_limit", limit));
            return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.setBlockAndUpdate(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
        if (level instanceof ServerLevel server && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof EnderAnchorBlockEntity anchor) {
            anchor.setOwner(player.getUUID());
            AnchorLedger.get(server.getServer()).add(player.getUUID(), GlobalPos.of(level.dimension(), pos));
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOtherHalf = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (!towardsOtherHalf) return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
        // Losing the other half removes this one too (with drops, which only the lower half has).
        if (!neighbour.is(this) || neighbour.getValue(HALF) == half) return Blocks.AIR.defaultBlockState();
        return state.setValue(ACTIVE, neighbour.getValue(ACTIVE));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // In creative, breaking the top must not make the bottom drop.
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).is(this)) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.ENDER_PEARL)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(lowerPos(state, pos)) instanceof EnderAnchorBlockEntity anchor) {
            if (anchor.insertPearl()) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            player.sendOverlayMessage(anchor.statusLine());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos lower = lowerPos(state, pos);
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp
                && level.getBlockEntity(lower) instanceof EnderAnchorBlockEntity anchor) {
            EnderAnchorMenu.open(sp, anchor);
        }
        return InteractionResult.SUCCESS;
    }

    /** A bubble column rises off the soul sand while the anchor is awake, popping at the surface. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) return;
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            // A steady column off the soul sand (the surface's Y rides in the y-speed slot).
            for (int i = 0; i < 4; i++) {
                double bx = pos.getX() + 0.3 + random.nextDouble() * 0.4;
                double bz = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
                level.addAlwaysVisibleParticle(EnderContent.STASIS_BUBBLE.get(), bx, pos.getY() + 0.27, bz, 0, pos.getY() + 1 + 13.0 / 16.0, 0);
            }
        } else if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.PORTAL, x, pos.getY() + 0.6, z, 0, 0.1, 0);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new EnderAnchorBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        return type == EnderContent.ENDER_ANCHOR_BE.get()
                ? (lvl, pos, st, be) -> ((EnderAnchorBlockEntity) be).serverTick((ServerLevel) lvl, st)
                : null;
    }
}
