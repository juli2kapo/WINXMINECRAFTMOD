package net.juli2kapo.minewinx.item.custom;

import net.juli2kapo.minewinx.util.PlayerDataProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ElementSeed extends Item {
    String element;
    int stage;
    public ElementSeed(Properties properties, String element, int stage) {
        super(properties);
        this.element = element;
        this.stage = stage;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            String currentElement = PlayerDataProvider.getElement(player);
            int currentStage = PlayerDataProvider.getStage(player);

            if(currentElement.equals(element)){
                if (currentStage >= stage) {
                    player.sendSystemMessage(Component.translatable("message.minewinx.seed.already_absorbed"));
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
                else if(currentStage == stage - 1 ){
                    player.sendSystemMessage(Component.translatable("message.minewinx.seed.growing"));
                }
                else{
                    player.sendSystemMessage(Component.translatable("message.minewinx.seed.missing_previous"));
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
            }
            else{
                if(stage==1){
                    if (!currentElement.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("message.minewinx.seed.switched", elementName(currentElement), elementName(element)));
                    } else {
                        player.sendSystemMessage(Component.translatable("message.minewinx.seed.absorbed", elementName(element)));
                    }
                    PlayerDataProvider.setElement(player, element);
                } else {
                    player.sendSystemMessage(Component.translatable("message.minewinx.seed.blocked", elementName(currentElement)));
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
            }


            PlayerDataProvider.setStage(player, stage);

            ItemStack itemStack = player.getItemInHand(hand);
            itemStack.shrink(1);

            return InteractionResultHolder.success(itemStack);
        }
        return super.use(level, player, hand);
    }

    /** Nombre traducible del elemento ("Fire" -> element.minewinx.fire). */
    private static Component elementName(String element) {
        return Component.translatable("element.minewinx." + element.toLowerCase(java.util.Locale.ROOT));
    }
}
