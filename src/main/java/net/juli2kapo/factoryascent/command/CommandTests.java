package net.juli2kapo.factoryascent.command;

import net.juli2kapo.factoryascent.gear.GearContent;
import net.juli2kapo.factoryascent.gear.ItemMagnetItem;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/** The charge command's filling, on an item held in a player's hand. */
public final class CommandTests {
    private CommandTests() {}

    /** An empty magnet in the hand is filled to capacity, past its per-insert rate, and the hand keeps the charge. */
    public static void chargeFillsHeldItem(GameTestHelper h) {
        @SuppressWarnings("removal")
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GearContent.ITEM_MAGNET.get()));
        EnergyHandler energy = ItemAccess.forPlayerSlot(player, player.getInventory().getSelectedSlot()).getCapability(Capabilities.Energy.ITEM);
        h.assertTrue(energy != null, "the magnet must hold energy");
        long added = ChargeCommand.fill(energy, Long.MAX_VALUE);
        h.assertTrue(added == ItemMagnetItem.CAPACITY, "must add a full charge, added " + added);
        EnergyHandler after = ItemAccess.forPlayerSlot(player, player.getInventory().getSelectedSlot()).getCapability(Capabilities.Energy.ITEM);
        h.assertTrue(after.getAmountAsLong() == ItemMagnetItem.CAPACITY, "the held magnet must keep the charge, has " + after.getAmountAsLong());
        h.assertTrue(ChargeCommand.fill(after, 1000) == 0, "a full item takes nothing more");
        h.succeed();
    }
}
