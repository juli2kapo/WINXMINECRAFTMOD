package net.juli2kapo.factoryascent.nuclear;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A lead-lined Hazmat Suit piece: each stops a quarter of the radiation, the full suit all of it. */
public class HazmatSuitItem extends Item {
    public HazmatSuitItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.hazmat").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.hazmat_set").withStyle(ChatFormatting.DARK_GREEN));
    }
}
