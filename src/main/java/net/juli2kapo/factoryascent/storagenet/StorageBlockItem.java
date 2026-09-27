package net.juli2kapo.factoryascent.storagenet;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.Age;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** Block item of every storage network block: age line plus a one-line description. */
public class StorageBlockItem extends BlockItem {
    public StorageBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.age", Age.ELECTRIC.displayName()).withStyle(ChatFormatting.DARK_GRAY));
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(getBlock()).getPath();
        tooltip.accept(Component.translatable("desc.factoryascent." + id).withStyle(ChatFormatting.GRAY));
        if (getBlock() == StorageContent.CONTROLLER.get()) {
            tooltip.accept(Component.translatable("tooltip.factoryascent.storage_controller.power",
                    StorageControllerBlockEntity.BASE_DRAIN, StorageControllerBlockEntity.PER_DEVICE).withStyle(ChatFormatting.GOLD));
        }
    }
}
