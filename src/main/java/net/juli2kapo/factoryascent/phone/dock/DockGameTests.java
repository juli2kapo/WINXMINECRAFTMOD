package net.juli2kapo.factoryascent.phone.dock;

import net.juli2kapo.factoryascent.Config;
import net.juli2kapo.factoryascent.capsule.CapsuleContent;
import net.juli2kapo.factoryascent.ender.EnderBeaconBlockEntity;
import net.juli2kapo.factoryascent.ender.EnderContent;
import net.juli2kapo.factoryascent.ender.RecallCharmItem;
import net.juli2kapo.factoryascent.phone.FactoryPhoneItem;
import net.juli2kapo.factoryascent.phone.PhoneContent;
import net.juli2kapo.factoryascent.phone.PhoneContext;
import net.juli2kapo.factoryascent.phone.PhoneDevices;
import net.juli2kapo.factoryascent.phone.PhoneMemory;
import net.juli2kapo.factoryascent.phone.apps.RecallApp;
import net.juli2kapo.factoryascent.registry.ModComponents;
import net.juli2kapo.factoryascent.util.HeldUse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Game test bodies for the Link Card + Phone Dock, the phone's many-beacon Recall app and the
 * Recall Charm's broken-beacon handling (listed in ModGameTests.TESTS).
 */
