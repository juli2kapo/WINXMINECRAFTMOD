package net.juli2kapo.factoryascent.space.station;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.space.SuitItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;

/** Magnetic Boots: Astronaut Boots (they count as the suit's boots) that hold you to the floor in low gravity ({@link MagneticBoots}). */
public class MagneticBootsItem extends SuitItems.SuitPieceItem {
    public MagneticBootsItem(Properties properties) {
        super(properties, ArmorType.BOOTS);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("tooltip.factoryascent.magnetic_boots").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.magnetic_boots_sneak").withStyle(ChatFormatting.DARK_GRAY));
    }
}
