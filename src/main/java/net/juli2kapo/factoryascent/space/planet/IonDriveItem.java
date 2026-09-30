package net.juli2kapo.factoryascent.space.planet;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** An Ionite-cored ion engine: carried in an Orbital Shuttle's hold it halves the fuel and time of every trip. */
public class IonDriveItem extends Item {
    public IonDriveItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.ion_drive").withStyle(ChatFormatting.GRAY));
    }
}
