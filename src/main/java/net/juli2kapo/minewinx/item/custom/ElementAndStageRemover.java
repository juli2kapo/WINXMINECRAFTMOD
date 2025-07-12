package net.juli2kapo.minewinx.item.custom;

import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ElementAndStageRemover extends Item {

    public ElementAndStageRemover(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            PlayerDataProvider.setElement(player, "");
            PlayerDataProvider.setStage(player, 0);

            ItemStack itemStack = player.getItemInHand(hand);
            itemStack.shrink(1);

            return InteractionResultHolder.success(itemStack);
        }
        return super.use(level, player, hand);
    }
}