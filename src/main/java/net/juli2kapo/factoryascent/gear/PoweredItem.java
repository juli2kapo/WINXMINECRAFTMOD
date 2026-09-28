package net.juli2kapo.factoryascent.gear;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * An item with an FE buffer in the {@code factoryascent:energy} component (exposed as the item
 * energy capability in {@link GearContent}), charged in a Charger or an Energy Cell's slot.
 */
public abstract class PoweredItem extends Item {
    private final int capacity;

    protected PoweredItem(int capacity, Properties properties) {
        super(properties);
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENERGY.get(), 0);
    }

    /** Takes {@code amount} FE if the item has it all; false (and nothing taken) otherwise. */
    public static boolean drain(ItemStack stack, int amount) {
        int have = energy(stack);
        if (have < amount) return false;
        stack.set(ModComponents.ENERGY.get(), have - amount);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.08f + 0.25f * energy(stack) / capacity, 0.9f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.stored_energy",
                EnergyUtil.format(energy(stack)), EnergyUtil.format(capacity)).withStyle(ChatFormatting.GRAY));
    }
}
