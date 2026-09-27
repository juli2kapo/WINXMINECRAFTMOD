package net.juli2kapo.factoryascent.item;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.machine.MachineBlock;
import net.juli2kapo.factoryascent.pipe.ItemPipeBlock;
import net.juli2kapo.factoryascent.pipe.PipeConnection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Rotates machines and switches item-pipe faces between delivering and extracting. */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof ItemPipeBlock pipe) {
            Direction arm = ItemPipeBlock.armAt(pos, context.getClickLocation());
            if (arm == null) arm = context.getClickedFace();
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (pipe.toggleExtract(level, pos, state, arm)) {
                boolean extracting = level.getBlockState(pos).getValue(ItemPipeBlock.PROPERTIES.get(arm)) == PipeConnection.EXTRACT;
                if (context.getPlayer() != null) {
                    context.getPlayer().sendOverlayMessage(Component.translatable(extracting
                            ? "message.factoryascent.pipe_extract" : "message.factoryascent.pipe_insert"));
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, extracting ? 1.2f : 0.9f);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        }

        if (state.getBlock() instanceof MachineBlock machine && machine.type().hasFacing()) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            Direction facing = state.getValue(MachineBlock.FACING);
            level.setBlock(pos, state.setValue(MachineBlock.FACING, facing.getClockWise()), Block.UPDATE_ALL);
            level.invalidateCapabilities(pos);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, 1f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.wrench").withStyle(ChatFormatting.GRAY));
    }
}
