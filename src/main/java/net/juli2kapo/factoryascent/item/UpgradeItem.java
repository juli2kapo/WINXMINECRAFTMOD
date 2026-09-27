package net.juli2kapo.factoryascent.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Speed and Energy upgrade cards that go into a machine's upgrade slots. */
public class UpgradeItem extends Item {
    public enum Kind { SPEED, ENERGY }

    private final Kind kind;

    public UpgradeItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        String key = kind == Kind.SPEED ? "tooltip.factoryascent.speed_upgrade" : "tooltip.factoryascent.energy_upgrade";
        tooltip.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.upgrade_slots").withStyle(ChatFormatting.DARK_GRAY));
    }
}
