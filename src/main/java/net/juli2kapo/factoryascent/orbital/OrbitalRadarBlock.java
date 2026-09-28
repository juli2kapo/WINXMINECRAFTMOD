package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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

/**
 * Orbital Radar: a tracking dish that lists every satellite over its dimension and locks foreign
 * ones (see {@link OrbitalRadarBlockEntity}). Right-click opens its screen; right-click with an
 * Anti-Satellite missile programs the missile with the target picked on the screen. Takes energy
 * from cables on any side.
 */
public class OrbitalRadarBlock extends BaseEntityBlock implements DescribedBlock {
    public static final MapCodec<OrbitalRadarBlock> CODEC = simpleCodec(OrbitalRadarBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public OrbitalRadarBlock(Properties properties) {
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
        tooltip.accept(Component.translatable("tooltip.factoryascent.orbital_radar").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.orbital_radar_lock", OrbitalRadarBlockEntity.TRACK_DRAIN)
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.factoryascent.orbital_radar_asat").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof OrbitalRadarBlockEntity radar) {
            radar.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof AsatMissileItem)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp
                && level.getBlockEntity(pos) instanceof OrbitalRadarBlockEntity radar) {
            Component problem = program(server, radar, sp, stack);
            sp.sendOverlayMessage(problem != null ? problem
                    : Component.translatable("message.factoryascent.asat_programmed", AsatMissileItem.target(stack).label())
                    .withStyle(ChatFormatting.GOLD));
            if (problem == null) level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1f, 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    /** Programs the missile with the radar's designated target; null on success, else why not. */
    static @Nullable Component program(ServerLevel level, OrbitalRadarBlockEntity radar, ServerPlayer player, ItemStack missile) {
        Component problem = radar.accessProblem(player);
        if (problem != null) return problem;
        var target = radar.designated();
        String label = target == null ? null : radar.label(level.getServer(), target);
        if (target == null || label == null) {
            return Component.translatable("message.factoryascent.asat_no_designation").withStyle(ChatFormatting.RED);
        }
        missile.set(OrbitalContent.ASAT_TARGET.get(),
                new AsatMissileItem.Target(target, GlobalPos.of(level.dimension(), radar.getBlockPos()), label));
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof OrbitalRadarBlockEntity radar) {
            radar.open(sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OrbitalRadarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == OrbitalContent.ORBITAL_RADAR_BE.get()
                ? (lvl, pos, st, be) -> ((OrbitalRadarBlockEntity) be).serverTick((ServerLevel) lvl)
                : null;
    }
}
