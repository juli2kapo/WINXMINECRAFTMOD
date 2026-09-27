package net.juli2kapo.factoryascent.orbital;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A satellite waiting for a ride: mount it on a Launch Pad. Rename it in an anvil to name it in orbit. */
public class SatelliteItem extends Item {
    private final SatelliteType type;

    public SatelliteItem(SatelliteType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public SatelliteType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent." + type.getSerializedName() + "_satellite")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.satellite_launch").withStyle(ChatFormatting.DARK_AQUA));
    }
}
