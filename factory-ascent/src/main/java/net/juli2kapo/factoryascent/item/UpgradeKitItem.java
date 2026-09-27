package net.juli2kapo.factoryascent.item;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Right-click a machine, cable or pipe of the previous tier to upgrade it in place (like
 * Mekanism's tier installer). The upgrade itself happens in the block's {@code useItemOn}.
 */
public class UpgradeKitItem extends Item {
    private final Tier tier;

    public UpgradeKitItem(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public Tier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Tier from = Tier.VALUES[tier.ordinal() - 1];
        tooltip.accept(Component.translatable("tooltip.factoryascent.upgrade_kit", from.displayName(), tier.displayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.upgrade_kit.keeps").withStyle(ChatFormatting.DARK_GRAY));
    }
}
