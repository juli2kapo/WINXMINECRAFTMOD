package net.juli2kapo.factoryascent.stationkit;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Station Kit (Orbital age): a folded, prefabricated station module for the Launch Pad. Launched,
 * it unfolds in orbit straight above the pad: a sealed 7×7 room with a Station Core claimed for the
 * launcher's team, a powered Oxygen Sealer, windows, an Airlock and a Return Pod
 * ({@link StationKits#deploy}).
 */
public class StationKitItem extends Item {
    public StationKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.station_kit").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.station_kit_how").withStyle(ChatFormatting.DARK_AQUA));
    }
}
