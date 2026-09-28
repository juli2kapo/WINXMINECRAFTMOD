package net.juli2kapo.factoryascent.space;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Astronaut Suit chestplate with an Advanced Jetpack built in: holds air and flies. */
public class JetSuitItem extends SuitItems.SuitChestItem implements Jetpack.Gear {
    public JetSuitItem(Properties properties) {
        super(properties);
    }

    @Override
    public Jetpack.Tier jetTier() {
        return Jetpack.Tier.ADVANCED;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide()) Jetpack.toggleHover(player, player.getItemInHand(hand));
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        JetpackItem.describe(stack, jetTier(), tooltip);
    }
}
