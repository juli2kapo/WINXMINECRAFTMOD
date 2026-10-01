package net.juli2kapo.factoryascent.outpost;

import java.util.function.Consumer;
import net.juli2kapo.factoryascent.space.SpaceRules;
import net.juli2kapo.factoryascent.space.SuitItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** The outpost's items with behaviour or a tooltip of their own. */
public final class OutpostItems {
    private OutpostItems() {}

    /** A material or part whose tooltip says what it is for ({@code tooltip.factoryascent.<id>}). */
    public static class Described extends Item {
        private final String key;

        public Described(Properties properties, String id) {
            super(properties);
            this.key = "tooltip.factoryascent." + id;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            tooltip.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * An Oxygen Cell (Martian ice split in the Electrolyzer): use it while wearing an Astronaut Suit
     * or Jet Suit to top up the suit's air anywhere, no compressor needed. Leaves the empty cell.
     */
    public static class OxygenCell extends Described {
        public OxygenCell(Properties properties) {
            super(properties, "oxygen_cell");
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack tank = SpaceRules.suitTank(player);
            if (tank.isEmpty()) {
                if (!level.isClientSide()) {
                    player.sendOverlayMessage(Component.translatable("message.factoryascent.lining_no_suit").withStyle(ChatFormatting.YELLOW));
                }
                return InteractionResult.FAIL;
            }
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            int added = SuitItems.fill(tank, OutpostConfig.oxygenCellAir());
            if (added <= 0) {
                player.sendOverlayMessage(Component.translatable("message.factoryascent.oxygen_cell_full").withStyle(ChatFormatting.YELLOW));
                return InteractionResult.FAIL;
            }
            ItemStack cell = player.getItemInHand(hand);
            cell.consume(1, player);
            ItemStack empty = new ItemStack(net.juli2kapo.factoryascent.power.PowerContent.EMPTY_CELL.get());
            if (!player.hasInfiniteMaterials() && !player.getInventory().add(empty)) player.drop(empty, false);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.8f, 1.6f);
            player.sendOverlayMessage(Component.translatable("message.factoryascent.oxygen_cell_used", (added + 19) / 20)
                    .withStyle(ChatFormatting.AQUA));
            return InteractionResult.SUCCESS;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, display, tooltip, flag);
            tooltip.accept(Component.translatable("tooltip.factoryascent.oxygen_cell_air", OutpostConfig.oxygenCellAir() / 20)
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
