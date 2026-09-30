package net.juli2kapo.factoryascent.space.planet;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.space.SuitItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A Thermal Lining for the Astronaut Suit (or Jet Suit): use it while wearing the suit to sew it in.
 * A lined suit keeps out Io's heat ({@link PlanetHazards}); the lining stays in the suit for good.
 */
public class ThermalLiningItem extends Item {
    public ThermalLiningItem(Properties properties) {
        super(properties);
    }

    public static boolean lined(ItemStack chest) {
        return SuitItems.holdsOxygen(chest) && chest.getOrDefault(PlanetContent.LINED.get(), false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!SuitItems.holdsOxygen(chest)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.factoryascent.lining_no_suit").withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResult.FAIL;
        }
        if (lined(chest)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.factoryascent.lining_already").withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            chest.set(PlanetContent.LINED.get(), true);
            player.getItemInHand(hand).consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, 0.8f);
            player.sendOverlayMessage(Component.translatable("message.factoryascent.lining_done").withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.thermal_lining").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.thermal_lining_use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
