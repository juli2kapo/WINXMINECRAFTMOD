package net.juli2kapo.factoryascent.phone.dock;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.phone.FactoryPhoneItem;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Link Card: sneak-use it on anything the Factory Phone can watch (machines, generators, reactors,
 * tanks, power cables, storage terminals, Ender Beacons, Dyson Receivers and Monitors, the orbital
 * consoles, cross-dimension links…) to record that block. Then insert it into a Phone Dock holding
 * your phone to add the link. One block per card; sneak-use again to overwrite, sneak-use in the
 * air to wipe it. Works before the block's own sneak-use (so blocks with their own right-click
 * still get recorded). Blank cards stack.
 */
public class LinkCardItem extends Item {
    public static final int BLANK_STACK = 16;

    public LinkCardItem(Properties properties) {
        super(properties);
    }

    public static PhoneMemory.@Nullable Link link(ItemStack stack) {
        return stack.get(DockContent.LINK_CARD.get());
    }

    /** Writes a link onto a card stack (a written card doesn't stack). */
    public static void write(ItemStack card, PhoneMemory.Link link) {
        card.set(DockContent.LINK_CARD.get(), link);
        card.set(DataComponents.MAX_STACK_SIZE, 1);
    }

    /** Wipes a card back to blank. */
    public static void wipe(ItemStack card) {
        card.remove(DockContent.LINK_CARD.get());
        card.set(DataComponents.MAX_STACK_SIZE, BLANK_STACK);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        record(sp, stack, context.getHand(), level, context.getClickedPos());
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Records the block at {@code pos} on the held card (splitting one card off a stack of blanks).
     * Returns the written card, or null (with a message) if that block can't be linked.
     */
    public static @Nullable ItemStack record(ServerPlayer player, ItemStack held, InteractionHand hand, ServerLevel level, BlockPos pos) {
        FactoryPhoneItem.Resolved resolved = FactoryPhoneItem.resolve(player, level, pos);
        if (resolved.link() == null) {
            player.sendOverlayMessage(resolved.error());
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5f, 0.6f);
            return null;
        }
        ItemStack card;
        if (held.getCount() > 1) {
            ItemStack split = held.split(1);
            write(split, resolved.link());
            card = split.copy(); // adding to the inventory may empty the stack it is given
            if (!player.getInventory().add(split)) player.drop(split, false);
        } else {
            card = held;
            write(card, resolved.link());
        }
        player.sendOverlayMessage(Component.translatable("message.factoryascent.link_card.recorded",
                Component.translatable(resolved.link().block()), pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.AQUA));
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7f, 1.7f);
        return card;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || link(stack) == null) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            wipe(stack);
            player.sendOverlayMessage(Component.translatable("message.factoryascent.link_card.wiped").withStyle(ChatFormatting.YELLOW));
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.5f, 0.8f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        PhoneMemory.Link link = link(stack);
        return link == null ? super.getName(stack)
                : Component.translatable("item.factoryascent.link_card.written", Component.translatable(link.block()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        PhoneMemory.Link link = link(stack);
        if (link == null) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.link_card.blank").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos p = link.pos().pos();
            tooltip.accept(Component.translatable("tooltip.factoryascent.link_card.kind." + link.kind()).withStyle(ChatFormatting.AQUA));
            tooltip.accept(Component.translatable("tooltip.factoryascent.link_card.at", p.getX(), p.getY(), p.getZ(),
                    link.pos().dimension().identifier().getPath()).withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("tooltip.factoryascent.link_card.dock").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.accept(Component.translatable("tooltip.factoryascent.link_card.howto").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return link(stack) != null;
    }
}
