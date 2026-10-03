package net.juli2kapo.factoryascent.space;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Return Pod: a one-seat re-entry capsule with a heat shield and a parachute. Use it in orbit
 * to go home (see {@link Orbit#reenter}); it stays where it is, ready for the next trip down.
 * Sneak-use it in orbit to sit inside (its cabin has air): a capsule that reaches orbit with no
 * station to land on stays up as one of these, the astronaut inside
 * ({@link net.juli2kapo.factoryascent.stationkit.CrewPods}).
 */
public class ReturnPodBlock extends HorizontalDirectionalBlock implements DescribedBlock {
    public static final MapCodec<ReturnPodBlock> CODEC = simpleCodec(ReturnPodBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    public ReturnPodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!SpaceRules.isAirless(level)) {
            player.sendOverlayMessage(Component.translatable("message.factoryascent.return_pod_ground").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        if (net.juli2kapo.factoryascent.space.planet.Planet.of(level) != null) {
            // a planet's gravity well is too deep for a re-entry capsule: that takes an Ascent Module
            player.sendOverlayMessage(Component.translatable("message.factoryascent.return_pod_planet").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            if (sp.isSecondaryUseActive()) {
                net.juli2kapo.factoryascent.stationkit.CrewPods.climbIn(sp, pos); // sit inside: cabin air
            } else {
                if (sp.isPassenger()) sp.stopRiding();
                Orbit.reenter(sp);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.return_pod").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.return_pod_how").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.factoryascent.return_pod_cabin").withStyle(ChatFormatting.DARK_GRAY));
    }
}
