package net.juli2kapo.factoryascent.space;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Electric and Advanced Jetpacks: worn in the chest slot, FE-powered (charge them in an Energy
 * Cell's slot). Use to put it on; sneak-use to switch hover mode.
 */
public class JetpackItem extends Item implements Jetpack.Gear {
    private final Jetpack.Tier tier;

    public JetpackItem(Properties properties, Jetpack.Tier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public Jetpack.Tier jetTier() {
        return tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide()) Jetpack.toggleHover(player, player.getItemInHand(hand));
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand); // equip
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * Jetpack.energy(stack) / tier.capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.08f + 0.25f * Jetpack.energy(stack) / tier.capacity, 0.9f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        describe(stack, tier, tooltip);
    }

    static void describe(ItemStack stack, Jetpack.Tier tier, Consumer<Component> tooltip) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.stored_energy",
                EnergyUtil.format(Jetpack.energy(stack)), EnergyUtil.format(tier.capacity)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(Jetpack.hover(stack) ? "tooltip.factoryascent.jetpack_hover_on"
                : "tooltip.factoryascent.jetpack_hover_off").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.factoryascent.jetpack", tier.thrustCost()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.factoryascent.jetpack_hover_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
