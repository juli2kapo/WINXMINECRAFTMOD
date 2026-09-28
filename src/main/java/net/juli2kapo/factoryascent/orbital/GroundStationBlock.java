package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * Ground Station: a satellite dish with a computer. Right-click opens the survey map (see
 * {@link SurveyService}): while your team has a Survey Satellite over this dimension the station
 * images the terrain around itself ({@link SurveyScanner}) and the map shows it, with your team's
 * satellites over the dimension (and their Deorbit buttons) alongside. Sneak-use with an empty hand
 * opens the Team screen ({@link OrbitalConsole}). The station belongs to the team of whoever placed
 * it.
 */
public class GroundStationBlock extends BaseEntityBlock implements DescribedBlock {
    public static final MapCodec<GroundStationBlock> CODEC = simpleCodec(GroundStationBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public GroundStationBlock(Properties properties) {
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
        tooltip.accept(Component.translatable("tooltip.factoryascent.ground_station").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.ground_station_map",
                Config.SURVEY_RADIUS_CHUNKS.get()).withStyle(ChatFormatting.DARK_GREEN));
        tooltip.accept(Component.translatable("tooltip.factoryascent.ground_station_console").withStyle(ChatFormatting.DARK_AQUA));
    }

    /** The placer owns the station: it surveys for their team. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof GroundStationBlockEntity station) {
            station.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) {
                OrbitalConsole.open(sp);
                return InteractionResult.SUCCESS;
            }
            if (server.getBlockEntity(pos) instanceof GroundStationBlockEntity station) SurveyService.openAtStation(sp, station);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == OrbitalContent.GROUND_STATION_BE.get()
                ? (lvl, p, st, be) -> ((GroundStationBlockEntity) be).serverTick((ServerLevel) lvl)
                : null;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GroundStationBlockEntity(pos, state);
    }
}
