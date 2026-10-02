package net.juli2kapo.factoryascent.gear;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Item Magnet: while switched on (use it to toggle) and charged, anything dropped within
 * {@link #RANGE} blocks flies to the player carrying it, anywhere in their inventory. Costs
 * {@link #COST_PER_ITEM} FE per item entity pulled. Items with a pickup delay (just thrown) are
 * left alone so you can still drop things.
 */
public class ItemMagnetItem extends PoweredItem {
    public static final int CAPACITY = 40_000;
    public static final int RANGE = 8;
    public static final int COST_PER_ITEM = 20;

    public ItemMagnetItem(Properties properties) {
        super(CAPACITY, properties);
    }

    public static boolean enabled(ItemStack stack) {
        return stack.getOrDefault(GearContent.MAGNET_ON.get(), false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // One toggle per press: holding the button must not flip it on and off every 4 ticks.
        if (net.juli2kapo.factoryascent.util.HeldUse.stillHeld(player)) return InteractionResult.CONSUME; // (not FAIL: that would try the other hand)
        net.juli2kapo.factoryascent.util.HeldUse.hold(player);
        boolean on = !enabled(stack);
        stack.set(GearContent.MAGNET_ON.get(), on);
        level.playSound(null, player.blockPosition(), on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.4f, on ? 1.6f : 1.2f);
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable(on ? "message.factoryascent.magnet_on" : "message.factoryascent.magnet_off"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof Player player) || player.isSpectator() || !enabled(stack) || level.getGameTime() % 4 != 0) return;
        pull(stack, level, player);
    }

    /** Pulls the loose items around the player to them. Returns how many item entities moved. */
    public static int pull(ItemStack magnet, ServerLevel level, Player player) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(RANGE),
                // already at the player's feet: nothing to pull (and with a full inventory it would
                // otherwise cost power every sweep for an item that can't be picked up)
                e -> e.isAlive() && !e.hasPickUpDelay() && e.distanceToSqr(player) > 2.25);
        int moved = 0;
        for (ItemEntity item : items) {
            if (!drain(magnet, COST_PER_ITEM)) break;
            item.setPos(player.getX(), player.getY() + 0.3, player.getZ());
            item.setDeltaMovement(0, 0, 0);
            moved++;
        }
        return moved;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return enabled(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable(enabled(stack) ? "tooltip.factoryascent.magnet_on" : "tooltip.factoryascent.magnet_off")
                .withStyle(enabled(stack) ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.item_magnet", RANGE).withStyle(ChatFormatting.GRAY));
    }
}
