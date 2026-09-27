package net.juli2kapo.factoryascent.ender;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.item.DescribedBlock;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import org.jspecify.annotations.Nullable;

/**
 * Ender Beacon: the home point for Recall Charms. It stores Forge Energy (feed it with a cable) and
 * spends some on every recall; it glows when it holds enough for one. Link a charm by sneaking and
 * using it on the beacon.
 */
public class EnderBeaconBlock extends BaseEntityBlock implements DescribedBlock {
    public static final MapCodec<EnderBeaconBlock> CODEC = simpleCodec(EnderBeaconBlock::new);
    public static final BooleanProperty CHARGED = BlockStateProperties.POWERED;

    public EnderBeaconBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGED);
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.ender_beacon").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.ender_beacon_cost",
                EnergyUtil.format(Config.RECALL_ENERGY.get())).withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof EnderBeaconBlockEntity beacon) {
            beacon.setOwner(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EnderBeaconBlockEntity beacon) {
            player.sendOverlayMessage(beacon.statusLine());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(CHARGED) || random.nextInt(3) != 0) return;
        level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
                pos.getY() + 1.05, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0, 0.05, 0);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnderBeaconBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return type == EnderContent.ENDER_BEACON_BE.get()
                ? (lvl, pos, st, be) -> ((EnderBeaconBlockEntity) be).serverTick((ServerLevel) lvl, st)
                : null;
    }
}
