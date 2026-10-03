package net.juli2kapo.factoryascent.space.station;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The Station Core: place it to claim a station for your team; right-click for the station's status screen. */
public class StationCoreBlock extends BaseEntityBlock implements DescribedBlock {
    public static final MapCodec<StationCoreBlock> CODEC = simpleCodec(StationCoreBlock::new);

    public StationCoreBlock(Properties properties) {
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StationCoreBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (by instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof StationCoreBlockEntity core) {
            core.claim(player, stack);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof StationCoreBlockEntity core) {
            if (core.team().isEmpty()) core.claim(sp, ItemStack.EMPTY); // placed by a machine or command: the first user claims it
            if (sp.isSecondaryUseActive()) {
                // the cargo hold: the station's own team only
                String team = net.juli2kapo.factoryascent.orbital.FactoryTeams.get(server.getServer()).teamOf(sp.getUUID());
                if (!team.equals(core.team()) && !sp.isCreative()) {
                    sp.sendOverlayMessage(Component.translatable("message.factoryascent.station_protected", core.name()).withStyle(ChatFormatting.RED));
                    return InteractionResult.SUCCESS;
                }
                sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, core.cargo()),
                        Component.translatable("gui.factoryascent.station.cargo", core.name())));
                return InteractionResult.SUCCESS;
            }
            core.sendView(server, sp, true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void describe(Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.station_core").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.station_core_claim").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.factoryascent.station_core_cargo").withStyle(ChatFormatting.DARK_GRAY));
    }
}
