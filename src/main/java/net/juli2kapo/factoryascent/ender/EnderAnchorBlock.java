package net.juli2kapo.factoryascent.ender;

import com.mojang.serialization.MapCodec;
import net.juli2kapo.factoryascent.Config;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Ender Anchor: a glass tank of ender-steeped water on soul sand with an eye of ender floating in
 * it. Fed ender pearls, it keeps the chunks around it loaded; when the pearls run out the eye sinks
 * and the chunks are released.
 */
public class EnderAnchorBlock extends BaseEntityBlock implements net.juli2kapo.factoryascent.item.DescribedBlock {
    public static final MapCodec<EnderAnchorBlock> CODEC = simpleCodec(EnderAnchorBlock::new);
    public static final BooleanProperty ACTIVE = BlockStateProperties.ENABLED;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public EnderAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    public void describe(java.util.function.Consumer<Component> tooltip) {
        int side = Config.ANCHOR_RADIUS.get() * 2 + 1;
        tooltip.accept(Component.translatable("tooltip.factoryascent.ender_anchor", side, side)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        if (Config.ANCHORS_NEED_FUEL.get()) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.ender_anchor_fuel", Config.ANCHOR_MINUTES_PER_PEARL.get())
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        }
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        int limit = Config.ANCHORS_PER_PLAYER.get();
        if (player != null && limit > 0 && !player.getAbilities().instabuild
                && context.getLevel() instanceof ServerLevel level
                && AnchorLedger.get(level.getServer()).countFor(player.getUUID()) >= limit) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.anchor_limit", limit));
            return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof EnderAnchorBlockEntity anchor) {
            anchor.setOwner(player.getUUID());
            AnchorLedger.get(server.getServer()).add(player.getUUID(), GlobalPos.of(level.dimension(), pos));
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.ENDER_PEARL)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EnderAnchorBlockEntity anchor) {
            int added = anchor.addPearls(stack.getCount());
            if (!player.getAbilities().instabuild) stack.shrink(added);
            player.sendOverlayMessage(anchor.statusLine());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EnderAnchorBlockEntity anchor) {
            player.sendOverlayMessage(anchor.statusLine());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) return;
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.PORTAL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    pos.getY() + 0.4 + random.nextDouble() * 0.5, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.4);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnderAnchorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == EnderContent.ENDER_ANCHOR_BE.get()
                ? (lvl, pos, st, be) -> ((EnderAnchorBlockEntity) be).serverTick((ServerLevel) lvl, st)
                : null;
    }
}
