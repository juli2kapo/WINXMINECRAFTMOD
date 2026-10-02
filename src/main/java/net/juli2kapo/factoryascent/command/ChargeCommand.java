package net.juli2kapo.factoryascent.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.juli2kapo.factoryascent.space.SuitItems;
import net.juli2kapo.factoryascent.util.EnergyUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * {@code /factoryascent charge [amount]} (operators): fills the FE of the item in your main hand,
 * fully or by {@code amount} FE. An Astronaut Suit chestplate also gets its air tank refilled.
 */
public final class ChargeCommand {
    private ChargeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("factoryascent").then(Commands.literal("charge")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(c -> charge(c, Integer.MAX_VALUE))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(c -> charge(c, IntegerArgumentType.getInteger(c, "amount"))))));
    }

    private static int charge(CommandContext<CommandSourceStack> c, int amount) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("message.factoryascent.charge.empty_hand"));
            return 0;
        }
        boolean air = SuitItems.holdsOxygen(stack);
        if (air) SuitItems.setOxygen(stack, Integer.MAX_VALUE); // clamped to a full tank
        EnergyHandler energy = ItemAccess.forPlayerSlot(player, player.getInventory().getSelectedSlot()).getCapability(Capabilities.Energy.ITEM);
        if (energy == null) {
            if (air) {
                c.getSource().sendSuccess(() -> Component.translatable("message.factoryascent.charge.air", stack.getHoverName()), false);
                return 1;
            }
            c.getSource().sendFailure(Component.translatable("message.factoryascent.charge.no_energy", stack.getHoverName()));
            return 0;
        }
        long added = fill(energy, amount);
        long stored = energy.getAmountAsLong(), capacity = energy.getCapacityAsLong();
        long total = added;
        c.getSource().sendSuccess(() -> Component.translatable("message.factoryascent.charge.done",
                player.getMainHandItem().getHoverName(), EnergyUtil.format(total), EnergyUtil.format(stored), EnergyUtil.format(capacity))
                .withStyle(ChatFormatting.GREEN), false);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * Inserts up to {@code amount} FE. An item may cap each insert, so it inserts again while the
     * stored amount keeps rising, but never more than a few rounds: if an insert reports success
     * without the charge actually sticking, this must not spin forever.
     */
    public static long fill(EnergyHandler energy, long amount) {
        long added = 0;
        for (int round = 0; round < 256 && added < amount; round++) {
            long before = energy.getAmountAsLong();
            if (before >= energy.getCapacityAsLong()) break;
            try (Transaction tx = Transaction.openRoot()) {
                energy.insert((int) Math.min(Integer.MAX_VALUE, amount - added), tx);
                tx.commit();
            }
            long gained = energy.getAmountAsLong() - before;
            if (gained <= 0) break;
            added += gained;
        }
        return added;
    }
}
