package net.juli2kapo.factoryascent.gear;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Bronze Backpack: 27 slots you carry around. Use it to open it. The contents live in the
 * item's {@code minecraft:container} component, so they survive dropping, chests and restarts.
 * Backpacks, shulker boxes and other container items don't fit inside.
 */
public class BackpackItem extends Item {
    public static final int SLOTS = 27;

    public BackpackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            ItemStack stack = player.getItemInHand(hand);
            server.openMenu(new SimpleMenuProvider((id, inv, p) -> new BackpackMenu(id, inv, hand), stack.getHoverName()),
                    buf -> buf.writeEnum(hand));
            level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7f, 1.1f);
        }
        return InteractionResult.SUCCESS;
    }

    /** Whether an item may go into a backpack. */
    public static boolean fits(ItemStack stack) {
        return !(stack.getItem() instanceof BackpackItem) && stack.getItem().canFitInsideContainerItems();
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        long used = contents.nonEmptyItemCopyStream().count();
        tooltip.accept(Component.translatable("tooltip.factoryascent.backpack", used, SLOTS).withStyle(ChatFormatting.GRAY));
    }
}