public final class DockGameTests {
    private DockGameTests() {}

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.setPos(h.absoluteVec(new Vec3(1.5, 1, 1.5)));
        return player;
    }

    private static ItemStack phone() {
        ItemStack stack = new ItemStack(PhoneContent.FACTORY_PHONE.get());
        stack.set(ModComponents.ENERGY.get(), 0);
        return stack;
    }

    private static String key(Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
    }

    private static EnderBeaconBlockEntity beacon(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, EnderContent.ENDER_BEACON.get());
        EnderBeaconBlockEntity beacon = h.getBlockEntity(pos, EnderBeaconBlockEntity.class);
        beacon.insertPearl();
        return beacon;
    }

    /** A Link Card records a block (one off a stack); the Phone Dock adds it to the phone, returns the card blank and charges the phone. */
    public static void linkCardAndDock(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos chamber = new BlockPos(6, 1, 6), receiver = new BlockPos(6, 1, 2), beaconPos = new BlockPos(2, 1, 6);
        h.setBlock(chamber, CapsuleContent.SIZE_CHAMBER.get());
        h.setBlock(receiver, net.juli2kapo.factoryascent.dyson.DysonContent.DYSON_RECEIVER.get());
        beacon(h, beaconPos);
        // the Dyson Receiver (its own right-click) and other devices are linkable
        var level = h.getLevel();
        h.assertTrue(PhoneDevices.linkKind(level.getBlockEntity(h.absolutePos(receiver)), level.getBlockState(h.absolutePos(receiver)))
                == PhoneMemory.MACHINE, "a Dyson Receiver must be linkable");
        ItemStack cards = new ItemStack(DockContent.LINK_CARD_ITEM.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, cards);
        ItemStack card = LinkCardItem.record(player, cards, InteractionHand.MAIN_HAND, level, h.absolutePos(beaconPos));
        h.assertTrue(card != null && LinkCardItem.link(card) != null && LinkCardItem.link(card).kind() == PhoneMemory.BEACON,
                "the card must record the beacon");
        h.assertTrue(cards.getCount() == 2, "one card is taken off the blank stack");
        ItemStack card2 = LinkCardItem.record(player, cards, InteractionHand.MAIN_HAND, level, h.absolutePos(receiver));
        h.assertTrue(card2 != null && LinkCardItem.link(card2).kind() == PhoneMemory.MACHINE, "the card must record the receiver");
        // overwrite
        LinkCardItem.record(player, card2, InteractionHand.MAIN_HAND, level, h.absolutePos(chamber));
        h.assertTrue(LinkCardItem.link(card2).pos().pos().equals(h.absolutePos(chamber)), "re-recording overwrites the card");

        BlockPos dockPos = new BlockPos(4, 1, 4);
        h.setBlock(dockPos, DockContent.PHONE_DOCK.get());
        PhoneDockBlockEntity dock = h.getBlockEntity(dockPos, PhoneDockBlockEntity.class);
        dock.energy().set(PhoneDockBlockEntity.CAPACITY);
        dock.inventory.setStack(PhoneDockBlockEntity.CARD_IN, card.copy());
        dock.serverTick(level);
        h.assertTrue(dock.result() == PhoneDockBlockEntity.R_NO_PHONE, "no phone: the card waits");
        dock.inventory.setStack(PhoneDockBlockEntity.PHONE, phone());
        dock.serverTick(level);
        PhoneMemory memory = PhoneMemory.of(dock.phone());
        h.assertTrue(memory.of(PhoneMemory.BEACON).size() == 1, "the phone must know the beacon");
        h.assertTrue(dock.inventory.stack(PhoneDockBlockEntity.CARD_IN).isEmpty(), "the card leaves the reader");
        ItemStack out = dock.inventory.stack(PhoneDockBlockEntity.CARD_OUT);
        h.assertTrue(out.is(DockContent.LINK_CARD_ITEM.get()) && LinkCardItem.link(out) == null, "a blank card comes out");
        dock.inventory.setStack(PhoneDockBlockEntity.CARD_IN, card2.copy());
        dock.serverTick(level);
        h.assertTrue(PhoneMemory.of(dock.phone()).links().size() == 2 && dock.inventory.stack(PhoneDockBlockEntity.CARD_OUT).getCount() == 2,
                "a second card adds a second link");
        h.assertTrue(dock.inventory.stack(PhoneDockBlockEntity.PHONE).getOrDefault(ModComponents.ENERGY.get(), 0) > 0,
                "the dock charges the phone");
        h.assertTrue(dock.removeLink(0) && PhoneMemory.of(dock.phone()).links().size() == 1, "the dock removes links");
        player.discard();
        h.succeed();
    }

    /** The phone's Recall app lists every linked beacon and recalls to the chosen one without a charm. */
    public static void phoneRecallsManyBeacons(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos a = new BlockPos(2, 1, 6), b = new BlockPos(6, 1, 6);
        EnderBeaconBlockEntity beaconB = beacon(h, b);
        beacon(h, a);
        beaconB.setName("Far base");
        ItemStack phone = phone();
        var server = h.getLevel().getServer();
        for (BlockPos p : new BlockPos[] {a, b}) {
            h.assertTrue(FactoryPhoneItem.addLink(server, phone, FactoryPhoneItem.resolve(player, h.getLevel(), h.absolutePos(p)).link()) == null,
                    "linking a beacon to the phone");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, phone);
        h.assertTrue(RecallApp.charm(player).isEmpty(), "no charm in this test");
        h.assertTrue(RecallApp.targets(player, phone).size() == 2, "both beacons are listed");
        var data = new RecallApp().data(new PhoneContext(player, phone, InteractionHand.MAIN_HAND, false));
        h.assertTrue(data.getListOrEmpty("beacons").size() == 2, "the app shows both beacons");
        GlobalPos target = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(b));
        h.assertTrue(RecallApp.start(player, target) == null && RecallApp.channelling(player.getUUID()), "the channel starts without a charm");
        RecallApp.forget(player.getUUID());
        h.assertTrue(RecallCharmItem.teleport(player, target) == null, "the recall goes through");
        h.assertTrue(player.blockPosition().equals(h.absolutePos(b.above())), "the player stands on the chosen beacon");
        h.assertFalse(beaconB.hasPearl(), "the recall uses the pearl");
        Component again = RecallApp.start(player, GlobalPos.of(h.getLevel().dimension(), h.absolutePos(a)));
        h.assertTrue("gui.factoryascent.phone.recall.cooling".equals(key(again)), "the shared cooldown applies, got " + key(again));
        RecallCharmItem.forgetCooldown(player.getUUID());
        player.discard();
        h.succeed();
    }

    /** A charm whose beacon was destroyed says so, keeps the (broken) link and ignores the still-held button. */
    public static void charmBrokenBeacon(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos pos = new BlockPos(6, 1, 6);
        beacon(h, pos);
        ItemStack charm = new ItemStack(EnderContent.RECALL_CHARM.get());
        GlobalPos target = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(pos));
        charm.set(EnderContent.LINKED_BEACON.get(), target);
        player.setItemInHand(InteractionHand.MAIN_HAND, charm);
        h.setBlock(pos, Blocks.AIR);
        Component why = RecallCharmItem.problem(player, target);
        h.assertTrue("message.factoryascent.charm_beacon_destroyed".equals(key(why)), "the reason must be the destroyed beacon, got " + key(why));
        BlockPos before = player.blockPosition();
        EnderContent.RECALL_CHARM.get().recall(player, charm);
        h.assertTrue(player.blockPosition().equals(before), "no teleport");
        h.assertTrue(target.equals(charm.get(EnderContent.LINKED_BEACON.get())), "the link is kept");
        h.assertTrue(charm.getOrDefault(EnderContent.BEACON_BROKEN.get(), false), "the link is marked broken");
        h.assertTrue(player.getCooldowns().isOnCooldown(charm), "the charm pauses so the message stays readable");
        // the button is still held: the next press must not start anything (nor print "link it first")
        h.assertTrue(HeldUse.stillHeld(player), "the held button is remembered");
        EnderContent.RECALL_CHARM.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertFalse(player.isUsingItem(), "a still-held press does nothing");
        // a beacon rebuilt there works again and clears the mark
        HeldUse.forget(player.getUUID());
        beacon(h, pos);
        EnderContent.RECALL_CHARM.get().recall(player, charm);
        h.assertFalse(charm.getOrDefault(EnderContent.BEACON_BROKEN.get(), false), "a working recall clears the mark");
        RecallCharmItem.forgetCooldown(player.getUUID());
        player.discard();
        h.succeed();
    }
}
