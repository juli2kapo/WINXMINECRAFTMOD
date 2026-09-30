package net.juli2kapo.factoryascent.dyson;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A Solar Collector: one panel of your team's Dyson swarm, waiting for a ride to solar orbit. */
public class DysonCollectorItem extends Item {
    public DysonCollectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.dyson_collector").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.dyson_collector_launch").withStyle(ChatFormatting.DARK_AQUA));
    }
}
