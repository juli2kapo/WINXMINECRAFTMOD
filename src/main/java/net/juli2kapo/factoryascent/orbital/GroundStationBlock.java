package net.juli2kapo.factoryascent.orbital;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Ground Station: a satellite dish. Right-click lists your team's satellites over this dimension.
 * With a Survey Satellite up there, right-click with an empty map to get a map of the area around
 * the station, filled in from orbit (see {@link SurveyMapper}). Sneak-use with an empty hand opens
 * the Team screen ({@link OrbitalConsole}): team management and deorbiting.
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
        tooltip.accept(Component.translatable("tooltip.factoryascent.ground_station_map").withStyle(ChatFormatting.DARK_GREEN));
        tooltip.accept(Component.translatable("tooltip.factoryascent.ground_station_console").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.MAP)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            ItemStack map = makeMap(server, pos, sp);
            if (map.isEmpty()) {
                sp.sendOverlayMessage(Component.translatable("message.factoryascent.station_no_survey").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (!player.getInventory().add(map)) player.drop(map, false);
            sp.sendOverlayMessage(Component.translatable("message.factoryascent.station_mapping").withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A filled map centred on the station, painted in from orbit over the next few ticks, or
     * EMPTY if the player's team has no Survey Satellite over this dimension.
     */
    public static ItemStack makeMap(ServerLevel level, BlockPos station, ServerPlayer player) {
        if (!OrbitalSignal.has(level.getServer(), player.getUUID(), level.dimension(), SatelliteType.SURVEY)) return ItemStack.EMPTY;
        return SurveyMapper.create(level, station);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown()) {
                OrbitalConsole.open(sp);
                return InteractionResult.SUCCESS;
            }
            FactoryTeams teams = FactoryTeams.get(server.getServer());
            teams.remember(sp);
            String team = teams.teamOf(sp.getUUID());
            List<Satellite> sats = OrbitRegistry.get(server.getServer()).over(team, server.dimension());
            sp.sendSystemMessage(Component.translatable("message.factoryascent.station_header",
                    OrbitalText.dimensionName(server.dimension()), teams.displayName(team), sats.size()).withStyle(ChatFormatting.GOLD));
            if (sats.isEmpty()) {
                sp.sendSystemMessage(Component.translatable("message.factoryascent.station_empty").withStyle(ChatFormatting.GRAY));
            }
            for (Satellite s : sats) {
                long days = OrbitalText.daysInOrbit(server.getServer(), s);
                sp.sendSystemMessage(OrbitalText.satelliteLine(s).append(
                        Component.translatable("message.factoryascent.station_age", days).withStyle(ChatFormatting.DARK_GRAY)));
            }
            boolean signal = OrbitalSignal.hasCoverage(sp), survey = OrbitalSignal.hasSurvey(sp);
            sp.sendSystemMessage(Component.translatable(signal ? "message.factoryascent.station_signal_on" : "message.factoryascent.station_signal_off")
                    .withStyle(signal ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
            sp.sendSystemMessage(Component.translatable(survey ? "message.factoryascent.station_survey_on" : "message.factoryascent.station_survey_off")
                    .withStyle(survey ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            sp.sendSystemMessage(Component.translatable("message.factoryascent.station_console_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GroundStationBlockEntity(pos, state);
    }
}
