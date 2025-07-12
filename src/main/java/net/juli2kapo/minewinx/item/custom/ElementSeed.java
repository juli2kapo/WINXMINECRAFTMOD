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
                    player.sendSystemMessage(Component.literal("Ya has asborbido esta semilla."));
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
                else if(currentStage == stage - 1 ){
                    player.sendSystemMessage(Component.literal("Sentis tus poderes crecer mientras absorbes la semilla"));
                }
                else{
                    player.sendSystemMessage(Component.literal("Te falta una semilla antes de poder usar esta."));
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
            }
            else{
                if(stage==1){
                    if (!currentElement.isEmpty()) {
                        player.sendSystemMessage(Component.literal("Has renunciado a tu elemento anterior: " + currentElement + ". Ahora has absorbido el poder del " + element + "."));
                    } else {
                        player.sendSystemMessage(Component.literal("Has absorbido el poder del " + element + "."));
                    }
                    PlayerDataProvider.setElement(player, element);
                } else {
                    player.sendSystemMessage(Component.literal("Tus poderes de " + currentElement + " te impiden absorber la semilla."));
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
}