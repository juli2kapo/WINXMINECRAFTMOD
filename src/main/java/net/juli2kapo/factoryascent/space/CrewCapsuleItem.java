package net.juli2kapo.factoryascent.space;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A one-seat capsule: mount it on a Launch Pad like a satellite, then Board from the controller. */
public class CrewCapsuleItem extends Item {
    public CrewCapsuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.crew_capsule").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.crew_capsule_fuel", SpaceConfig.crewFuel()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.factoryascent.crew_capsule_suit").withStyle(ChatFormatting.RED));
        tooltip.accept(Component.translatable("tooltip.factoryascent.crew_capsule_arrive").withStyle(ChatFormatting.DARK_GRAY));
    }
}
