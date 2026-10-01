package net.juli2kapo.factoryascent.fluid.tank;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.fluid.FluidContent;
import net.juli2kapo.factoryascent.item.FactoryBlockItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

/** A tank as an item: says what it still holds. */
public class FluidTankItem extends FactoryBlockItem {
    public FluidTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        SimpleFluidContent content = stack.get(FluidContent.TANK_CONTENTS.get());
        if (content != null && !content.isEmpty()) {
            FluidStack fs = content.copy();
            tooltip.accept(Component.translatable("tooltip.factoryascent.tank_holds", fs.getHoverName(), fs.getAmount())
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
