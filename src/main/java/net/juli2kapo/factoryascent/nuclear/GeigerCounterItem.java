package net.juli2kapo.factoryascent.nuclear;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Clicks faster the more radiation there is around you while you hold it (in either hand), and
 * shows the dose rate and your absorbed dose on the HUD. Use it for a reading in chat.
 */
public class GeigerCounterItem extends Item {
    public GeigerCounterItem(Properties properties) {
        super(properties);
    }

    public static boolean holding(Player player) {
        return player.getMainHandItem().getItem() instanceof GeigerCounterItem || player.getOffhandItem().getItem() instanceof GeigerCounterItem;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            float exposure = Radiation.exposure(sp);
            RadiationState s = Radiation.state(sp);
            ChatFormatting color = exposure >= 5 ? ChatFormatting.RED : exposure >= 0.5 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
            sp.sendSystemMessage(Component.translatable("message.factoryascent.geiger", Radiation.format(exposure),
                    Radiation.format(exposure * (1 - Radiation.shielding(sp))), Math.round(s.dose())).withStyle(color));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.factoryascent.geiger_counter").withStyle(ChatFormatting.GRAY));
    }
}
